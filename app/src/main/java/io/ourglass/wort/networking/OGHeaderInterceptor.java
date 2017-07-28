package io.ourglass.wort.networking;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import static io.ourglass.wort.settings.OGConstants.DEVICE_AUTH_HDR;


/**
 * Created by mkahn on 5/30/17.
 */

public class OGHeaderInterceptor implements Interceptor {

    public static String sessionCookie;
    //public static String xAuthHeaderValue = "x-ogdevice-1234";
    public static String xAuthHeaderKey = "x-dev-authorization";

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        Request newRequest;

        // Can I get HACKA?
        // TODO: This is shite. Box should authorize somehow.
        newRequest = request.newBuilder()
                .addHeader(xAuthHeaderKey, DEVICE_AUTH_HDR)
                .build();

        if (sessionCookie!=null){
            newRequest = newRequest.newBuilder()
                    .addHeader("Cookie", sessionCookie)
                    .build();
        }

        return chain.proceed(newRequest);
    }
}