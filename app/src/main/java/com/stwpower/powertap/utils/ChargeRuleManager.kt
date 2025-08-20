package com.stwpower.powertap.utils

import android.util.Log
import com.stwpower.powertap.config.ConfigLoader
import com.stwpower.powertap.data.api.MyApiClient
import com.stwpower.powertap.domain.ChargeRule
import com.stwpower.powertap.managers.PreferenceManager
import com.stwpower.powertap.managers.ChargeRuleCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 收费规则工具类
 */
object ChargeRuleManager {

    /**
     * TODO 请求次数太多，没有使用缓存，需优化
     * 获取充电规则（优先从网络获取，失败时使用缓存）
     * @param context 上下文
     * @return ChargeRule对象，如果获取失败则返回null
     */
    suspend fun getChargeRule(context: android.content.Context): ChargeRule? = withContext(Dispatchers.IO) {
        return@withContext try {
            val qrCode = PreferenceManager.getQrCode()
            if (qrCode.isNullOrEmpty()) {
                MyLog.w("QR code is empty or null")
                return@withContext getChargeRuleFromCache(context)
            }
            
            val response = MyApiClient.getChargeRuleByQrCode(qrCode)
            if (response?.code == 200 && response.data != null) {
                // 解析data字段
                val dataJson = response.data as? Map<*, *>
                if (dataJson != null) {
                    val maxPerMoney = (dataJson["maxPerMoney"] as? Number)?.toDouble() ?: 0.0
                    val oneMoneyUnit = (dataJson["oneMoneyUnit"] as? Number)?.toDouble() ?: 0.0
                    val hourUnit = (dataJson["hourUnit"] as? Number)?.toInt() ?: 1
                    val reportLoss = (dataJson["reportLoss"] as? Number)?.toDouble() ?: 0.0
                    
                    val chargeRule = ChargeRule(maxPerMoney, oneMoneyUnit, hourUnit, reportLoss)
                    
                    // 保存到缓存
                    ChargeRuleCacheManager.getInstance(context).saveChargeRule(chargeRule)
                    
                    MyLog.d("从网络获取到收费规则并保存到缓存: $chargeRule")
                    return@withContext chargeRule
                } else {
                    MyLog.w("Failed to parse charge rule data")
                    return@withContext getChargeRuleFromCache(context)
                }
            } else {
                MyLog.w("Failed to get charge rule from network: ${response?.message}")
                return@withContext getChargeRuleFromCache(context)
            }
        } catch (e: Exception) {
            MyLog.e("Error getting charge rule from network", e)
            return@withContext getChargeRuleFromCache(context)
        }
    }
    
    /**
     * 从缓存获取收费规则
     */
    private fun getChargeRuleFromCache(context: android.content.Context): ChargeRule? {
        return try {
            val cachedRule = ChargeRuleCacheManager.getInstance(context).getChargeRuleFromCache()
            if (cachedRule != null) {
                MyLog.d("使用缓存的收费规则: $cachedRule")
                cachedRule
            } else {
                MyLog.w("没有缓存的收费规则可用")
                null
            }
        } catch (e: Exception) {
            MyLog.e("从缓存获取收费规则时出错", e)
            null
        }
    }
    
    /**
     * 格式化价格显示
     * @param amount 金额
     * @return 格式化后的字符串，例如 "5.00$"
     */
    fun formatPrice(amount: Double): String {
        val currencySymbol = ConfigLoader.currency
        return String.format("%.2f%s", amount, currencySymbol)
    }
    
    /**
     * 获取每小时价格文本
     * @param oneMoneyUnit 单位时间收费金额
     * @param hourUnit 单位时间（分钟）
     * @return 例如 "/30 Min" 或 "/1 Hour"
     */
    fun getPerHourText(oneMoneyUnit: Double, hourUnit: Int): String {
        return if (hourUnit == 60) {
            "/1 Hour"
        } else {
            "/${hourUnit} Min"
        }
    }
    
    /**
     * 获取每天价格文本
     * @param maxPerMoney 每天最大金额
     * @return 例如 "/per_1_day"
     */
    fun getPerDayText(maxPerMoney: Double): String {
        return "/1 Day"
    }
}