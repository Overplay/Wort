package io.ourglass.wort.application;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

import net.danlew.android.joda.JodaTimeAndroid;

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

    }



}
