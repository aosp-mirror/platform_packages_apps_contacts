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

import static com.android.contacts.CallDetailActivity.EXTRA_VOICEMAIL_START_PLAYBACK;
import static com.android.contacts.CallDetailActivity.EXTRA_VOICEMAIL_URI;

import com.android.contacts.R;
import com.android.ex.variablespeed.MediaPlayerProxy;
import com.android.ex.variablespeed.VariableSpeed;
import com.google.common.base.Preconditions;

import android.app.Fragment;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Displays and plays back a single voicemail.
 * <p>
 * When the Activity containing this Fragment is created, voicemail playback
 * will begin immediately. The Activity is expected to be started via an intent
 * containing a suitable voicemail uri to playback.
 * <p>
 * This class is not thread-safe, it is thread-confined. All calls to all public
 * methods on this class are expected to come from the main ui thread.
 */
@NotThreadSafe
public class VoicemailPlaybackFragment extends Fragment {
    private static final String TAG = "VoicemailPlayback";
    private static final int NUMBER_OF_THREADS_IN_POOL = 2;

    private VoicemailPlaybackPresenter mPresenter;
    private ScheduledExecutorService mScheduledExecutorService;
    private SeekBar mPlaybackSeek;
    private ImageButton mStartStopButton;
    private ImageButton mPlaybackSpeakerphone;
    private TextView mPlaybackPositionText;
    private ImageButton mRateDecreaseButton;
    private ImageButton mRateIncreaseButton;
    private TextViewWithMessagesController mTextController;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.playback_layout, null);
        mPlaybackSeek = (SeekBar) view.findViewById(R.id.playback_seek);
        mPlaybackSeek = (SeekBar) view.findViewById(R.id.playback_seek);
        mStartStopButton = (ImageButton) view.findViewById(R.id.playback_start_stop);
        mPlaybackSpeakerphone = (ImageButton) view.findViewById(R.id.playback_speakerphone);
        mPlaybackPositionText = (TextView) view.findViewById(R.id.playback_position_text);
        mRateDecreaseButton = (ImageButton) view.findViewById(R.id.rate_decrease_button);
        mRateIncreaseButton = (ImageButton) view.findViewById(R.id.rate_increase_button);
        mTextController = new TextViewWithMessagesController(mPlaybackPositionText);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mScheduledExecutorService = createScheduledExecutorService();
        Bundle arguments = getArguments();
        Preconditions.checkNotNull(arguments, "fragment must be started with arguments");
        Uri voicemailUri = arguments.getParcelable(EXTRA_VOICEMAIL_URI);
        Preconditions.checkNotNull(voicemailUri, "fragment must contain EXTRA_VOICEMAIL_URI");
        boolean startPlayback = arguments.getBoolean(EXTRA_VOICEMAIL_START_PLAYBACK, false);
        mPresenter = new VoicemailPlaybackPresenter(new PlaybackViewImpl(),
                createMediaPlayer(mScheduledExecutorService), voicemailUri,
                mScheduledExecutorService, startPlayback);
        mPresenter.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mPresenter.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        mPresenter.onDestroy();
        mScheduledExecutorService.shutdown();
        super.onDestroy();
    }

    private MediaPlayerProxy createMediaPlayer(ExecutorService executorService) {
        return VariableSpeed.createVariableSpeed(executorService);
    }

    private ScheduledExecutorService createScheduledExecutorService() {
        return Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_POOL);
    }

    /**
     * Formats a number of milliseconds as something that looks like {@code 00:05}.
     * <p>
     * We always use four digits, two for minutes two for seconds.  In the very unlikely event
     * that the voicemail duration exceeds 99 minutes, the display is capped at 99 minutes.
     */
    private String formatAsMinutesAndSeconds(int millis) {
        int seconds = millis / 1000;
        int minutes = seconds / 60;
        seconds -= minutes * 60;
        if (minutes > 99) {
            minutes = 99;
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private AudioManager getAudioManager() {
        return (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
    }

    /**  Methods required by the PlaybackView for the VoicemailPlaybackPresenter. */
    private class PlaybackViewImpl implements VoicemailPlaybackPresenter.PlaybackView {
        @Override
        public void finish() {
            getActivity().finish();
        }

        @Override
        public void runOnUiThread(Runnable runnable) {
            getActivity().runOnUiThread(runnable);
        }

        @Override
        public Context getDataSourceContext() {
            return getActivity();
        }

        @Override
        public void setRateDecreaseButtonListener(View.OnClickListener listener) {
            mRateDecreaseButton.setOnClickListener(listener);
        }

        @Override
        public void setRateIncreaseButtonListener(View.OnClickListener listener) {
            mRateIncreaseButton.setOnClickListener(listener);
        }

        @Override
        public void setStartStopListener(View.OnClickListener listener) {
            mStartStopButton.setOnClickListener(listener);
        }

        @Override
        public void setSpeakerphoneListener(View.OnClickListener listener) {
            mPlaybackSpeakerphone.setOnClickListener(listener);
        }

        @Override
        public void setRateDisplay(float rate, int stringResourceId) {
            mTextController.setTemporaryText(
                    getActivity().getString(stringResourceId), 1, TimeUnit.SECONDS);
        }

        @Override
        public void setPositionSeekListener(SeekBar.OnSeekBarChangeListener listener) {
            mPlaybackSeek.setOnSeekBarChangeListener(listener);
        }

        @Override
        public void playbackStarted() {
            mStartStopButton.setImageResource(R.drawable.ic_hold_pause_holo_dark);
        }

        @Override
        public void playbackStopped() {
            mStartStopButton.setImageResource(R.drawable.ic_play_holo_dark);
        }

        @Override
        public void setClipPosition(int clipPositionInMillis, int clipLengthInMillis) {
            int seekBarPosition = Math.max(0, clipPositionInMillis);
            int seekBarMax = Math.max(seekBarPosition, clipLengthInMillis);
            if (mPlaybackSeek.getMax() != seekBarMax) {
                mPlaybackSeek.setMax(seekBarMax);
            }
            mPlaybackSeek.setProgress(seekBarPosition);
            mTextController.setPermanentText(
                    formatAsMinutesAndSeconds(seekBarMax - seekBarPosition));
        }

        @Override
        public int getDesiredClipPosition() {
            return mPlaybackSeek.getProgress();
        }

        @Override
        public void playbackError(Exception e) {
            mRateIncreaseButton.setEnabled(false);
            mRateDecreaseButton.setEnabled(false);
            mStartStopButton.setEnabled(false);
            mPlaybackSeek.setProgress(0);
            mPlaybackSeek.setEnabled(false);
            Toast.makeText(getActivity(), R.string.voicemail_playback_error, Toast.LENGTH_SHORT);
            Log.e(TAG, "Could not play voicemail", e);
        }

        @Override
        public boolean isSpeakerPhoneOn() {
            return getAudioManager().isSpeakerphoneOn();
        }

        @Override
        public void setSpeakerPhoneOn(boolean on) {
            getAudioManager().setMode(AudioManager.MODE_IN_CALL);
            getAudioManager().setSpeakerphoneOn(on);
            if (on) {
                mPlaybackSpeakerphone.setImageResource(R.drawable.ic_sound_holo_dark);
            } else {
                mPlaybackSpeakerphone.setImageResource(R.drawable.ic_sound_holo_dark);
            }
        }
    }

    /**
     * Controls a TextView with dynamically changing text.
     * <p>
     * There are two methods here of interest,
     * {@link TextViewWithMessagesController#setPermanentText(String)} and
     * {@link TextViewWithMessagesController#setTemporaryText(String, long, TimeUnit)}.  The
     * former is used to set the text on the text view immediately, and is used in our case for
     * the countdown of duration remaining during voicemail playback.  The second is used to
     * temporarily replace this countdown with a message, in our case faster voicemail speed or
     * slower voicemail speed, before returning to the countdown display.
     * <p>
     * All the methods on this class must be called from the ui thread.
     */
    private static final class TextViewWithMessagesController {
        private final Object mLock = new Object();
        private final TextView mTextView;
        @GuardedBy("mLock") String mCurrentText = "";
        @GuardedBy("mLock") Runnable mRunnable;

        public TextViewWithMessagesController(TextView textView) {
            mTextView = textView;
        }

        public void setPermanentText(String text) {
            synchronized (mLock) {
                mCurrentText = text;
                // If there's currently a Runnable pending, then we don't alter the display
                // text. The Runnable will use the most recent version of mCurrentText
                // when it completes.
                if (mRunnable == null) {
                    mTextView.setText(text);
                }
            }
        }

        public void setTemporaryText(String text, long duration, TimeUnit units) {
            synchronized (mLock) {
                mTextView.setText(text);
                mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mLock) {
                            // We check for (mRunnable == this) becuase if not true, then another
                            // setTemporaryText call has taken place in the meantime, and this
                            // one is now defunct and needs to take no action.
                            if (mRunnable == this) {
                                mRunnable = null;
                                mTextView.setText(mCurrentText);
                            }
                        }
                    }
                };
                mTextView.postDelayed(mRunnable, units.toMillis(duration));
            }
        }
    }
}
