package com.stwpower.powertap.managers

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences 工具类
 * 用于管理应用的配置信息
 */
object PreferenceManager {
    
    private const val PREF_NAME = "powertap_prefs"
    private const val KEY_LOCATION_ID = "locationId"
    private const val KEY_SNO = "SNO"
    private const val KEY_QR_CODE = "QR_CODE"
    
    private lateinit var preferences: SharedPreferences
    
    fun init(context: Context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 获取位置ID
     */
    fun getLocationId(): String? {
        return preferences.getString(KEY_LOCATION_ID, null)
    }
    
    /**
     * 设置位置ID
     */
    fun setLocationId(locationId: String) {
        preferences.edit().putString(KEY_LOCATION_ID, locationId).apply()
    }
    
    /**
     * 获取设备序列号
     */
    fun getDeviceSno(): String? {
        return preferences.getString(KEY_SNO, null)
    }
    
    /**
     * 设置设备序列号
     */
    fun setDeviceSno(sno: String) {
        preferences.edit().putString(KEY_SNO, sno).apply()
    }
    
    /**
     * 获取二维码
     */
    fun getQrCode(): String? {
        return preferences.getString(KEY_QR_CODE, null)
    }
    
    /**
     * 设置二维码
     */
    fun setQrCode(qrCode: String) {
        preferences.edit().putString(KEY_QR_CODE, qrCode).apply()
    }
}
