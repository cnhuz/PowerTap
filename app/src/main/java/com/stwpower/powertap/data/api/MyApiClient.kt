package com.stwpower.powertap.data.api

import android.util.Log
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import com.stwpower.powertap.ConfigLoader
import com.stwpower.powertap.domain.MyResponse
import com.stwpower.powertap.utils.LoggingInterceptor
import java.io.IOException
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import javax.net.ssl.*
import java.security.cert.X509Certificate

object MyApiClient {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(LoggingInterceptor())
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(ConfigLoader.apiUrl + "/")
        .client(getUnsafeOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val service: MyApiService = retrofit.create(MyApiService::class.java)

    fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )

            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, SecureRandom())
            }
            val sslSocketFactory = sslContext.socketFactory

            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .addInterceptor(LoggingInterceptor())
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()

        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Throws(ConnectionTokenException::class)
    fun createConnectionToken(): String {
        return try {
            val result = service.getConnectionToken(ConfigLoader.secretKey).execute()
            if (result.isSuccessful && result.body() != null) {
                result.body()!!.data as String
            } else {
                throw ConnectionTokenException("Creating connection token failed")
            }
        } catch (e: IOException) {
            throw ConnectionTokenException("Creating connection token failed", e)
        }
    }

    @Throws(IOException::class)
    fun createPaymentIntent(qrCode: String): Map<String, String>? {
        val result = service.createPaymentIntent(ConfigLoader.secretKey, qrCode).execute()
        Log.d("terminal", Gson().toJson(result.body()))
        if (result.body()?.code == 200) {
            val data = result.body()?.data
            if (data is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                return data as Map<String, String>
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun lendPowerStripeTerminal(body: Map<String, String>): String? {
        val result = service.lendPowerStripeTerminal(body).execute()
        return if (result.body()?.code == 200) {
            result.body()?.data as? String
        } else null
    }

    @Throws(IOException::class)
    fun getPreAmount(): Map<String, String>? {
        val result = service.getPreAmount(ConfigLoader.secretKey).execute()
        return if (result.isSuccessful && result.body() != null) {
            @Suppress("UNCHECKED_CAST")
            result.body()?.data as? Map<String, String>
        } else null
    }

    @Throws(IOException::class)
    fun lendPowerNayax(body: Map<String, Any>): String? {
        val result = service.lendPowerNayax(body).execute()
        return if (result.body()?.code == 200) {
            result.body()?.data as? String
        } else null
    }

    @Throws(IOException::class)
    fun getVersion(qrCode: String): Map<String, Any>? {
        val result = service.getVersion(qrCode).execute()
        return if (result.isSuccessful && result.body() != null) {
            @Suppress("UNCHECKED_CAST")
            result.body()?.data as? Map<String, Any>
        } else null
    }

    @Throws(IOException::class)
    fun getBrightnessConfig(qrCode: String): Map<String, Any>? {
        val result = service.getBrightnessConfig(qrCode).execute()
        return if (result.isSuccessful && result.body() != null) {
            @Suppress("UNCHECKED_CAST")
            result.body()?.data as? Map<String, Any>
        } else null
    }

    @Throws(IOException::class)
    fun getQrCode(fno: String): String? {
        android.util.Log.d("MyApiClient", "=== 开始调用getQrCode API ===")
        android.util.Log.d("MyApiClient", "请求参数 fno: $fno")

        try {
            val result = service.getQrCode(fno).execute()
            android.util.Log.d("MyApiClient", "API调用完成，HTTP状态码: ${result.code()}")
            android.util.Log.d("MyApiClient", "响应体: ${result.body()}")

            if (result.body()?.code == 200) {
                @Suppress("UNCHECKED_CAST")
                val data = result.body()?.data as? Map<String, Any>
                val qrCode = data?.get("qrCode") as? String
                android.util.Log.d("MyApiClient", "成功获取QR码: $qrCode")
                return qrCode
            } else {
                android.util.Log.w("MyApiClient", "API返回错误，code: ${result.body()?.code}, message: ${result.body()?.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MyApiClient", "调用getQrCode API时发生异常", e)
            throw e
        }

        android.util.Log.w("MyApiClient", "getQrCode返回null")
        return null
    }

    @Throws(IOException::class)
    fun getAD(qrCode: String, timestamp: Long, ip: String, sign: String): MyResponse? {
        val result = service.getAD(qrCode, timestamp, ip, sign).execute()
        return result.body()
    }

    @Throws(IOException::class)
    fun getLocationId(qrCode: String): MyResponse? {
        val result = service.getLocationId(qrCode).execute()
        return result.body()
    }

    @Throws(IOException::class)
    fun getOrderInfoByPowerBankId(powerBankId: String): MyResponse? {
        val result = service.getOrderInfoByPowerBankId(powerBankId).execute()
        return result.body()
    }
}
