package com.hwatong.platformadapter.utils;

import android.util.Log;

public class L {
	private static boolean DEBUG = true;
	private static String TAG = "Voice";
	
	public static void d(String clazz, String info) {
		if(DEBUG) {
			Log.d(TAG, "[" + clazz + "] " + info);
		}
	}
	
	public static void dRoll(String clazz, String info){
		if(!DEBUG) {
			Log.d(TAG + "_roll", "[" + clazz + "] " + info);
		}
	}
	
}
