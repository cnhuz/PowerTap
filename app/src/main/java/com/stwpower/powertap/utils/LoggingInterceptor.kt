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
        MyLog.d("Sending request ${request.url} on ${chain.connection()}\n${request.headers}")

        // 打印 POST 请求 body
        if (request.method.equals("POST", ignoreCase = true)) {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body?.writeTo(buffer)
            MyLog.d("Request body: ${buffer.readUtf8()}")
        }

        val response = chain.proceed(request)

        val t2 = System.nanoTime()
        val responseBody = response.peekBody(Long.MAX_VALUE).string()
        MyLog.d("Received response for ${response.request.url} in ${(t2 - t1) / 1e6}ms\n${response.headers}")
        MyLog.d("Response body: $responseBody")

        return response
    }
}
