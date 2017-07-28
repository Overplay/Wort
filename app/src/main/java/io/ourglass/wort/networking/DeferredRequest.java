package io.ourglass.wort.networking;

import org.jdeferred.Deferred;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import io.ourglass.wort.application.WortApplication;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

/**
 * Created by mkahn on 7/18/17.
 */

public class DeferredRequest  {

    // Alas, Request is final, otherwise I'd have just added some functionality...
    public Request.Builder requestBuilder;
    private Deferred mDeferred;
    private enum ReqType { JSON_ARRAY, JSON_OBJECT, STRING, BINARY, OK_RESPONSE };
    private ReqType mReqType;
    private File mTargetFile;

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public static final MediaType STRING
            = MediaType.parse("text/x-markdown; charset=utf-8");

    public static HashMap<String, String> sharedHeaders = new HashMap<>();

    public static String sessionCookie = null;

    public static void addSharedHeader(String key, String value){
        sharedHeaders.put(key, value);
    }

    public static void removeSharedHeader(String key){
        sharedHeaders.remove(key);
    }

    private void setResponseForReq(Class responseClass){

        if (responseClass == JSONArray.class){

            mReqType = ReqType.JSON_ARRAY;
            mDeferred = new DeferredObject<JSONArray, Exception, Void>();

        } else if (responseClass == JSONObject.class){

            mReqType = ReqType.JSON_OBJECT;
            mDeferred = new DeferredObject<JSONObject, Exception, Void>();

        } else if ( responseClass == Response.class ) {

            mReqType = ReqType.OK_RESPONSE;
            mDeferred = new DeferredObject<Response, Exception, Void>();
        }

        else {
            mDeferred = new DeferredObject<String, Exception, Void>();
            mReqType = ReqType.STRING;
        }

    }

    /**
     * Creates a DeferredResponse with a XXX.class return value
     * @param url
     * @param responseClass
     * @return
     */
    public static DeferredRequest get(String url, Class responseClass){

        DeferredRequest newDR = new DeferredRequest();
        newDR.requestBuilder = new Request.Builder()
                .url(url);
                //.build();

        newDR.setResponseForReq(responseClass);

        return newDR;

    }

    /**
     * JSONObject returning DR (convenience factory)
     * @param url
     * @return
     */
    public static DeferredRequest getJsonObject(String url){
        return DeferredRequest.get(url, JSONObject.class);
    }

    /**
     * JSONArray returning DR (convenience factory)
     * @param url
     * @return
     */
    public static DeferredRequest getJsonArray(String url){
        return DeferredRequest.get(url, JSONArray.class);
    }

    /**
     * String returning DR (convenience factory)
     * @param url
     * @return
     */
    public static DeferredRequest getString(String url){
        return DeferredRequest.get(url, String.class);
    }

    public static DeferredRequest getBinaryFile(String url, File targetFile){

        DeferredRequest newDR = new DeferredRequest();
        newDR.requestBuilder = new Request.Builder()
                .url(url);

        newDR.mReqType = ReqType.BINARY;
        newDR.mDeferred = new DeferredObject<Long, Exception, Void>();
        newDR.mTargetFile = targetFile;

        return  newDR;

    }


    public static DeferredRequest post(String url, JSONObject jsonObject, Class responseClass){

        DeferredRequest newDR = new DeferredRequest();

        RequestBody body = RequestBody.create(JSON, jsonObject.toString());

        newDR.requestBuilder = new Request.Builder()
                .url(url)
                .post(body);

        newDR.setResponseForReq(responseClass);

        return newDR;

    }

    public static DeferredRequest post(String url, String uploadString, Class responseClass){

        DeferredRequest newDR = new DeferredRequest();

        RequestBody body = RequestBody.create(STRING, uploadString);

        newDR.requestBuilder = new Request.Builder()
                .url(url)
                .post(body);

        newDR.setResponseForReq(responseClass);

        return newDR;

    }

    public static DeferredRequest post(String url, RequestBody body, Class responseClass){

        DeferredRequest newDR = new DeferredRequest();

        newDR.requestBuilder = new Request.Builder()
                .url(url)
                .post(body);

        newDR.setResponseForReq(responseClass);

        return newDR;

    }


    public Promise go(){

        for (String key: sharedHeaders.keySet()){
            requestBuilder.addHeader(key, sharedHeaders.get(key));
        }

        Request request = requestBuilder.build(); // make it concrete

        WortApplication.okclient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mDeferred.reject(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                if(response.isSuccessful()) {

                        try {
                            switch (mReqType) {
                                case JSON_ARRAY:
                                    mDeferred.resolve(new JSONArray(response.body().string()));
                                    break;
                                case JSON_OBJECT:
                                    mDeferred.resolve(new JSONObject(response.body().string()));
                                    break;
                                case STRING:
                                    mDeferred.resolve(response.body().string());
                                    break;
                                case BINARY:
                                    BufferedSink sink = Okio.buffer(Okio.sink(mTargetFile));
                                    Long size = sink.writeAll(response.body().source());
                                    sink.close();
                                    mDeferred.resolve(size);
                                    break;
                                case OK_RESPONSE:
                                    mDeferred.resolve(response);
                                    break;
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                            mDeferred.reject(e);
                        }


                } else {
                    //Non-200 response, pass the response back for inspection
                    mDeferred.reject(new DeferredRequestException(response));
                }


            }
        });

        return mDeferred.promise();
    }


}


/*

EXAMPLE USAGES:

DeferredRequest dr = DeferredRequest.getJson(OGSettings.getBelliniDMAddress()+"/ogdevice", JSONArray.class);
        dr.go()
                .then(new DoneFilter<JSONArray, Integer>() {
                    @Override
                    public Integer filterDone(JSONArray result) {
                        return result.length();
                    }
                })
                .done(new DoneCallback<Integer>() {
                    @Override
                    public void onDone(Integer result) {
                        Log.d(TAG, "There are "+result+" devices");
                    }
                })
                .fail(new FailCallback() {
                    @Override
                    public void onFail(Object result) {
                        Log.e(TAG, "Fail: "+result.toString());
                    }
                });


 */