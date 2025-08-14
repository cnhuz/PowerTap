package com.stwpower.powertap.utils

import android.graphics.Bitmap
import android.util.Log

/**
 * 二维码缓存管理器
 * 用于在不同Activity间共享预生成的二维码和相关参数
 */
object QRCodeCacheManager {
    private const val TAG = "powertap"
    
    // 缓存的二维码bitmap
    private var cachedQRCodeBitmap: Bitmap? = null
    
    // 缓存的二维码内容信息
    private var cachedQRCodeUrl: String = ""
    private var cachedQRCode: String = ""
    private var cachedFullQRCodeContent: String = ""
    
    // 缓存时间戳
    private var cacheTimestamp: Long = 0
    
    // 缓存有效期（5分钟）
    private const val CACHE_EXPIRATION_TIME = 5 * 60 * 1000L
    
    /**
     * 设置缓存的二维码
     */
    fun setCachedQRCode(
        bitmap: Bitmap,
        qrCodeUrl: String,
        qrCode: String,
        fullQRCodeContent: String
    ) {
        // 释放旧的bitmap资源
        cachedQRCodeBitmap?.recycle()
        
        // 存储新的二维码信息
        cachedQRCodeBitmap = bitmap.config?.let { bitmap.copy(it, true) } // 创建副本以避免引用问题
        cachedQRCodeUrl = qrCodeUrl
        cachedQRCode = qrCode
        cachedFullQRCodeContent = fullQRCodeContent
        cacheTimestamp = System.currentTimeMillis()
        
        Log.d(TAG, "二维码已缓存，时间戳: $cacheTimestamp")
    }
    
    /**
     * 获取缓存的二维码bitmap
     */
    fun getCachedQRCodeBitmap(): Bitmap? {
        // 检查缓存是否过期
        if (isCacheExpired()) {
            clearCache()
            return null
        }
        
        return cachedQRCodeBitmap?.let { 
            if (it.isRecycled) {
                clearCache()
                null
            } else {
                it.config?.let { it1 -> it.copy(it1, true) } // 返回副本以避免引用问题
            }
        }
    }
    
    /**
     * 获取缓存的二维码URL
     */
    fun getCachedQRCodeUrl(): String {
        return if (isCacheExpired()) {
            ""
        } else {
            cachedQRCodeUrl
        }
    }
    
    /**
     * 获取缓存的二维码
     */
    fun getCachedQRCode(): String {
        return if (isCacheExpired()) {
            ""
        } else {
            cachedQRCode
        }
    }
    
    /**
     * 获取缓存的完整二维码内容
     */
    fun getCachedFullQRCodeContent(): String {
        return if (isCacheExpired()) {
            ""
        } else {
            cachedFullQRCodeContent
        }
    }
    
    /**
     * 检查缓存是否匹配
     */
    fun isCacheMatch(qrCodeUrl: String, qrCode: String, fullQRCodeContent: String): Boolean {
        if (isCacheExpired()) {
            return false
        }
        
        return cachedQRCodeUrl == qrCodeUrl &&
               cachedQRCode == qrCode &&
               cachedFullQRCodeContent == fullQRCodeContent
    }
    
    /**
     * 检查缓存是否过期
     */
    private fun isCacheExpired(): Boolean {
        return System.currentTimeMillis() - cacheTimestamp > CACHE_EXPIRATION_TIME
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        cachedQRCodeBitmap?.recycle()
        cachedQRCodeBitmap = null
        cachedQRCodeUrl = ""
        cachedQRCode = ""
        cachedFullQRCodeContent = ""
        cacheTimestamp = 0
        Log.d(TAG, "二维码缓存已清除")
    }
    
    /**
     * 检查是否有有效的缓存
     */
    fun hasValidCache(): Boolean {
        return !isCacheExpired() && 
               cachedQRCodeBitmap != null && 
               !cachedQRCodeBitmap!!.isRecycled &&
               cachedQRCodeUrl.isNotEmpty() &&
               cachedQRCode.isNotEmpty() &&
               cachedFullQRCodeContent.isNotEmpty()
    }
    
    /**
     * 获取当前缓存的状态，用于比较
     */
    fun getCurrentCacheState(): CacheState {
        return CacheState(
            qrCodeUrl = getCachedQRCodeUrl(),
            qrCode = getCachedQRCode(),
            fullQRCodeContent = getCachedFullQRCodeContent()
        )
    }
    
    /**
     * 缓存状态数据类
     */
    data class CacheState(
        val qrCodeUrl: String,
        val qrCode: String,
        val fullQRCodeContent: String
    )
}