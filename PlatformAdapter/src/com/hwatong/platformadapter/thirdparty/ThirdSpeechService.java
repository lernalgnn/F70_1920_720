package com.hwatong.platformadapter.thirdparty;

import java.util.ArrayList;
import java.util.List;

import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import com.hwatong.platformadapter.PlatformAdapterApp;
import com.hwatong.statusbarinfo.aidl.IStatusBarInfo;
import com.iflytek.platform.type.PlatformCode;
import com.iflytek.platformservice.PlatformService;
/**
 * @date 2017-11-22
 * @author caochao
 */
public class ThirdSpeechService extends Service implements ResultListener{
    
    private static final String TAG = "Voice";
    
	public static boolean state = false ;
	
	protected IStatusBarInfo iStatusBarInfo;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG , "ThirdSpeechService is started!");
        PlatformAdapterApp.getPlatformClientInstance().setResultListener(this);
        
        //add++ 绑定状态栏服务
        Intent intent2 = new Intent();
		intent2.setAction("com.remote.hwatong.statusinfoservice");
		bindService(intent2, mConn2, Context.BIND_AUTO_CREATE);
        
	    return super.onStartCommand(intent, flags, startId);
    }
	
	//add++ 得到aidl回调
	protected ServiceConnection mConn2 = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			iStatusBarInfo = null;
		}

		@Override
		public void onServiceConnected(ComponentName arg0, IBinder binder) {
			iStatusBarInfo = IStatusBarInfo.Stub.asInterface(binder);
		}
	};
	
	
	
    private IService.Stub iservice = new IService.Stub() {
		@Override
		public void registCallBack(CallBack callBack) throws RemoteException {
			if(callBack == null){
				Log.d(TAG, "third callback is null");
				return ;
			}
			synchronized(callbacks){				
				for(int i =0 ; i<callbacks.size(); i++){
					if(callBack.asBinder().equals(callbacks.get(i).mCallback.asBinder())){
					    Log.d(TAG, "this callback had registed !");
					    //state = true ;
						return ;
					}
				}
				try {
					CallBackListener l = new CallBackListener(callBack);
					callBack.asBinder().linkToDeath(l, 0);
					callbacks.add(new CallBackListener(callBack));
					state = true ;
					Log.d(TAG, "a new client regist");					
				} catch (Exception e) {
					e.printStackTrace();
					Log.d(TAG, "a new client regist error:"+e.toString()+";"+e.getMessage());					
				}
			}
		}
		@Override
		public void unregistCallBack(CallBack callBack) throws RemoteException {
            		synchronized(callbacks){
                /**
				for(int i =0 ; i<callbacks.size(); i++){
					if(callBack.asBinder().equals(callbacks.get(i).mCallback.asBinder())){
					    callBack.asBinder().unlinkToDeath(callbacks.get(i), 0);
						callbacks.remove(i);
						state = false;
						return ;
					}
				}*/
                state = false;
                for(int i =0 ; i<callbacks.size(); i++){
                    //callBack.asBinder().unlinkToDeath(callbacks.get(i), 0);
                    callbacks.remove(i);
                }
                //callbacks.clear();
                return ;
            }
		}
        @Override
        public void openVoiceHelp() throws RemoteException {
            Log.d(TAG, "third open voice"); 
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.iflytek.cutefly.speechclient", "com.iflytek.autofly.SpeechClientService"));
            intent.putExtra("fromservice", "com.hwatong.platformadapter");
            startService(intent);
        }
        @Override
        public void switchSpeechMic(int state) throws RemoteException {
            Log.d(TAG, "third switch mic for voice:" + state);
            if(state == 0){
                PlatformService.platformCallback.systemStateChange(PlatformCode.STATE_SPEECHOFF);
            } else if(state == 1){
                PlatformService.platformCallback.systemStateChange(PlatformCode.STATE_SPEECHON);
            }
        }
	};
	
	private ArrayList<CallBackListener> callbacks = new ArrayList<CallBackListener>();
	
	private final class CallBackListener implements IBinder.DeathRecipient{
		
        final CallBack mCallback ;
        
		public CallBackListener(CallBack callback) {
			this.mCallback = callback;
		}
		@Override
		public void binderDied() {
			synchronized(callbacks){
				callbacks.remove(this);
			}
			if(mCallback!=null){
				mCallback.asBinder().unlinkToDeath(this, 0);
			}
		}
	}
	/**
	 * 分发语义给第三方
	 * @return 
	 */
	private String doAction(String result){
        Log.d(TAG, "third voice daAction:"+result);
		for(int i = 0 ; i<callbacks.size() ; i++ ){
			try {
                String s = callbacks.get(i).mCallback.onResult(result);
			    Log.d(TAG, "小马返回:" + s);
				return s ; 
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
        return null;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return iservice;
	}

	@Override
	public String onResult(String result) {
		return doAction(result);
	}
	public static final int START_RECORDER = 1 ;
	public static final int STOP_RECORDER = 0 ;
	@Override
	public void onState(int state) {
        for(int i = 0 ; i<callbacks.size() ; i++ ){
        try {
	            callbacks.get(i).mCallback.onStatus(state);
	        } catch (RemoteException e) {
	            e.printStackTrace();
	        }
        }		
	}
	
	//add++ 添加一个同步状态栏的代码
	@Override
	public void syncStatusBar(boolean show) {
		if (iStatusBarInfo != null) {
			try {
				Log.d(TAG, "syncStatusBar voice");
				if(isIflytekFroground()) {
					iStatusBarInfo.setCurrentPageName("voice");
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	}

	//add++ 添加判断前台activity是不是讯飞的activity
	private boolean isIflytekFroground() {
		ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningTaskInfo> cn = am.getRunningTasks(1);
		RunningTaskInfo taskInfo = cn.get(0);
		ComponentName name = taskInfo.topActivity;
		Log.d(TAG, "top name : " + name.getClassName());
		if (!TextUtils.isEmpty(name.getClassName()) && name.getClassName().contains("com.iflytek.autofly.activity")) {
			return true;
		}
		return false;
	}
	

}