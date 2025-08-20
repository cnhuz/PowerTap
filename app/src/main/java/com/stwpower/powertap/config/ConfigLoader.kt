package com.stwpower.powertap.config

import android.content.Context
import android.util.Log
import com.stwpower.powertap.utils.MyLog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConfigLoader(private val context: Context) {

    companion object {
        private const val CONFIG_FILE = "/sdcard/Player/config.txt"
        private const val DEVINFO_FILE = "/sdcard/devinfo.txt"

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
        
        @JvmStatic
        var currency: String = "$" // 默认货币符号
        
        @JvmStatic
        var brandName: String = "STW" // 默认品牌名称
        
        // 配置加载状态
        @JvmStatic
        var isConfigLoaded = false
        
        @JvmStatic
        var configLoadError: String? = null
    }
    
    /**
     * 从/sdcard/Player/config.txt读取配置文件（同步方式）
     */
    fun loadConfig() {
        MyLog.d("开始加载配置文件...")
        try {
            val configFile = File(CONFIG_FILE)
            MyLog.d("检查配置文件是否存在: ${configFile.exists()}, 可读: ${configFile.canRead()}")
            if (configFile.exists() && configFile.canRead()) {
                val lines = configFile.readLines()
                MyLog.d("读取到配置文件内容，共 ${lines.size} 行")
                lines.forEach { line ->
                    parseLine(line.trim())
                }
                MyLog.d("Config loaded successfully from $CONFIG_FILE")
                isConfigLoaded = true
                configLoadError = null
            } else {
                MyLog.w("Config file not found or not readable: $CONFIG_FILE")
                setDefaultValues()
                isConfigLoaded = true
                configLoadError = "Config file not found"
            }

            // 读取IMEI信息
            loadImeiFromFile()

            logConfig()

        } catch (e: Exception) {
            MyLog.e("Failed to load config file: $CONFIG_FILE", e)
            setDefaultValues()
            // 即使配置文件读取失败，仍然尝试读取IMEI
            loadImeiFromFile()
            logConfig()
            isConfigLoaded = true
            configLoadError = e.message
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
            "baseUrl" -> apiUrl = value
            "secretKey" -> secretKey = value
            "qrCodeUrl" -> qrCodeUrl = value
            "currency" -> currency = value // 添加currency字段支持
            "brandName" -> brandName = value // 添加brandName字段支持
            "enableDebug" -> enableDebug = value.equals("true", ignoreCase = true)
            else -> MyLog.w("Unknown config key: $key")
        }
    }
    
    /**
     * 从/sdcard/devinfo.txt读取IMEI信息
     */
    private fun loadImeiFromFile() {
        try {
            val file = File(DEVINFO_FILE)
            if (file.exists() && file.canRead()) {
                val imeiValue = file.readText().trim()
                if (imeiValue.isNotEmpty()) {
                    imei = imeiValue
                    MyLog.d("IMEI loaded from $DEVINFO_FILE: $imeiValue")
                } else {
                    MyLog.w("IMEI file is empty: $DEVINFO_FILE")
                    imei = "123456"
                }
            } else {
                MyLog.w("IMEI file not found or not readable: $DEVINFO_FILE")
                imei = "123456"
            }
        } catch (e: Exception) {
            MyLog.e("Failed to read IMEI from $DEVINFO_FILE", e)
            imei = "123456"
        }
    }

    /**
     * 设置默认值
     */
    private fun setDefaultValues() {
        MyLog.d("Setting default config values...")
        apiUrl = "https://powerweb-stw.stwpower.com/power_bank"
        qrCodeUrl = "https://powerweb-stw.stwpower.com/appWeb/store?id="
        secretKey = "q6b56jCopc7UW91eMON0wbAEeZdsd96x"
        imei = "123456"
        enableDebug = false
        currency = "$" // 默认货币符号
        brandName = "STW" // 默认品牌名称
        MyLog.d("Using default config values")
    }
    
    /**
     * 打印当前配置
     */
    private fun logConfig() {
        MyLog.d("Current config:")
        MyLog.d("  api_url = $apiUrl")
        MyLog.d("  qr_code_url = $qrCodeUrl")
        MyLog.d("  secret_key = $secretKey")
        MyLog.d("  imei = $imei")
        MyLog.d("  enable_debug = $enableDebug")
        MyLog.d("  currency = $currency") // 添加currency日志
        MyLog.d("  brand_name = $brandName") // 添加brandName日志
    }
}
