/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.hwatong.settings.fragment;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.VolumePreference.VolumeStore;
import android.provider.Settings;
import android.provider.Settings.System;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import com.hwatong.settings.R;
import com.hwatong.settings.SettingsPreferenceFragment;
import com.hwatong.settings.Utils;
import com.hwatong.providers.carsettings.SettingsProvider;

/**
 * Special preference type that allows configuration of both the ring volume and
 * notification volume.
 */
public class MyRingerVolumeFragment extends SettingsPreferenceFragment implements OnClickListener, SeekBar.OnSeekBarChangeListener{
	private static final String TAG = "MyRingerVolumeFragment";

	private SeekBarVolumizer [] mSeekBarVolumizer;

	// These arrays must all match in length and order
	private static final int[] SEEKBAR_ID = new int[] {
		R.id.media_volume_seekbar,
		R.id.notification_volume_seekbar,
		R.id.voicecall_volume_seekbar
	};
	private static final int[] SEEKBAR_MINUS_ID = new int[] {
		R.id.media_volume_minus,
		R.id.notification_volume_minus,
		R.id.voicecall_volume_minus
	};
	private static final int[] SEEKBAR_PLUS_ID = new int[] {
		R.id.media_volume_plus,
		R.id.notification_volume_plus,
		R.id.voicecall_volume_plus
	};
	private static final int[] TEXTVIEW_ID = new int[] {
		R.id.media_volume_text,
		R.id.notification_volume_text,
		R.id.voicecall_volume_text
	};
	private static final int[] MY_SEEKBAR_ID = new int[] {
		R.id.auto_volume_seekbar,
		R.id.boot_volume_seekbar
	};
	private static final int[] MY_SEEKBAR_MINUS_ID = new int[] {
		R.id.auto_volume_minus,
		R.id.boot_volume_minus
	};
	private static final int[] MY_SEEKBAR_PLUS_ID = new int[] {
		R.id.auto_volume_plus,
		R.id.boot_volume_plus
	};
	private static final int[] MY_TEXTVIEW_ID = new int[] {
		R.id.auto_volume_text,
		R.id.boot_volume_text,
	};
	private static final String[] MY_SEEKBAR_TYPENAME = new String[] {
		SettingsProvider.SOUND_AUTO_VOLUME,
		SettingsProvider.SOUND_BOOT_VOLUME
	};
	private static final String[] MY_SEEKBAR_DEFAULT = new String[] {
		SettingsProvider.DEFAULT_AUTO_VOLUME,
		SettingsProvider.DEFAULT_BOOT_VOLUME
	};
	private static final int[] MY_SEEKBAR_MAX = new int[] {
		7,
		20
	};
	private static final int[] MY_SEEKBAR_MIN = new int[] {
		0,
		5
	};

	private static final int[] SEEKBAR_TYPE = new int[] {
		AudioManager.STREAM_MUSIC,
		AudioManager.STREAM_NOTIFICATION,
		AudioManager.STREAM_RING
	};

