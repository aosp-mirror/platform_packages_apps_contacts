/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.voicemail;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import com.android.ex.variablespeed.MediaPlayerProxy;
import com.android.ex.variablespeed.SingleThreadedMediaPlayerProxy;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Contains the controlling logic for a voicemail playback ui.
 * <p>
 * Specifically right now this class is used to control the
 * {@link com.android.contacts.voicemail.VoicemailPlaybackFragment}.
 * <p>
 * This class is not thread safe. The thread policy for this class is
 * thread-confinement, all calls into this class from outside must be done from
 * the main ui thread.
 */
@NotThreadSafe
/*package*/ class VoicemailPlaybackPresenter {
    /** Contract describing the behaviour we need from the ui we are controlling. */
    public interface PlaybackView {
        Context getDataSourceContext();
        void runOnUiThread(Runnable runnable);
        void setStartStopListener(View.OnClickListener listener);
        void setPositionSeekListener(SeekBar.OnSeekBarChangeListener listener);
        void setSpeakerphoneListener(View.OnClickListener listener);
        void setDeleteButtonListener(View.OnClickListener listener);
        void setClipLength(int clipLengthInMillis);
        void setClipPosition(int clipPositionInMillis);
        int getDesiredClipPosition();
        void playbackStarted();
        void playbackStopped();
        void playbackError();
        boolean isSpeakerPhoneOn();
        void setSpeakerPhoneOn(boolean on);
        void finish();
        void setRateDisplay(float rate);
        void setRateIncreaseButtonListener(View.OnClickListener listener);
        void setRateDecreaseButtonListener(View.OnClickListener listener);
    }

    /** Update rate for the slider, 30fps. */
    private static final int SLIDER_UPDATE_PERIOD_MILLIS = 1000 / 30;
    /**
     * If present in the saved instance bundle, we should not resume playback on
     * create.
     */
    private static final String PAUSED_STATE_KEY = VoicemailPlaybackPresenter.class.getName()
            + ".PAUSED_STATE_KEY";
    /**
     * If present in the saved instance bundle, indicates where to set the
     * playback slider.
     */
    private static final String CLIP_POSITION_KEY = VoicemailPlaybackPresenter.class.getName()
            + ".CLIP_POSITION_KEY";

    /** The preset variable-speed rates.  Each is greater than the previous by 25%. */
    private static final float[] PRESET_RATES = new float[] {
        0.64f, 0.8f, 1.0f, 1.25f, 1.5625f
    };

    /** Index into {@link #PRESET_RATES} indicating the current playback speed. */
    private final AtomicInteger mCurrentPlaybackRate = new AtomicInteger(2);

    private final PlaybackView mView;
    private final MediaPlayerProxy mPlayer;
    private final PositionUpdater mPositionUpdater;

    /** Voicemail uri to play, will be set with a call to {@link #setVoicemailUri(Uri, boolean)}. */
    private Uri mVoicemailUri;

    public VoicemailPlaybackPresenter(PlaybackView view, MediaPlayerProxy player,
            ScheduledExecutorService executorService) {
        mView = view;
        mPlayer = player;
        mPositionUpdater = new PositionUpdater(executorService, SLIDER_UPDATE_PERIOD_MILLIS);
    }

    public void onCreate(Bundle bundle) {
        mView.setPositionSeekListener(new PlaybackPositionListener());
        mView.setStartStopListener(new StartStopButtonListener());
        mView.setSpeakerphoneListener(new SpeakerphoneListener());
        mView.setDeleteButtonListener(new DeleteButtonListener());
        mPlayer.setOnErrorListener(new MediaPlayerErrorListener());
        mPlayer.setOnCompletionListener(new MediaPlayerCompletionListener());
        mView.setSpeakerPhoneOn(mView.isSpeakerPhoneOn());
        mView.setRateDecreaseButtonListener(createRateDecreaseListener());
        mView.setRateIncreaseButtonListener(createRateIncreaseListener());
        mView.setClipPosition(0);
        // TODO: Now I'm ignoring the bundle, when previously I was checking for contains against
        // the PAUSED_STATE_KEY, and CLIP_POSITION_KEY.
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(CLIP_POSITION_KEY, mView.getDesiredClipPosition());
        if (!mPlayer.isPlaying()) {
            outState.putBoolean(PAUSED_STATE_KEY, true);
        }
    }

    public void onDestroy() {
        mPlayer.release();
    }

    public void setVoicemailUri(Uri voicemailUri, boolean startPlaying) {
        mVoicemailUri = voicemailUri;
        if (startPlaying) {
            resetPrepareStartPlaying(0);
        }
    }

    private class MediaPlayerErrorListener implements MediaPlayer.OnErrorListener {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            mView.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleError(new IllegalStateException("MediaPlayer error listener invoked"));
                }
            });
            return true;
        }
    }

    private class MediaPlayerCompletionListener implements MediaPlayer.OnCompletionListener {
        @Override
        public void onCompletion(final MediaPlayer mp) {
            mView.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleCompletion(mp);
                }
            });
        }
    }

    public View.OnClickListener createRateDecreaseListener() {
        return new RateChangeListener(false);
    }

    public View.OnClickListener createRateIncreaseListener() {
        return new RateChangeListener(true);
    }

    private class RateChangeListener implements View.OnClickListener {
        private final boolean mIncrease;

        public RateChangeListener(boolean increase) {
            mIncrease = increase;
        }

        @Override
        public void onClick(View v) {
            int adjustment = (mIncrease ? 1 : -1);
            int andGet = mCurrentPlaybackRate.addAndGet(adjustment);
            if (andGet < 0) {
                // TODO: discussions with interaction design have suggested that we might make
                // an audible tone play here to indicate that you've hit the end of the range?
                // Let's firm up this decision.
                mCurrentPlaybackRate.set(0);
            } else if (andGet >= PRESET_RATES.length) {
                mCurrentPlaybackRate.set(PRESET_RATES.length - 1);
            } else {
                changeRate(PRESET_RATES[andGet]);
            }
        }
    }

    private void resetPrepareStartPlaying(int clipPositionInMillis) {
        try {
            mPlayer.reset();
            mPlayer.setDataSource(mView.getDataSourceContext(), mVoicemailUri);
            mPlayer.prepare();
            int clipLengthInMillis = mPlayer.getDuration();
            mView.setClipLength(clipLengthInMillis);
            int startPosition = Math.min(Math.max(clipPositionInMillis, 0), clipLengthInMillis);
            mPlayer.seekTo(startPosition);
            mPlayer.start();
            mView.playbackStarted();
            mPositionUpdater.startUpdating(startPosition, clipLengthInMillis);
        } catch (IOException e) {
            handleError(e);
        }
    }

    private void handleError(Exception e) {
        mView.playbackError();
        mPlayer.release();
        mPositionUpdater.stopUpdating();
    }

    public void handleCompletion(MediaPlayer mediaPlayer) {
        stopPlaybackAtPosition(0);
    }

    private void stopPlaybackAtPosition(int clipPosition) {
        mView.playbackStopped();
        mPositionUpdater.stopUpdating();
        mView.setClipPosition(clipPosition);
        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        }
    }

    private class PlaybackPositionListener implements SeekBar.OnSeekBarChangeListener {
        private boolean mShouldResumePlaybackAfterSeeking;

        @Override
        public void onStartTrackingTouch(SeekBar arg0) {
            if (mPlayer.isPlaying()) {
                mShouldResumePlaybackAfterSeeking = true;
                stopPlaybackAtPosition(mPlayer.getCurrentPosition());
            } else {
                mShouldResumePlaybackAfterSeeking = false;
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar arg0) {
            if (mPlayer.isPlaying()) {
                stopPlaybackAtPosition(mPlayer.getCurrentPosition());
            }
            if (mShouldResumePlaybackAfterSeeking) {
                resetPrepareStartPlaying(mView.getDesiredClipPosition());
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            mView.setClipPosition(seekBar.getProgress());
        }
    }

    private void changeRate(float rate) {
        ((SingleThreadedMediaPlayerProxy) mPlayer).setVariableSpeed(rate);
        mView.setRateDisplay(rate);
    }

    private class SpeakerphoneListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            mView.setSpeakerPhoneOn(!mView.isSpeakerPhoneOn());
        }
    }

    private class DeleteButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // TODO: Temporarily removed this whilst the team discuss the merits of porting
            // the VoicemailHelper class across vs just hard-coding the delete via cursor.
            mView.finish();
        }
    }

    private class StartStopButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View arg0) {
            if (mPlayer.isPlaying()) {
                stopPlaybackAtPosition(mPlayer.getCurrentPosition());
            } else {
                resetPrepareStartPlaying(mView.getDesiredClipPosition());
            }
        }
    }

    /**
     * Controls the animation of the playback slider.
     */
    @ThreadSafe
    private final class PositionUpdater implements Runnable {
        private final ScheduledExecutorService mExecutorService;
        private final int mPeriodMillis;
        private final Object mLock = new Object();
        @GuardedBy("mLock") private ScheduledFuture<?> mScheduledFuture;

        public PositionUpdater(ScheduledExecutorService executorService, int periodMillis) {
            mExecutorService = executorService;
            mPeriodMillis = periodMillis;
        }

        @Override
        public void run() {
            synchronized (mLock) {
                if (mScheduledFuture != null) {
                    mView.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mView.setClipPosition(mPlayer.getCurrentPosition());
                        }
                    });
                }
            }
        }

        public void startUpdating(int beginPosition, int endPosition) {
            synchronized (mLock) {
                if (mScheduledFuture != null) {
                    mScheduledFuture.cancel(false);
                }
                mScheduledFuture = mExecutorService.scheduleAtFixedRate(this, 0, mPeriodMillis,
                        TimeUnit.MILLISECONDS);
            }
        }

        public void stopUpdating() {
            synchronized (mLock) {
                if (mScheduledFuture != null) {
                    mScheduledFuture.cancel(false);
                    mScheduledFuture = null;
                }
            }
        }
    }
}
