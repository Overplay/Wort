package io.ourglass.wort.networking;

import android.util.Log;

import org.jdeferred.DoneFilter;
import org.jdeferred.Promise;
import org.json.JSONException;
import org.json.JSONObject;

import io.ourglass.wort.settings.OGSettings;
import okhttp3.MediaType;
import okhttp3.Response;

/**
 * Created by mkahn on 3/13/17.
 */

public class BelliniDMLiteAPI {

    public static final String TAG = "BelliniDMLiteAPI";

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public static String SESSION_COOKIE;

    public static String fullUrlForApp(String appId){
        return OGSettings.getBelliniDMAddress() + "/blueline/opp/" + appId + "/app/tv";
    }

    /**
     * Convenience factory for initial params object. This is a HACK since deviceUDID is in the
     * main application.
     * @return
     */
    private static JSONObject getParamsWithDeviceUDID(){
        JSONObject params = new JSONObject();
        try {
            // We don't have a device UDID since this is in Bucanero, so this is a limited login
            params.put("deviceUDID", "wort");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return params;
    }

    /**
     * This won't work until "wort" is an official device in BelliniDM :D
     * @return
     */
    public static Promise<String, Exception, Void> authenticateDevice(){

        JSONObject params = getParamsWithDeviceUDID();

        DeferredRequest dr = DeferredRequest.post(OGSettings.getBelliniDMAddress() + "/auth/device", params, Response.class);
        return dr.go()
                .then(new DoneFilter<Response, String>() {

                    @Override
                    public String filterDone(Response response) {

                        Log.v(TAG, "Device Login Request successful");
                        if (response.headers().get("set-cookie")!=null){
                            String cookie = response.headers().get("set-cookie");
                            SESSION_COOKIE = cookie.split(";")[0];
                        }

                        return SESSION_COOKIE;
                    }
                });

    }

    public static Promise<JSONObject, Exception, Void> pingCloud(){
        return DeferredRequest.get(OGSettings.getBelliniDMAddress()+"/ogdevice/pingcloud", JSONObject.class).go();
    }


}
