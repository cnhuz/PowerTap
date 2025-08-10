package com.stwpower.powertap

import android.app.Application
import android.util.Log
import com.stwpower.powertap.config.ConfigLoader

class PowerTapApplication : Application() {

    companion object {
        private const val TAG = "PowerTapApplication"

        @Volatile
        private var INSTANCE: PowerTapApplication? = null

        fun getInstance(): PowerTapApplication {
            return INSTANCE ?: throw IllegalStateException("Application not initialized")
        }
    }

    // 配置加载器
    lateinit var configLoader: ConfigLoader
        private set

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        Log.d(TAG, "PowerTap Application starting...")

        // 加载配置
        loadConfig()

        Log.d(TAG, "PowerTap Application initialized successfully")
    }

    private fun loadConfig() {
        configLoader = ConfigLoader(this)
        configLoader.loadConfig()
    }
}
