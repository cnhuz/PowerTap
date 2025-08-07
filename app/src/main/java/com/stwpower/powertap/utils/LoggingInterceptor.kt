package com.stwpower.powertap.utils;

import android.util.Log;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;

public class LoggingInterceptor implements Interceptor {
    private static final String TAG = "网络请求";

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        long t1 = System.nanoTime();
        Log.d(TAG, String.format("Sending request %s on %s%n%s",
                request.url(), chain.connection(), request.headers()));

        if (request.method().compareToIgnoreCase("post")==0) {
            Request copy = request.newBuilder().build();
            Buffer buffer = new Buffer();
            if(copy.body() != null)
                copy.body().writeTo(buffer);
            Log.d(TAG, "Request body: " + buffer.readUtf8());
        }

        Response response = chain.proceed(request);

        long t2 = System.nanoTime();
        String responseBody = response.peekBody(Long.MAX_VALUE).string();
        Log.d(TAG, String.format("Received response for %s in %.1fms%n%s",
                response.request().url(), (t2 - t1) / 1e6d, response.headers()));
        Log.d(TAG, "Response body: " + responseBody);
        return response;
    }
}