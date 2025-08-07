package com.stwpower.powertap.domain

/**
 * 通用响应类
 */
data class MyResponse(
    val code: Int,
    val message: String,
    val data: Any?
)
