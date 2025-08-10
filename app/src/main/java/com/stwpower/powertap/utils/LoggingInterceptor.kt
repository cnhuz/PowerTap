package com.stwpower.powertap.utils

import android.util.Log

import java.io.IOException

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer

class LoggingInterceptor : Interceptor {
    companion object {
        private const val TAG = "powertap"
    }

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val t1 = System.nanoTime()
        Log.d(TAG, "Sending request ${request.url} on ${chain.connection()}\n${request.headers}")

        // 打印 POST 请求 body
        if (request.method.equals("POST", ignoreCase = true)) {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body?.writeTo(buffer)
            Log.d(TAG, "Request body: ${buffer.readUtf8()}")
        }

        val response = chain.proceed(request)

        val t2 = System.nanoTime()
        val responseBody = response.peekBody(Long.MAX_VALUE).string()
        Log.d(TAG, "Received response for ${response.request.url} in ${(t2 - t1) / 1e6}ms\n${response.headers}")
        Log.d(TAG, "Response body: $responseBody")

        return response
    }
}
