package com.stwpower.powertap

import android.app.Application
import android.util.Log
import com.stwpower.powertap.config.ConfigLoader
import com.stwpower.powertap.utils.ChargeRuleManager
import com.stwpower.powertap.utils.MyLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PowerTapApplication : Application() {

    companion object {

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

        MyLog.d("PowerTap Application starting...")

        // 加载配置
        loadConfig()

        // 配置加载完成后再预加载收费规则
        preloadChargeRuleAfterConfig()

        MyLog.d("PowerTap Application initialized successfully")
    }

    private fun loadConfig() {
        MyLog.d("Creating new ConfigLoader instance...")
        configLoader = ConfigLoader(this)
        MyLog.d("Loading config...")
        configLoader.loadConfig()
        MyLog.d("Config loading completed")
    }

    /**
     * 配置加载完成后再预加载收费规则
     */
    private fun preloadChargeRuleAfterConfig() {
        // 确保配置已加载
        if (ConfigLoader.isConfigLoaded) {
            preloadChargeRule()
        } else {
            // 如果配置还未加载完成，等待一小段时间后重试
            try {
                Thread.sleep(100)
                preloadChargeRule()
            } catch (e: InterruptedException) {
                preloadChargeRule()
            }
        }
    }

    /**
     * 预加载收费规则
     */
    private fun preloadChargeRule() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                //TODO 加载之后，应该存起来了
                MyLog.d("开始预加载收费规则...")
                val chargeRule = ChargeRuleManager.getChargeRule(this@PowerTapApplication)
                if (chargeRule != null) {
                    MyLog.d("收费规则预加载成功: $chargeRule")
                } else {
                    MyLog.w("收费规则预加载失败或无数据")
                }
            } catch (e: Exception) {
                MyLog.e("预加载收费规则时出错", e)
            }
        }
    }
}
