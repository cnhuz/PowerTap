package com.stwpower.powertap

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class ConfigLoader(private val context: Context) {

    companion object {
        private const val TAG = "ConfigLoader"
        private const val CONFIG_FILE = "/sdcard/Player/config.txt"

        // 静态配置变量 - 可以直接引用
        @JvmStatic
        var apiUrl: String = ""

        @JvmStatic
        var qrCodeUrl: String = ""

        @JvmStatic
        var secretKey: String = ""

        @JvmStatic
        var imei: String = ""

        @JvmStatic
        var enableDebug: Boolean = false
    }
    
    /**
     * 从/sdcard/Player/config.txt读取配置文件
     */
    fun loadConfig() {
        try {
            val configFile = java.io.File(CONFIG_FILE)
            if (configFile.exists() && configFile.canRead()) {
                configFile.readLines().forEach { line ->
                    parseLine(line.trim())
                }
                Log.d(TAG, "Config loaded successfully from $CONFIG_FILE")
            } else {
                Log.w(TAG, "Config file not found or not readable: $CONFIG_FILE")
                setDefaultValues()
            }

            // 读取IMEI信息
            loadImeiFromFile()

            logConfig()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config file: $CONFIG_FILE", e)
            setDefaultValues()
            // 即使配置文件读取失败，仍然尝试读取IMEI
            loadImeiFromFile()
            logConfig()
        }
    }
    
    /**
     * 解析配置行
     */
    private fun parseLine(line: String) {
        if (line.isEmpty() || line.startsWith("#")) {
            return // 跳过空行和注释
        }

        val parts = line.split(":", limit = 2)
        if (parts.size != 2) {
            return
        }

        val key = parts[0].trim()
        val value = parts[1].trim()
        
        when (key) {
            "baseUrl" -> Companion.apiUrl = value
            "secretKey" -> Companion.secretKey = value
            "qrCodeUrl" -> Companion.qrCodeUrl = value
            else -> Log.w(TAG, "Unknown config key: $key")
        }
    }
    
    /**
     * 从/sdcard/devinfo.txt读取IMEI信息
     */
    private fun loadImeiFromFile() {
        val devinfoFile = "/sdcard/devinfo.txt"
        try {
            val file = java.io.File(devinfoFile)
            if (file.exists() && file.canRead()) {
                val imeiValue = file.readText().trim()
                if (imeiValue.isNotEmpty()) {
                    Companion.imei = imeiValue
                    Log.d(TAG, "IMEI loaded from $devinfoFile: $imeiValue")
                } else {
                    Log.w(TAG, "IMEI file is empty: $devinfoFile")
                    Companion.imei = "123456"
                }
            } else {
                Log.w(TAG, "IMEI file not found or not readable: $devinfoFile")
                Companion.imei = "123456"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read IMEI from $devinfoFile", e)
            Companion.imei = "123456"
        }
    }

    /**
     * 设置默认值
     */
    private fun setDefaultValues() {
        Companion.apiUrl = "https://powerweb-stw.stwpower.com/power_bank"
        Companion.qrCodeUrl = "https://powerweb-stw.stwpower.com/appWeb/store?id="
        Companion.secretKey = "q6b56jCopc7UW91eMON0wbAEeZdsd96x"
        Companion.imei = "123456"
        Companion.enableDebug = false
        Log.d(TAG, "Using default config values")
    }
    
    /**
     * 打印当前配置
     */
    private fun logConfig() {
        Log.d(TAG, "Current config:")
        Log.d(TAG, "  api_url = $apiUrl")
        Log.d(TAG, "  qr_code_url = $qrCodeUrl")
        Log.d(TAG, "  secret_key = $secretKey")
        Log.d(TAG, "  imei = $imei")
        Log.d(TAG, "  enable_debug = $enableDebug")
    }
}
