package com.stwpower.powertap.data.api;

import android.util.Log;

import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.stripe.stripeterminal.external.models.ConnectionTokenException;
import com.stwpower.powertap.domain.MyResponse;
import com.stwpower.powertap.utils.LoggingInterceptor;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class MyApiClient {
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(new LoggingInterceptor())
            .build();
    private static final Retrofit mRetrofit = new Retrofit.Builder()
            .baseUrl(MyApp.baseUrl + "/")
//            .baseUrl("http://192.168.5.142:8082/power_bank/")
            .client(getUnsafeOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    private static final MyApiService mService = mRetrofit.create(MyApiService.class);

    public static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .addInterceptor(new LoggingInterceptor());
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @NotNull
    public static String createConnectionToken() throws ConnectionTokenException {
        try {
            final Response<MyResponse> result = mService.getConnectionToken(MyApp.secretKey).execute();
            if (result.isSuccessful() && result.body() != null) {
                return (String)result.body().getData();
            } else {
                throw new ConnectionTokenException("Creating connection token failed");
            }
        } catch (IOException e) {
            throw new ConnectionTokenException("Creating connection token failed", e);
        }
    }

    public static Map<String,String> createPaymentIntent(String qrCode) throws IOException{
        Response<MyResponse> result = mService.createPaymentIntent(MyApp.secretKey,qrCode).execute();
        Log.d(MyApp.TERMINAL_TAG,new Gson().toJson(result.body()));
        if(result.body() != null && result.body().getCode() == 200){
            Object data = result.body().getData();
            if(data instanceof Map){
                Map<String,String> map = (Map<String,String>) data;
                return map;
            }
        }
        return null;
    }

    public static String lendPowerStripeTerminal(@NotNull Map<String,String> body) throws IOException {
        Response<MyResponse> result = mService.lendPowerStripeTerminal(body).execute();
        if(result.body() != null && result.body().getCode() == 200){
            return (String)result.body().getData();
        }
        return null;
    }

    public static Map<String,String> getPreAmount() throws IOException {
        final Response<MyResponse> result = mService.getPreAmount(MyApp.secretKey).execute();
        if (result.isSuccessful() && result.body() != null) {
            Map<String,String> data = (Map)result.body().getData();
            return data;
        }
        return null;
    }

    public static String lendPowerNayax(@NotNull Map<String,Object> body) throws IOException {
        Response<MyResponse> result = mService.lendPowerNayax(body).execute();
        if(result.body() != null && result.body().getCode() == 200){
            return (String)result.body().getData();
        }
        return null;
    }

    public static Map<String,Object> getVersion(String qrCode) throws IOException {
        final Response<MyResponse> result = mService.getVersion(qrCode).execute();
        if (result.isSuccessful() && result.body() != null) {
            Map<String,Object> data = (Map)result.body().getData();
            return data;
        }
        return null;
    }

    public static Map<String,Object> getBrightnessConfig(String qrCode) throws IOException {
        final Response<MyResponse> result = mService.getBrightnessConfig(qrCode).execute();
        if (result.isSuccessful() && result.body() != null) {
            Map<String,Object> data = (Map)result.body().getData();
            return data;
        }
        return null;
    }

    public static String getQrCode(@NotNull String fno) throws IOException {
        Response<MyResponse> result = mService.getQrCode(fno).execute();
        if(result.body() != null && result.body().getCode() == 200){
            Map<String,Object> data = (Map)result.body().getData();
            return (String)data.get("qrCode");
        }
        return null;
    }

    public static MyResponse getAD(@NotNull("qrCode") String qrCode, @NotNull("timestamp") Long timestamp, @NotNull("ip") String ip, @NotNull("sign") String sign) throws IOException {
        Response<MyResponse> result = mService.getAD(qrCode,timestamp,ip,sign).execute();
        return result.body();

    }

    public static MyResponse getLocationId(@NotNull String qrCode) throws IOException {
        Response<MyResponse> result = mService.getLocationId(qrCode).execute();
        return result.body();
    }

    public static MyResponse getOrderInfoByPowerBankId(@NotNull String powerBankId) throws IOException {
        Response<MyResponse> result = mService.getOrderInfoByPowerBankId(powerBankId).execute();
        return result.body();
    }
}