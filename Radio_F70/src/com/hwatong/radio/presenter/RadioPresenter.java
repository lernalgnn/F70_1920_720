package com.hwatong.radio.presenter;

import java.util.ArrayList;
import java.util.List;

import android.canbus.ICanbusService;
import android.canbus.ISystemStatusListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.widget.TextView;

import com.hwatong.radio.Channel;
import com.hwatong.radio.Frequence;
import com.hwatong.radio.IService;
import com.hwatong.radio.RadioSharedPreference;
import com.hwatong.radio.ui.iview.IRadioView;
import com.hwatong.statusbarinfo.aidl.IStatusBarInfo;
import com.hwatong.utils.L;
import com.hwatong.utils.Utils;

public class RadioPresenter {

	private static final String thiz = RadioPresenter.class.getSimpleName();

	private IRadioView iRadioView;
	// service
	private IService mService;

	private RadioSharedPreference mRadioPref;

	private static final String SERVICE_ACTION = "com.hwatong.radio.service";

	private static final int MSG_STATUS_CHANGED = 1001;
	private static final int MSG_CHANNELLIST_CHANGED = 1002;
	private static final int MSG_FAVORCHANNELLIST_CHANGED = 1003;
	private static final int MSG_CHANNEL_CHANGED = 1004;
	private static final int MSG_DISPLAY_CHANGED = 1005;
	private static final int MSG_PREVIEW_CHANNEL = 1006;

	private static int mBand = 1;	// 1~5

	private int mFreq = 8750;
	private int mFmFreq, mAmFreq;

	private int previewStartFreq;
	private boolean previewMode = false;

	private ArrayList<Frequence> mList = new ArrayList<Frequence>();
	private ArrayList<Frequence> mFmList = new ArrayList<Frequence>(),
			mAmList = new ArrayList<Frequence>();

	private int mFmPlaying, mAmPlaying;

	protected IStatusBarInfo iStatusBarInfo;
	
	private int initType = -1;			//0表示刚进入是fm，1表示刚进入是am
	
	private boolean isFmInit = true, isAmInit = true; // 恢复出厂首次进入时为true
	

	private ICanbusService mCanbusService;
	
	private Context mContext;
	