	private SeekBar[] mSeekBars = new SeekBar[SEEKBAR_ID.length];
	private SeekBar[] mMySeekBars = new SeekBar[MY_SEEKBAR_ID.length];
	private TextView[] mTextViews= new TextView[TEXTVIEW_ID.length];
	private TextView[] mMyTextViews= new TextView[MY_TEXTVIEW_ID.length];
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			updateSlidersAndMutedStates();
		}
	};

	private void updateSlidersAndMutedStates() {
		for (int i = 0; i < SEEKBAR_TYPE.length; i++) {
			int streamType = SEEKBAR_TYPE[i];
			boolean muted = mAudioManager.isStreamMute(streamType);

			if (mSeekBars[i] != null) {
				int volume = mAudioManager.getStreamVolume(streamType);
				mSeekBars[i].setProgress(volume);
				if (mTextViews[i]!=null) mTextViews[i].setText(String.valueOf(volume));
				if (streamType != mAudioManager.getMasterStreamType() && muted) {
					mSeekBars[i].setEnabled(false);
				} else {
					mSeekBars[i].setEnabled(true);
				}
			}
		}
	}

	private AudioManager mAudioManager;
	private View mContentView=null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		mSeekBarVolumizer = new SeekBarVolumizer[SEEKBAR_ID.length];
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		mContentView = inflater.inflate(R.layout.preference_dialog_ringervolume, container, false);  

		for (int i = 0; i < SEEKBAR_ID.length; i++) {
			SeekBar seekBar = (SeekBar) mContentView.findViewById(SEEKBAR_ID[i]);
			TextView textview = (TextView)mContentView.findViewById(TEXTVIEW_ID[i]);
			mSeekBars[i] = seekBar;
			mTextViews[i] = textview;
			if (SEEKBAR_TYPE[i] == AudioManager.STREAM_MUSIC) {
				mSeekBarVolumizer[i] = new SeekBarVolumizer(getActivity(), seekBar, textview, SEEKBAR_TYPE[i], getMediaVolumeUri(getActivity())) {
					public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
						if (fromTouch) {
							Intent intent = new Intent("com.hwatong.SET_MUSIC_MUTE");
							intent.putExtra("status", "off");
							getActivity().sendBroadcast(intent);
						}
						super.onProgressChanged(seekBar, progress, fromTouch);
					}
				};
			} else if(SEEKBAR_TYPE[i] == AudioManager.STREAM_RING){
				mSeekBarVolumizer[i] = new SeekBarVolumizer(getActivity(), seekBar, textview, SEEKBAR_TYPE[i], getMediaVolumeUri(getActivity()));
			} else {
				mSeekBarVolumizer[i] = new SeekBarVolumizer(getActivity(), seekBar, textview, SEEKBAR_TYPE[i]);
			}
		}
		for (int i = 0; i < SEEKBAR_MINUS_ID.length; i++) {
			mContentView.findViewById(SEEKBAR_MINUS_ID[i]).setOnClickListener(this);
		}
		for (int i = 0; i < SEEKBAR_PLUS_ID.length; i++) {
			mContentView.findViewById(SEEKBAR_PLUS_ID[i]).setOnClickListener(this);
		}
		for (int i = 0; i < MY_SEEKBAR_ID.length; i++) {
			String volume = Utils.getCarSettingsString(getContentResolver(), MY_SEEKBAR_TYPENAME[i], MY_SEEKBAR_DEFAULT[i]);

			SeekBar seekBar = (SeekBar) mContentView.findViewById(MY_SEEKBAR_ID[i]);
			TextView textview = (TextView)mContentView.findViewById(MY_TEXTVIEW_ID[i]);
			seekBar.setMax(MY_SEEKBAR_MAX[i] - MY_SEEKBAR_MIN[i]);
			seekBar.setProgress(Integer.valueOf(volume)-MY_SEEKBAR_MIN[i]);
			textview.setText(String.valueOf(Integer.valueOf(volume)));
			seekBar.setOnSeekBarChangeListener(this);
			mMySeekBars[i] = seekBar;
			mMyTextViews[i] = textview;
		}
		for (int i = 0; i < MY_SEEKBAR_MINUS_ID.length; i++) {
			mContentView.findViewById(MY_SEEKBAR_MINUS_ID[i]).setOnClickListener(this);
		}
		for (int i = 0; i < MY_SEEKBAR_PLUS_ID.length; i++) {
			mContentView.findViewById(MY_SEEKBAR_PLUS_ID[i]).setOnClickListener(this);
		}

		// Load initial states from AudioManager
		updateSlidersAndMutedStates();

		return mContentView;
	}

	private Uri getMediaVolumeUri(Context context) {
		return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"+ context.getPackageName()+ "/" + R.raw.media_volume);
	}

	/**
	 * Turns a {@link SeekBar} into a volume control.
	 */
	public class SeekBarVolumizer implements OnSeekBarChangeListener, Runnable {

		private Context mContext;
		private Handler mHandler = new Handler();

		private AudioManager mAudioManager;
		private int mStreamType;
		private int mOriginalStreamVolume;
		private Ringtone mRingtone;

		private int mLastProgress = -1;
		private SeekBar mSeekBar;
		private TextView mTextView;
		private int mVolumeBeforeMute = -1;
		private VolumeReceiver mVolumeReceiver = new VolumeReceiver();
		private class VolumeReceiver extends BroadcastReceiver{
			@Override
			public void onReceive(Context context, Intent intent) {
				if(intent.getAction().equals(AudioManager.VOLUME_CHANGED_ACTION)){
					int type = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
					int volume = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, 0);
					Log.d(TAG, "onRecerver:StreamType=" + type + ", value="+volume);
					if (type==mStreamType && mSeekBar!=null && type==AudioManager.STREAM_MUSIC)
						mSeekBar.setProgress(volume);
				}
			}
		}	

		private ContentObserver mVolumeObserver = new ContentObserver(mHandler) {
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				Log.d(TAG, "onChange");
				if (mSeekBar != null && mAudioManager != null) {
					int volume = mAudioManager.getStreamVolume(mStreamType);
					mSeekBar.setProgress(volume);
				}
			}
		};

		public SeekBarVolumizer(Context context, SeekBar seekBar, TextView textview, int streamType) {
			this(context, seekBar, textview, streamType, null);
		}

		public SeekBarVolumizer(Context context, SeekBar seekBar, TextView textview, int streamType, Uri defaultUri) {
			mContext = context;
			mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
			mStreamType = streamType;
			mSeekBar = seekBar;
			mTextView = textview;
			initSeekBar(seekBar, defaultUri);
		}

		private void initSeekBar(SeekBar seekBar, Uri defaultUri) {
			seekBar.setMax(mAudioManager.getStreamMaxVolume(mStreamType));
			mOriginalStreamVolume = mAudioManager.getStreamVolume(mStreamType);
			seekBar.setProgress(mOriginalStreamVolume);
			seekBar.setOnSeekBarChangeListener(this);
			mTextView.setText(String.valueOf(mOriginalStreamVolume));

			mContext.getContentResolver().registerContentObserver(System.getUriFor(System.VOLUME_SETTINGS[mStreamType]),false, mVolumeObserver);
			mContext.registerReceiver(mVolumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));

			if (defaultUri == null) {
				if (mStreamType == AudioManager.STREAM_RING) {
					defaultUri = Settings.System.DEFAULT_RINGTONE_URI;
				} else if (mStreamType == AudioManager.STREAM_NOTIFICATION) {
					defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
				} else {
					defaultUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
				}
			}

			mRingtone = RingtoneManager.getRingtone(mContext, defaultUri);

			if (mRingtone != null) {
				mRingtone.setStreamType(mStreamType);
			}
		}

		public void stop() {
			Log.d(TAG, "stop");
			mContext.getContentResolver().unregisterContentObserver(mVolumeObserver);
			mContext.unregisterReceiver(mVolumeReceiver);
			mSeekBar.setOnSeekBarChangeListener(null);
		}

		public void revertVolume() {
			mAudioManager.setStreamVolume(mStreamType, mOriginalStreamVolume, 0);
		}

		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromTouch) {
			if (!fromTouch) {
				return;
			}

			postSetVolume(progress);
		}

		void postSetVolume(int progress) {
			// Do the volume changing separately to give responsive UI
			mLastProgress = progress;
			mTextView.setText(String.valueOf(progress));
			mHandler.removeCallbacks(this);
			mHandler.post(this);
		}

		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		public void onStopTrackingTouch(SeekBar seekBar) {
			if (!isSamplePlaying()) {
			}
		}

		public void run() {
			mAudioManager.setStreamVolume(mStreamType, mLastProgress, 0);
		}

		public boolean isSamplePlaying() {
			return mRingtone != null && mRingtone.isPlaying();
		}

		public SeekBar getSeekBar() {
			return mSeekBar;
		}

		public void changeVolumeBy(int amount) {
			mSeekBar.incrementProgressBy(amount);
			if (!isSamplePlaying()) {
			}
			postSetVolume(mSeekBar.getProgress());
			mVolumeBeforeMute = -1;
		}

		public void muteVolume() {
			if (mVolumeBeforeMute != -1) {
				mSeekBar.setProgress(mVolumeBeforeMute);
				postSetVolume(mVolumeBeforeMute);
				mVolumeBeforeMute = -1;
			} else {
				mVolumeBeforeMute = mSeekBar.getProgress();
				mSeekBar.setProgress(0);
				postSetVolume(0);
			}
		}

		public void onSaveInstanceState(VolumeStore volumeStore) {
			if (mLastProgress >= 0) {
				volumeStore.volume = mLastProgress;
				volumeStore.originalVolume = mOriginalStreamVolume;
			}
		}

		public void onRestoreInstanceState(VolumeStore volumeStore) {
			if (volumeStore.volume != -1) {
				mOriginalStreamVolume = volumeStore.originalVolume;
				mLastProgress = volumeStore.volume;
				postSetVolume(mLastProgress);
			}
		}
	}

	@Override
	public void onClick(View v) {

		switch(v.getId()) {
		case R.id.media_volume_minus:
			setProgress(0,false);
			break;
		case R.id.media_volume_plus:
			setProgress(0,true);
			break;
		case R.id.notification_volume_minus:
			setProgress(1,false);
			break;
		case R.id.notification_volume_plus:
			setProgress(1,true);
			break;
		case R.id.voicecall_volume_minus:
			setProgress(2,false);
			break;
		case R.id.voicecall_volume_plus:
			setProgress(2,true);
			break;
		case R.id.auto_volume_minus:
			setMyProgress(0,false);
			break;
		case R.id.auto_volume_plus:
			setMyProgress(0,true);
			break;
		case R.id.boot_volume_minus:
			setMyProgress(1,false);
			break;
		case R.id.boot_volume_plus:
			setMyProgress(1,true);
			break;
		}
	}
	private void setProgress(int index, boolean add) {
		int progress = mSeekBars[index].getProgress();
		int max = mSeekBars[index].getMax();
		int step =  max/10;
		if (step<1) step=1;
		if (add) {
			if (progress<max) {
				int value = (progress+step>max)?max:progress+step;
				mSeekBars[index].setProgress(value);
				if (mTextViews[index]!=null) mTextViews[index].setText(String.valueOf(value));
				mAudioManager.setStreamVolume(SEEKBAR_TYPE[index], value, 0);
			}
		}else {
			if (progress>0) {
				int value = (progress-step<0)?0:progress-step;
				mSeekBars[index].setProgress(value);
				if (mTextViews[index]!=null) mTextViews[index].setText(String.valueOf(value));
				mAudioManager.setStreamVolume(SEEKBAR_TYPE[index], value, 0);
			}
		}
	}
	private void setMyProgress(int index, boolean add) {
		int progress = mMySeekBars[index].getProgress();
		int max = mMySeekBars[index].getMax();
		int step =  max/10;
		if (step<1) step=1;
		if (add) {
			if (progress<max) {
				int value = (progress+step>max)?max:progress+step;
				mMySeekBars[index].setProgress(value);
				if (mMyTextViews[index]!=null) mMyTextViews[index].setText(String.valueOf(value+MY_SEEKBAR_MIN[index]));
				Utils.putCarSettingsString(getContentResolver(), MY_SEEKBAR_TYPENAME[index], String.valueOf(value+MY_SEEKBAR_MIN[index]));
			}
		}else {
			if (progress>0) {
				int value = (progress-step<0)?0:progress-step;
				mMySeekBars[index].setProgress(value);
				if (mMyTextViews[index]!=null) mMyTextViews[index].setText(String.valueOf(value+MY_SEEKBAR_MIN[index]));
				Utils.putCarSettingsString(getContentResolver(), MY_SEEKBAR_TYPENAME[index], String.valueOf(value+MY_SEEKBAR_MIN[index]));
			}
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		// TODO Auto-generated method stub
		for (int i = 0; i < SEEKBAR_ID.length; i++) {
			mSeekBarVolumizer[i].stop();
			mSeekBarVolumizer[i] = null;
		}
		super.onDestroy();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		for (int i = 0; i < MY_TEXTVIEW_ID.length; i++) {
			if (seekBar.getId()==mMySeekBars[i].getId()) { 
				mMyTextViews[i].setText(String.valueOf(progress+MY_SEEKBAR_MIN[i]));
				if (fromUser) {
					Utils.putCarSettingsString(getContentResolver(), MY_SEEKBAR_TYPENAME[i], String.valueOf(progress+MY_SEEKBAR_MIN[i]));
				}
				break;
			}
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub

	}
}
