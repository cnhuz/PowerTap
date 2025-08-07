package com.stwpower.powertap.data.api;

import com.stwpower.powertap.domain.MyResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface MyApiService {
    /**
     * 获取ConnectionToken
     */
    @GET("pos/stripe/create_connection_token")
    Call<MyResponse> getConnectionToken(@Query("key") String key);

    /**
     * 获取ClientSecret
     */
    @POST("api/bankcard/stripe/createPaymentIntent4Terminal/{secretKey}")
    Call<MyResponse> createPaymentIntent(@Path("secretKey") String secretKey);

    /**
     * 获取ClientSecret
     */
    @POST("api/bankcard/stripe/createPaymentIntent4Terminal")
    Call<MyResponse> createPaymentIntent(@Query("secretKey") String secretKey, @Query("qrCode") String qrCode);

    /**
     * 获取设备绑定的Stripe terminal位置ID
     * @param qrCode
     * @return
     */
    @GET("pos/power/get_location_id")
    Call<MyResponse> getLocationId(@Query("qrCode") String qrCode);

    /**
     * 租借充电宝
     */
    @POST("pos/power/lend_power_stripe_terminal")
    Call<MyResponse> lendPowerStripeTerminal(@Body Map<String,String> body);

    @GET("pos/power/get_pre_amount")
    Call<MyResponse> getPreAmount(@Query("secretKey") String secretKey);

    @POST("pos/power/lend_power_nayax")
    Call<MyResponse> lendPowerNayax(@Body Map<String,Object> body);

    @GET("cabinet/advertising/getVersion")
    Call<MyResponse> getVersion(@Query("qrCode") String qrCode);

    @GET("cabinet/advertising/getBrightnessConfig")
    Call<MyResponse> getBrightnessConfig(@Query("qrCode") String qrCode);

    /**
     * 请求二维码
     * @param fno
     * @return
     */
    @GET("cabinet/advertising/cabinet_advertising")
    Call<MyResponse> getQrCode(@Query("fno") String fno);

    /**
     * 获取广告
     * @param qrCode
     * @param timestamp
     * @param ip
     * @param sign
     * @return
     */
    @GET("cabinet/advertising/get")
    Call<MyResponse> getAD(@Query("qrCode") String qrCode, @Query("timestamp") Long timestamp, @Query("ip") String ip, @Query("sign") String sign);

    @GET("api/borrow/getOrderInfoByPowerBankId")
    Call<MyResponse> getOrderInfoByPowerBankId(@Query("powerBankId") String powerBankId);
}