package com.stwpower.powertap.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.stwpower.powertap.domain.ChargeRule

/**
 * 收费规则缓存管理器
 */
class ChargeRuleCacheManager private constructor(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("charge_rule_cache", Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = sharedPreferences.edit()
    
    companion object {
        private const val TAG = "ChargeRuleCacheManager"
        private const val KEY_MAX_PER_MONEY = "max_per_money"
        private const val KEY_ONE_MONEY_UNIT = "one_money_unit"
        private const val KEY_HOUR_UNIT = "hour_unit"
        private const val KEY_REPORT_LOSS = "report_loss"
        private const val KEY_CACHE_TIME = "cache_time"
        private const val CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000L // 24小时缓存有效期
        
        @Volatile
        private var instance: ChargeRuleCacheManager? = null
        
        fun getInstance(context: Context): ChargeRuleCacheManager {
            return instance ?: synchronized(this) {
                instance ?: ChargeRuleCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 保存收费规则到缓存
     */
    fun saveChargeRule(chargeRule: ChargeRule) {
        try {
            editor.putLong(KEY_CACHE_TIME, System.currentTimeMillis())
            editor.putFloat(KEY_MAX_PER_MONEY, chargeRule.maxPerMoney.toFloat())
            editor.putFloat(KEY_ONE_MONEY_UNIT, chargeRule.oneMoneyUnit.toFloat())
            editor.putInt(KEY_HOUR_UNIT, chargeRule.hourUnit)
            editor.putFloat(KEY_REPORT_LOSS, chargeRule.reportLoss.toFloat())
            editor.apply()
            Log.d(TAG, "收费规则已保存到缓存")
        } catch (e: Exception) {
            Log.e(TAG, "保存收费规则到缓存时出错", e)
        }
    }
    
    /**
     * 从缓存获取收费规则
     */
    fun getChargeRuleFromCache(): ChargeRule? {
        return try {
            // 检查缓存是否过期
            val cacheTime = sharedPreferences.getLong(KEY_CACHE_TIME, 0L)
            if (cacheTime == 0L || System.currentTimeMillis() - cacheTime > CACHE_EXPIRY_TIME) {
                Log.d(TAG, "缓存已过期或不存在")
                return null
            }
            
            val maxPerMoney = sharedPreferences.getFloat(KEY_MAX_PER_MONEY, 0f).toDouble()
            val oneMoneyUnit = sharedPreferences.getFloat(KEY_ONE_MONEY_UNIT, 0f).toDouble()
            val hourUnit = sharedPreferences.getInt(KEY_HOUR_UNIT, 1)
            val reportLoss = sharedPreferences.getFloat(KEY_REPORT_LOSS, 0f).toDouble()
            
            if (maxPerMoney == 0.0 && oneMoneyUnit == 0.0 && reportLoss == 0.0) {
                Log.d(TAG, "缓存中没有有效的收费规则数据")
                return null
            }
            
            val chargeRule = ChargeRule(maxPerMoney, oneMoneyUnit, hourUnit, reportLoss)
            Log.d(TAG, "从缓存获取到收费规则: $chargeRule")
            chargeRule
        } catch (e: Exception) {
            Log.e(TAG, "从缓存获取收费规则时出错", e)
            null
        }
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        try {
            editor.clear().apply()
            Log.d(TAG, "收费规则缓存已清除")
        } catch (e: Exception) {
            Log.e(TAG, "清除收费规则缓存时出错", e)
        }
    }
    
    /**
     * 检查是否有有效的缓存数据
     */
    fun hasValidCache(): Boolean {
        return getChargeRuleFromCache() != null
    }
}