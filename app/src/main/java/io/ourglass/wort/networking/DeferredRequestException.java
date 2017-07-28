package io.ourglass.wort.networking;

import okhttp3.Response;

/**
 * Created by mkahn on 7/18/17.
 */

public class DeferredRequestException extends Exception {

    public Response response;

    public DeferredRequestException(Response resp){
        super();
        this.response = resp;
    }

    public int getStatusCode(){
        return response.code();
    }

}
