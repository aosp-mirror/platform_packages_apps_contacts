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

import com.android.contacts.R;
import com.android.ex.variablespeed.MediaPlayerProxy;
import com.android.ex.variablespeed.VariableSpeed;

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
    private ImageButton mPlaybackTrashButton;
    private TextView mPlaybackPositionText;
    private ImageButton mRateDecreaseButton;
    private ImageButton mRateIncreaseButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.playback_layout, container);
        mPlaybackSeek = (SeekBar) view.findViewById(R.id.playback_seek);
        mPlaybackSeek = (SeekBar) view.findViewById(R.id.playback_seek);
        mStartStopButton = (ImageButton) view.findViewById(R.id.playback_start_stop);
        mPlaybackSpeakerphone = (ImageButton) view.findViewById(R.id.playback_speakerphone);
        mPlaybackTrashButton = (ImageButton) view.findViewById(R.id.playback_trash);
        mPlaybackPositionText = (TextView) view.findViewById(R.id.playback_position_text);
        mRateDecreaseButton = (ImageButton) view.findViewById(R.id.rate_decrease_button);
        mRateIncreaseButton = (ImageButton) view.findViewById(R.id.rate_increase_button);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mScheduledExecutorService = createScheduledExecutorService();
        mPresenter = new VoicemailPlaybackPresenter(new PlaybackViewImpl(),
                createMediaPlayer(mScheduledExecutorService), mScheduledExecutorService);
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

    /** Call this from the Activity containing this fragment to set the voicemail to play. */
    public void setVoicemailUri(Uri voicemailUri, boolean startPlaying) {
        mPresenter.setVoicemailUri(voicemailUri, startPlaying);
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
        public void setRateDisplay(float rate) {
            // TODO: This isn't being done yet.  Old rate display code has been removed.
            // Instead we're going to temporarily fade out the track position when you change
            // rate, and display one of the words "slowest", "slower", "normal", "faster",
            // "fastest" briefly when you change speed, before fading back in the time.
            // At least, that's the current thinking.
        }

        @Override
        public void setDeleteButtonListener(View.OnClickListener listener) {
            mPlaybackTrashButton.setOnClickListener(listener);
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
        public void setClipLength(int clipLengthInMillis) {
            mPlaybackSeek.setMax(clipLengthInMillis);
            // TODO: The old code used to set the static lenght-of-clip text field, but now
            // the thinking is that we will only show this text whilst the recording is stopped.
        }

        @Override
        public void setClipPosition(int clipPositionInMillis) {
            mPlaybackSeek.setProgress(clipPositionInMillis);
            mPlaybackPositionText.setText(formatAsMinutesAndSeconds(clipPositionInMillis));
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
}
