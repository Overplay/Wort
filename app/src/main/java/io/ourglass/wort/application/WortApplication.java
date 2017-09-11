package io.ourglass.wort.application;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import net.danlew.android.joda.JodaTimeAndroid;

import java.util.ArrayList;

import io.ourglass.wort.api.ShellExecutor;
import io.ourglass.wort.api.ShellExecutor2;
import io.ourglass.wort.networking.OGHeaderInterceptor;
import okhttp3.OkHttpClient;


/**
 * Created by mkahn on 5/18/16.
 */
public class WortApplication extends Application {

    public static Context sharedContext;
    public static final String TAG = "WortApplication";

    public static WortApplication thisApplication;

    private static Handler mAppHandler = new Handler();

    public static final OkHttpClient okclient = new OkHttpClient.Builder()
            .addInterceptor(new OGHeaderInterceptor())
            .build();

    @Override
    public void onCreate() {
        super.onCreate();
        // The realm file will be located in Context.getFilesDir() with name "default.realm"
        Log.d(TAG, "Loading Wort application");

        thisApplication = this;
        sharedContext = getApplicationContext();

        JodaTimeAndroid.init(this);

        new ShellExecutor2(new ShellExecutor2.ShellExecutorListener2() {
            @Override
            public void results(ArrayList<String> results) {
                Log.d(TAG, results.toString());
            }
        }).exec("touch /mnt/sdcard/hello.txt");

        new ShellExecutor(new ShellExecutor.ShellExecutorListener() {
            @Override
            public void results(String results) {
                Log.d(TAG, results.toString());
            }
        }).exec("touch /mnt/sdcard/hello2.txt");

    }



}
