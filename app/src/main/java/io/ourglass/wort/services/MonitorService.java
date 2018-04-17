package io.ourglass.wort.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import static io.ourglass.wort.BootActivity.MAIN_APK_NAME;

/**
 * Created by mkahn on 10/29/17.
 */

public class MonitorService extends Service {

    private static final String TAG = "MonitorService";
    private static final long ASSUME_MF_DEAD_DELTA_T = 7500; // 7.5 seconds without a kick, and we are dead.

    private Handler mHandler;
    private boolean mWatchdogArmed = true;
    private long mWDTInterval = ASSUME_MF_DEAD_DELTA_T;
    private long mLastHeardFromMf = 0;

    private Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {

            long deltaT = System.currentTimeMillis() - mLastHeardFromMf;

            if ( deltaT > ASSUME_MF_DEAD_DELTA_T ){

                Log.e(TAG, "MF has not seen an ACK in " + ASSUME_MF_DEAD_DELTA_T + "ms!");

                if (mWatchdogArmed==true){
                    Log.d(TAG, "MF being restarted.");
                    // true indicates it is a restart
                    startMainframe(true);
                } else {
                    Log.d(TAG, "WDT is disarmed, so not restarting MF.");
                }

            } else {
                Log.d(TAG, "Heard from MF in the last " + ASSUME_MF_DEAD_DELTA_T +"ms, cool beans.");
            }

            mHandler.postDelayed(this, ASSUME_MF_DEAD_DELTA_T);

        }
    };

    BroadcastReceiver wdtKickRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLastHeardFromMf = System.currentTimeMillis();
            mWatchdogArmed = intent.getBooleanExtra("watchdogEnabled", true);
            long mWDTInterval = intent.getLongExtra("wdtInterval", ASSUME_MF_DEAD_DELTA_T);
            Log.d(TAG, "Got WDT kick from MF. Watchdog still enabled: " + mWatchdogArmed);
        }
    };

    private void startMainframe(boolean isARestart){

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(MAIN_APK_NAME);
        if (launchIntent != null) {
            launchIntent.putExtra("restart", isARestart);
            startActivity(launchIntent);
        }

    }


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        super.onCreate();
        initHandler();
        registerReceiver( wdtKickRx, new IntentFilter("io.ourglass.MF_WDT_KICK"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHandler.removeCallbacksAndMessages(null); // in case of re-entry?
        mHandler.postDelayed(mWatchdogRunnable, ASSUME_MF_DEAD_DELTA_T);
        startMainframe(false);
        return Service.START_STICKY;
    }

    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        unregisterReceiver(wdtKickRx);
        super.onDestroy();
    }

    private void initHandler(){
        HandlerThread handlerThread = new HandlerThread("OGLogHandlerThread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        mHandler = new Handler(looper);
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
