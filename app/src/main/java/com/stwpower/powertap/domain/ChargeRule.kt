package com.stwpower.powertap.domain

/**
 * 收费规则数据类
 */
data class ChargeRule(
    val maxPerMoney: Double,     // 每天最大金额
    val oneMoneyUnit: Double,    // 单位时间收费金额
    val hourUnit: Int,          // 单位时间（小时）
    val reportLoss: Double      // 报失金额
)