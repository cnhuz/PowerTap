package com.stwpower.powertap.utils

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import java.security.MessageDigest

/**
 * 二维码缓存管理器
 * 用于缓存生成的二维码，避免重复渲染，提升性能
 */
object QRCodeCache {
    
    private const val TAG = "QRCodeCache"
    
    // 缓存大小：最多缓存10个二维码
    private const val CACHE_SIZE = 10
    
    // LRU缓存，自动管理内存
    private val cache = LruCache<String, CachedQRCode>(CACHE_SIZE)
    
    /**
     * 缓存的二维码数据
     */
    data class CachedQRCode(
        val bitmap: Bitmap,
        val content: String,
        val size: Int,
        val style: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(content: String, size: Int, style: String): String {
        val input = "$content|$size|$style"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 获取缓存的二维码
     */
    fun getCachedQRCode(content: String, size: Int, style: String): Bitmap? {
        val key = generateCacheKey(content, size, style)
        val cached = cache.get(key)
        
        if (cached != null) {
            Log.d(TAG, "二维码缓存命中: $content")
            return cached.bitmap
        }
        
        Log.d(TAG, "二维码缓存未命中: $content")
        return null
    }
    
    /**
     * 缓存二维码
     */
    fun cacheQRCode(content: String, size: Int, style: String, bitmap: Bitmap) {
        val key = generateCacheKey(content, size, style)
        val cachedQRCode = CachedQRCode(
            bitmap = bitmap,
            content = content,
            size = size,
            style = style
        )
        
        cache.put(key, cachedQRCode)
        Log.d(TAG, "二维码已缓存: $content, 缓存大小: ${cache.size()}")
    }
    
    /**
     * 检查二维码是否已缓存
     */
    fun isQRCodeCached(content: String, size: Int, style: String): Boolean {
        val key = generateCacheKey(content, size, style)
        return cache.get(key) != null
    }
    
    /**
     * 清除特定二维码缓存
     */
    fun clearQRCode(content: String, size: Int, style: String) {
        val key = generateCacheKey(content, size, style)
        cache.remove(key)
        Log.d(TAG, "已清除二维码缓存: $content")
    }
    
    /**
     * 清除所有缓存
     */
    fun clearAll() {
        cache.evictAll()
        Log.d(TAG, "已清除所有二维码缓存")
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): String {
        return """
            二维码缓存统计:
            - 当前缓存数量: ${cache.size()}
            - 最大缓存数量: $CACHE_SIZE
            - 命中次数: ${cache.hitCount()}
            - 未命中次数: ${cache.missCount()}
            - 命中率: ${if (cache.hitCount() + cache.missCount() > 0) 
                String.format("%.2f%%", cache.hitCount() * 100.0 / (cache.hitCount() + cache.missCount())) 
                else "0.00%"}
        """.trimIndent()
    }
    
    /**
     * 获取缓存中的所有二维码信息
     */
    fun getCachedQRCodes(): List<CachedQRCode> {
        val result = mutableListOf<CachedQRCode>()
        val snapshot = cache.snapshot()
        
        for ((_, value) in snapshot) {
            result.add(value)
        }
        
        return result.sortedByDescending { it.timestamp }
    }
    
    /**
     * 清理过期缓存（超过1小时的缓存）
     */
    fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expireTime = 60 * 60 * 1000L // 1小时
        
        val snapshot = cache.snapshot()
        var removedCount = 0
        
        for ((key, value) in snapshot) {
            if (currentTime - value.timestamp > expireTime) {
                cache.remove(key)
                removedCount++
            }
        }
        
        if (removedCount > 0) {
            Log.d(TAG, "已清理 $removedCount 个过期的二维码缓存")
        }
    }
}