	private Handler mHandler = new Handler() {
		public void handleMessage(Message msg) {
			/**
			 * CallBack 事件
			 */
			switch (msg.what) {
			case MSG_DISPLAY_CHANGED:
				L.d(thiz, "refreshView in MSG_DISPLAY_CHANGED");
				iRadioView.refreshView(msg.arg1, msg.arg2);
				break;
			case MSG_CHANNEL_CHANGED:
				L.d(thiz, "refreshView in MSG_CHANNEL_CHANGED");
				iRadioView.refreshView(mBand, mFreq, null);
				break;
			case MSG_FAVORCHANNELLIST_CHANGED:

				break;
			case MSG_CHANNELLIST_CHANGED:
				//iRadioView.hideLoading();
				if (msg.arg1 == 0) { // FM
					refreshFmList();
				} else { // AM
					refreshAmList();
				}
				if (isFm() && msg.arg1 == 0) {
					mList = mFmList;
					L.d(thiz, "refreshView in MSG_CHANNELLIST_CHANGED 1");
					iRadioView.refreshView(mBand, mFreq, mList);
				}
				if (!isFm() && msg.arg1 == 1) {
					mList = mAmList;
					L.d(thiz, "refreshView in MSG_CHANNELLIST_CHANGED 2");
					iRadioView.refreshView(mBand, mFreq, mList);
				}
				break;
			case MSG_STATUS_CHANGED:
				// handleStatusChange();
				if (mService == null)
					return;

				int[] status = new int[2];

				try {
					status = mService.getStatus();
					if (status != null && status.length >= 2) {

					} else {
						return;
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}

				if (status[0] == -1) {
					L.d(thiz, "-1状态！");
					iRadioView.showLoading();			//初始状态，可以认为正在扫描
					
				} else if (status[0] == 0) {
					// OP_IDLE
					L.d(thiz, "空闲状态！");
					
					// 空闲状态判断一下是不是电台预览，如果是的话，停留10s后需要继续扫描，直到回到初始频率
					if (previewMode) {
						mHandler.sendEmptyMessageDelayed(MSG_PREVIEW_CHANNEL,
								10000);
					} else {
						iRadioView.hideLoading();
					}
					
					
				} else if (status[0] == 1) {
					// OP_SCAN
					L.d(thiz, "扫描状态！");
					iRadioView.showLoading();
				}

				break;
			case MSG_PREVIEW_CHANNEL:
				play(mFreq);
				seek(true);
				previewMode = true;
				break;
			}

		}

	};
	
	private void setFMInitFalse() {
		L.d(thiz, "setFMInitFalse!");
		new Thread(new Runnable() {
			@Override
			public void run() {
				SystemClock.sleep(1000);
				mRadioPref.setFMInit(false);
			}
		}).start();
	}
	
	
	private void setAMInitFalse() {
		L.d(thiz, "setAMInitFalse!");
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				SystemClock.sleep(1000);
				mRadioPref.setAMInit(false);
			}
		}).start();
	}
	
	

	public RadioPresenter(IRadioView iRadioView, Context context) {
		this.iRadioView = iRadioView;
		this.mContext = context;
		initBand();
	}
	
	private void initBand() {
		mBand = restoreBand();
		
		L.d(thiz, "initBand : " + mBand);
	}

	public void initSharedPreferences(Context context) {
		mRadioPref = new RadioSharedPreference(context);
	}

	public void bindService(Context context) {
		context.bindService(new Intent(SERVICE_ACTION), mConn, 0);
		context.sendBroadcast(new Intent("com.hwatong.media.START").putExtra("tag", "FM"));

		Intent intent = new Intent();
		intent.setAction("com.remote.hwatong.statusinfoservice");
		context.bindService(intent, mConn2, Context.BIND_AUTO_CREATE);
		
		//锁屏状态下停止预览
		mCanbusService = ICanbusService.Stub.asInterface(ServiceManager.getService("canbus"));
        if (mCanbusService != null) {
	        try {
	        	mCanbusService.addSystemStatusListener("lock", new ISystemStatusListener.Stub() {
	    			@Override
	    			public void onReceived(String value) throws RemoteException {
	    				if("mute_locked".equals(value)){
	    					L.d(thiz, "screen locked !");
	    					doBack();
	    				}else if("unlocked".equals(value)){
	    					
	    				}
	    			}
	    		});
	        } catch (RemoteException e) {
	        	e.printStackTrace();
	        }
        }
	}

	public void unbindService(Context context) {
		if (mService != null) {
			try {
				mService.unregisterCallback(mRadioCallback);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

		context.unbindService(mConn);
		mService = null;

		// 状态栏
		context.unbindService(mConn2);
		iStatusBarInfo = null;

	}
	
	
	public void setInitType(int initType) {
		this.initType = initType;
	}

	private ServiceConnection mConn = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			if (mService != null) {
				try {
					L.d(thiz, "mService.unregisterCallback(mRadioCallback)");
					mService.unregisterCallback(mRadioCallback);
					mService = null;
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {

			L.d(thiz, "onServiceConnected!");

			mService = com.hwatong.radio.IService.Stub.asInterface(service);
			
			if(mService == null) {
				return;
			}
			
			//int[] status = new int[2];

//			try {
//				status = mService.getStatus();
//				if (status != null && status.length >= 2) {
//					isFmInit = isAmInit = status[0] == -1;
//				}
//			} catch (RemoteException e) {
//				e.printStackTrace();
//			}
			
			isFmInit();
			isAmInit();
			
			
			try {
				L.d(thiz, "mService.registerCallback(mRadioCallback)");
				mService.registerCallback(mRadioCallback);
				L.d(thiz, "mService.play()");
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							L.d(thiz, "before mService.play()");
							if(mService != null) {
								mService.play();
							}
							L.d(thiz, "after mService.play()");
						} catch (RemoteException e) {
							e.printStackTrace();
						}
					}
				}).start();

				checkBand();

				if (isFm()) {
					refreshFmList();
					mList = mFmList;
					if(mService != null) {
						mFreq = mFmFreq = mService.getCurrentChannel(0);
					}
					
					// 首次进入-->扫描
					setFMInitFalse();
					//firstScan(mRadioPref.isFMInit());
				} else {
					refreshAmList();
					mList = mAmList;
					if(mService != null) {
						mFreq = mAmFreq = mService.getCurrentChannel(1);
					}
					
					// 首次进入-->扫描
					setAMInitFalse();
					//firstScan(mRadioPref.isAMInit());
				}
				
				L.d(thiz, "onServiceConnected initType : " + initType + " isFm : " + isFm() + " mFreq:" + mFreq + " mlist.size: " + mList.size());
				
				if(initType == 0 && !isFm()){
					realBand();
				} else if(initType == 1 && isFm()) {
					realBand();
				} else {
					iRadioView.refreshView(mBand, mFreq, mList);
				}
				
				initType = -1;
				
				//只调用一次没有效果，不知道原因
				
				iRadioView.showSeekbarThumb();
				
//				mHandler.removeMessages(MSG_STATUS_CHANGED);
//				mHandler.sendEmptyMessageDelayed(MSG_STATUS_CHANGED, 150);

				
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};
	
	/**
	 * 首次扫描
	 * @param scan
	 */
	private void firstScan(boolean scan) {
//		if(scan) {
//			L.d(thiz, "First scan !" );
//			iRadioView.showFirstScan();
//			scan();
//		} else {
//			L.d(thiz, "First fm start false !" );
//		}
	}
	

	protected ServiceConnection mConn2 = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			iStatusBarInfo = null;
		}

		@Override
		public void onServiceConnected(ComponentName arg0, IBinder binder) {
			iStatusBarInfo = IStatusBarInfo.Stub.asInterface(binder);
			if (iStatusBarInfo != null) {
				try {
					iStatusBarInfo.setCurrentPageName("radio");
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}
	};

	/**
	 * Framework Radio服务的回调
	 */
	private final com.hwatong.radio.ICallback mRadioCallback = new com.hwatong.radio.ICallback.Stub() {
		@Override
		public void onStatusChanged() throws RemoteException {
			L.d(thiz, "onStatusChanged!");
			mHandler.removeMessages(MSG_STATUS_CHANGED);
			mHandler.sendEmptyMessage(MSG_STATUS_CHANGED);
			
			//解决seek结束列表对应电台不高亮问题
			mHandler.removeMessages(MSG_CHANNELLIST_CHANGED);
			if(isFm()) {
				mHandler.sendMessage(Message.obtain(mHandler,
						MSG_CHANNELLIST_CHANGED, 0, 0));
 			} else {
 				mHandler.sendMessage(Message.obtain(mHandler,
 						MSG_CHANNELLIST_CHANGED, 1, 0));
 			}
		}

		@Override
		public void onFavorChannelListChanged() throws RemoteException {
			// 收藏列表发生变化
			L.d(thiz, "onFavorChannelListChanged! ");
			mHandler.removeMessages(MSG_FAVORCHANNELLIST_CHANGED);
			mHandler.sendEmptyMessage(MSG_FAVORCHANNELLIST_CHANGED);
		}

		@Override
		public void onDisplayChanged(int band, int freq) throws RemoteException {
			// 频率切换
			
			L.d(thiz, "onDisplayChanged! " + band + " " + freq
					+ " previewMode : " + previewMode + " preview start at : " + previewStartFreq);

			if (band == 0) {
				mFreq = mFmFreq = freq;
			} else {
				mFreq = mAmFreq = freq;
			}

			// 如果是电台预览，判断是不是回到初始频率，回到初始频率后停止扫描。
			if (previewMode && freq == previewStartFreq) {
				stopPreview();
				play(freq);
			}

			checkBand(band);

			Message m = Message.obtain(mHandler, MSG_DISPLAY_CHANGED, mBand,
					freq);
			mHandler.sendMessage(m);
		}

		@Override
		public void onChannelListChanged(int band) throws RemoteException {
			// 搜台列表

			L.d(thiz, "onChannelListChanged! " + band);

			Message m = Message.obtain(mHandler, MSG_CHANNELLIST_CHANGED, band,
					0);
			mHandler.sendMessage(m);
			
			iRadioView.hideLoading();
			
			if (band == 0) {
				setFMInitFalse();					
			} else {
				setAMInitFalse();	
			}
			
		}

		@Override
		public void onChannelChanged() throws RemoteException {
			// 波段切换

			L.d(thiz, "onChannelChanged!");

			checkBand();

			if (mService != null) {
				if (isFm()) {
					mFreq = mFmFreq = mService.getCurrentChannel(0);
					L.d(thiz, "onChannelChanged mFmFreq = " + mFmFreq);
					mHandler.removeMessages(MSG_CHANNEL_CHANGED);
					mHandler.sendEmptyMessage(MSG_CHANNEL_CHANGED);
					
					//切换频段是不会回调onChannelListChanged,所以需要在这里更新列表。
					mHandler.removeMessages(MSG_CHANNELLIST_CHANGED);
					mHandler.sendMessage(Message.obtain(mHandler,
							MSG_CHANNELLIST_CHANGED, 0, 0));
					
					// 首次进入-->扫描
					//firstScan(mRadioPref.isFMInit());
					setFMInitFalse();					
					
				} else {
					mFreq = mAmFreq = mService.getCurrentChannel(1);
					L.d(thiz, "onChannelChanged mAmFreq = " + mAmFreq);
					mHandler.removeMessages(MSG_CHANNEL_CHANGED);
					mHandler.sendEmptyMessage(MSG_CHANNEL_CHANGED);
					
					mHandler.removeMessages(MSG_CHANNELLIST_CHANGED);
					mHandler.sendMessage(Message.obtain(mHandler,
							MSG_CHANNELLIST_CHANGED, 1, 0));
					
					// 首次进入-->扫描
					//firstScan(mRadioPref.isAMInit());
					//mRadioPref.setAMInit(false);
					setAMInitFalse();	
				}
			}
		}
	};

	private synchronized void refreshAmList() {
		// 获取对应频段列表
		try {
			if(mService != null) {
				List<Channel> list = mService.getChannelList(1);
				L.d(thiz, "getChannelList 1 size : " + list.size());
				
				mAmList.clear();
				// 判断是否已收藏
				for (int i = 0; i < list.size(); i++) {
					Frequence v = new Frequence();
					v.frequence = list.get(i).frequence;
					v.isCollected = false;
					for (int j = 0; j < 12; j++) {
						if (v.frequence == getAmPosFreq(j)) {
							v.isCollected = true;
						}
					}
					if (list.get(i).frequence == mFreq) {
						mAmPlaying = i;
					}
					mAmList.add(v);
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	private synchronized void refreshFmList() {
		// 获取对应频段列表
		try {
			if(mService != null){
				List<Channel> list = mService.getChannelList(0);
				L.d(thiz, "getChannelList 0 size : " + list.size());
				
				mFmList.clear();
				// 判断是否已收藏
				for (int i = 0; i < list.size(); i++) {
					L.d(thiz,i + " " + list.get(i).frequence);
					Frequence v = new Frequence();
					v.frequence = list.get(i).frequence;
					v.isCollected = false;
					for (int j = 0; j < 18; j++) {
						if (v.frequence == getFmPosFreq(j)) {
							v.isCollected = true;
						}
					}
					if (list.get(i).frequence == mFreq) {
						mFmPlaying = i;
					}
					mFmList.add(v);
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public void band() {
		mBand++;
		if (mBand > 5) {
			mBand = 1;
		}
		// 需要服务切换
		if (mBand == 4 || mBand == 1) {
			if (mService != null) {
				try {
					L.d(thiz, "mService.band()");
					mService.band();
					//iRadioView.hideLoading();
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			// 只做界面切换
		} else {
			mHandler.removeMessages(MSG_CHANNEL_CHANGED);
			mHandler.sendEmptyMessage(MSG_CHANNEL_CHANGED);
		}
		
		storeBand();
	}
	
	private void storeBand() {
		L.d(thiz, "storeBand mBand = " + mBand);
		if(mContext != null) {
			Settings.System.putInt(mContext.getContentResolver(), "radio_band", mBand);
		}
	}
	
	private int restoreBand() {
		try {
			return Settings.System.getInt(mContext.getContentResolver(), "radio_band");
		} catch (SettingNotFoundException e) {
			e.printStackTrace();
		}
		return 1;
	}
	
	
	
	public void realBand() {
		L.d(thiz, "realBand");
		if (mService != null) {
			try {
				L.d(thiz, "mService.realBand()");
				mService.band();
				//iRadioView.hideLoading();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	

	/**
	 * 请求步进
	 * 
	 * @param up
	 */
	public void tune(final boolean forward) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				if (mService != null) {
					try {
						if (forward) {
							L.d(thiz, "mService.tuneUp()");
							mService.tuneUp();
						} else {
							L.d(thiz, "mService.tuneDown()");
							mService.tuneDown();
						}
					} catch (RemoteException e) {
						e.printStackTrace();
					}
				}
				
			}
		}).start();
	}

	/**
	 * 
	 * 请求有效电台(接口不确定是不是这个)
	 * 
	 * @param up
	 *            0：down 1:up
	 */
	public void seek(boolean forward) {
		if (mService != null) {
			try {
				if (forward) {
					L.d(thiz, "mService.seekUp()");
					mService.seekUp();
				} else {
					L.d(thiz, "mService.seekDown()");
					mService.seekDown();
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 请求扫描频道
	 */
	public void scan() {
		try {
			if (mService != null) {
				int[] status = mService.getStatus();
				if (status != null && status.length >= 2 && status[0] == 1) {
					// 正在扫描
					L.d(thiz, "mService.scan()");
					mService.scan();
					return;
				}
			}
		} catch (RemoteException e1) {
			e1.printStackTrace();
		}
		//非正在扫描
		iRadioView.showLoading();
		if (mService != null) {
			try {
				L.d(thiz, "mService.scan()");
				mService.scan();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 如果正在预览，停止预览，如果正在seek，停止seek，如果正在更新，停止更新
	 */
	public void doBack() {
		L.d(thiz, "doBack");
		if(stopPreview()) {
			return;
		}
		if(stopSeeking()) {
			return;
		}
		stopScaning();
	}
	

	/**
	 * 请求播放频率
	 * 
	 * 频率为-1表示停止当前搜索状态
	 * 
	 * @param frequence
	 */
	public void play(int freq) {
		if (mService != null) {
			try {
				L.d(thiz, "mService.tuneTo(" + freq + ", false)");
				mService.tuneTo(freq, false);
				L.d(thiz, " after mService.tuneTo(" + freq + ", false)");
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * 预览电台
	 */
	public void previewChannels() {
		if (previewMode) {
			stopPreview();
		} else {
			startPreview();
		}

	}

	/**
	 * 判断频率是否已经收藏
	 * 
	 * @return
	 */
	public int checkIfCollected() {
		if (isFm()) {
			for (int i = 0; i < 18; i++) {
				if (getFmPosFreq(i) == mFreq) {
					return i + 1;
				}
			}
		} else {
			for (int i = 0; i < 12; i++) {
				if (getAmPosFreq(i) == mFreq) {
					return i + 1;
				}
			}
		}
		return -1;
	}

	/**
	 * 判断电台类型
	 * 
	 * @return
	 */
	public boolean isFm() {
		return mBand <= 3;
	}

	/**
	 * 判断APP的band与服务的band是不是统一,不统一，APP同步成服务的band。
	 */
	private void checkBand() {
		if (mService != null) {
			try {
				int current = mService.getCurrentBand();
				L.d(thiz, "checkBand() getCurrentBand :" + current);
				checkBand(current);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void checkBand(int band) {
		if (band == 1 && mBand <= 3) {
			mBand = 4;
		}
		if (band == 0 && mBand > 3) {
			mBand = 1;
		}
		
		storeBand();
	}
	
	/**
	 * 切换到语音指定的频段
	 * @param position
	 */
	public void syncBand(int position) {
		if(isFm()) {
			if((position/6 + 1) != mBand) {
				//切换频段界面
				mBand = (position/6 + 1);
				mHandler.removeMessages(MSG_CHANNEL_CHANGED);
				mHandler.sendEmptyMessage(MSG_CHANNEL_CHANGED);
			}
		} else {
			if((position/6 + 1) != mBand - 3) {
				//切换频段界面
				mBand = (position/6 + 1) + 3;
				mHandler.removeMessages(MSG_CHANNEL_CHANGED);
				mHandler.sendEmptyMessage(MSG_CHANNEL_CHANGED);
			}
		}
		
		storeBand();
	}
	

	public int getCurrentBand() {
		return mBand;
	}

	public int getCurrentFreq() {
		return mFreq;
	}

	public void collectFmChannel(TextView textView, int position) {
		textView.setText(Utils.numberToString(mFreq));
		textView.setTag(mFreq);
		mRadioPref.setFmPosFreq(position, mFreq);
	}

	public void collectAmChannel(TextView textView, int position) {
		textView.setText(String.valueOf(mFreq));
		textView.setTag(mFreq);
		mRadioPref.setAmPosFreq(position, mFreq);
	}
	
	/**
	 * 播放频存X
	 * @param pos
	 */
	public void playPosition(int pos) {
		pos -= 1;
		if(isFm()) {
			int freq = getFmPosFreq(pos);
			if(freq == 0) {
				//表示该位置没有预存电台
				return;
			}
			play(freq);
			
			syncBand(pos);
		} else {
			int freq = getAmPosFreq(pos);
			if(freq == 0) {
				//表示该位置没有预存电台
				return;
			}
			play(getAmPosFreq(pos));
			
			syncBand(pos);
		}
	}

	/**
	 * 
	 * @param pos 0 ~ 17
	 * @return
	 */
	public int getFmPosFreq(int pos) {
		return mRadioPref.getFmPosFreq(pos);
	}

	/**
	 * 
	 * @param pos 0 ~ 11
	 * @return
	 */
	public int getAmPosFreq(int pos) {
		return mRadioPref.getAmPosFreq(pos);
	}

	public boolean isCurrentFmFreq(int posInSp) {
		return mRadioPref.getFmPosFreq(posInSp) == mFreq;
	}

	public boolean isCurrentAmFreq(int posInSp) {
		return mRadioPref.getAmPosFreq(posInSp) == mFreq;
	}

	public void startPreview() {
		previewStartFreq = mFreq;
		mHandler.sendEmptyMessage(MSG_PREVIEW_CHANNEL);
		iRadioView.showPreview();
	}

	public synchronized boolean stopPreview() {
		L.d(thiz, "doBack previewMode " + previewMode);
		if(previewMode) {
			L.d(thiz, "stopPreview()");
			//开线程是因为服务端的wait会暂停主线程，然后停止后主线程才响应，导致频率又向下跳动了一次
			new Thread(new Runnable() {
				
				@Override
				public void run() {
					play(mFreq);
				}
			}).start();
			mHandler.removeMessages(MSG_PREVIEW_CHANNEL);
			previewMode = false;
			iRadioView.hidePreview();
			return true;
		}
		return false;
	}
	
	public synchronized boolean stopPreviewWithNoThread() {
		L.d(thiz, "doBack previewMode " + previewMode);
		if(previewMode) {
			L.d(thiz, "stopPreview()");
			play(mFreq);
			mHandler.removeMessages(MSG_PREVIEW_CHANNEL);
			previewMode = false;
			iRadioView.hidePreview();
			return true;
		}
		return false;
	}
	
	
	
	public boolean stopSeeking() {
		if(mService != null) {
			try {
				int[] status = mService.getStatus();
				L.d(thiz, "doBack stopSeeking status " + status[0]);
				//OP_SEEK_UP = 2  OP_SEEK_DOWN = 3
				if (status != null && status.length >= 2 && (status[0] == 2 || status[0] == 3)) {
					// 正在扫描
					L.d(thiz, "stopSeeking()");
					//开线程是因为服务端的wait会暂停主线程，然后停止后主线程才响应，导致频率又向下跳动了一次
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							play(mFreq);
						}
					}).start();
					return true;
				}
				
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		
		return false;
	}
	
	
	public boolean stopScaning() {
		if(mService != null) {
			try {
				int[] status = mService.getStatus();
				L.d(thiz, "doBack stopSeeking status " + status[0]);
				//OP_SCAN = 1
				if (status != null && status.length >= 2 && (status[0] == 1 || status[0] == -1)) {
					// 正在扫描
					L.d(thiz, "stopScaning()");
					//开线程是因为服务端的wait会暂停主线程，然后停止后主线程才响应，导致频率又向下跳动了一次
					new Thread(new Runnable() {
						
						@Override
						public void run() {
							play(mFreq);
							L.d(thiz, "doBack stopSeeking play after sleep");
						}
					}).start();
					return true;
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
	
	
//	private void syncMFreq() {
//		if(mService != null) {
//			try {
//				mFreq = mService.getCurrentChannel(isFm() ? 0 : 1) ;
//			} catch (RemoteException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//	}
	
	
	
	

	/**
	 * 得到空的预存位置
	 * @return
	 */
	public int getEmptyPosition() {
		
		if(isFm()) {
			for(int i = 0; i < 18; i++) {
				if(getFmPosFreq(i) == 0) {
					return i;
				}
			}
		} else {
			for(int i = 0; i < 12; i++) {
				if(getAmPosFreq(i) == 0) {
					return i;
				}
			}
		}
		return 0;
		
	}
	
	public int getStatus() {
		try {
			if(mService != null) {
				return mService.getStatus()[0];
			}
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	
	
	public boolean isFmInit() {
		if(isFmInit) {
			isFmInit = mRadioPref.isFMInit();
		}
		
		L.d(thiz, "isFmInit: " + isFmInit);
		
		return isFmInit;
	}
	
	public boolean isAmInit() {
		if(isAmInit) {
			isAmInit = mRadioPref.isAMInit();
		} 
		
		L.d(thiz, "isAmInit: " + isAmInit);
		
		return isAmInit;
	}
	
	public void setBandFromMode(int band, boolean isNewIntent) {
		L.d(thiz, "setBandFromMode band= " + band + " mBand: " + mBand + " isNewIntent= " + isNewIntent);
		
		//若在预览，则停止预览
		stopPreview();
		
		if(band == mBand + 1 || (band == 1 && mBand == 5)) {
			if(band == 4) {
				initType = 1;
			} else if(band == 1) {
				initType = 0;
			} else {
				band();
			}
		}
	}

	
	public void stop() {
//		if(mService != null) {
//			try {
//				mService.pause();
//			} catch (RemoteException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
	}
	
	
}
