package com.stwpower.powertap.data.api

import com.stwpower.powertap.domain.MyResponse

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MyApiService {

    /** 获取 ConnectionToken */
    @GET("pos/stripe/create_connection_token")
    fun getConnectionToken(@Query("key") key: String): Call<MyResponse>

    /** 获取 ClientSecret（方式一） */
    @POST("api/bankcard/stripe/createPaymentIntent4Terminal/{secretKey}")
    fun createPaymentIntent(@Path("secretKey") secretKey: String): Call<MyResponse>

    /** 获取 ClientSecret（方式二） */
    @POST("api/bankcard/stripe/createPaymentIntent4Terminal")
    fun createPaymentIntent(
        @Query("secretKey") secretKey: String,
        @Query("qrCode") qrCode: String
    ): Call<MyResponse>

    /** 获取设备绑定的 Stripe terminal 位置ID */
    @GET("pos/power/get_location_id")
    fun getLocationId(@Query("qrCode") qrCode: String): Call<MyResponse>

    /** 租借充电宝（Stripe Terminal） */
    @POST("pos/power/lend_power_stripe_terminal")
    fun lendPowerStripeTerminal(@Body body: Map<String, String>): Call<MyResponse>

    /** 获取预授权金额 */
    @GET("pos/power/get_pre_amount")
    fun getPreAmount(@Query("secretKey") secretKey: String): Call<MyResponse>

    /** 租借充电宝（Nayax） */
    @POST("pos/power/lend_power_nayax")
    fun lendPowerNayax(@Body body: Map<String, Any>): Call<MyResponse>

    /** 获取广告版本号 */
    @GET("cabinet/advertising/getVersion")
    fun getVersion(@Query("qrCode") qrCode: String): Call<MyResponse>

    /** 获取亮度配置 */
    @GET("cabinet/advertising/getBrightnessConfig")
    fun getBrightnessConfig(@Query("qrCode") qrCode: String): Call<MyResponse>

    /** 请求二维码 */
    @GET("cabinet/advertising/cabinet_advertising")
    fun getQrCode(@Query("fno") fno: String): Call<MyResponse>

    /** 获取广告内容 */
    @GET("cabinet/advertising/get")
    fun getAD(
        @Query("qrCode") qrCode: String,
        @Query("timestamp") timestamp: Long,
        @Query("ip") ip: String,
        @Query("sign") sign: String
    ): Call<MyResponse>

    /** 根据充电宝ID获取订单信息 */
    @GET("api/borrow/getOrderInfoByPowerBankId")
    fun getOrderInfoByPowerBankId(@Query("powerBankId") powerBankId: String): Call<MyResponse>

    /** 查询收费规则 */
    @GET("/getChargeRuleByQrCode/{qrCode}")
    fun getChargeRuleByQrCode(@Path("qrCode") qrCode: String): Call<MyResponse>
}
