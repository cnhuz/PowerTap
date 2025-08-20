package com.stwpower.powertap.utils

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*

/**
 * 优化的二维码生成器
 * 集成缓存机制，提升性能
 */
object OptimizedQRGenerator {

    /**
     * 异步生成二维码（带缓存）
     */
    suspend fun generateQRCodeAsync(
        content: String,
        size: Int = 400,
        style: String = "WHITE_BORDERED"
    ): Bitmap = withContext(Dispatchers.IO) {
        
        MyLog.d("开始生成二维码: $content")
        
        // 首先检查缓存
        val cachedBitmap = QRCodeCache.getCachedQRCode(content, size, style)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            MyLog.d("使用缓存的二维码: $content")
            return@withContext cachedBitmap
        }
        
        // 缓存未命中，生成新的二维码
        MyLog.d("生成新的二维码: $content")
        val startTime = System.currentTimeMillis()
        
        val bitmap = when (style) {
            "WHITE_BORDERED" -> BeautifulQRGenerator.generateBeautifulQR(
                content = content,
                size = size,
                style = BeautifulQRGenerator.Styles.WHITE_BORDERED
            )
            "SIMPLE" -> BeautifulQRGenerator.generateBeautifulQR(
                content = content,
                size = size,
                style = BeautifulQRGenerator.Styles.SIMPLE
            )
            else -> BeautifulQRGenerator.generateBeautifulQR(
                content = content,
                size = size,
                style = BeautifulQRGenerator.Styles.WHITE_BORDERED
            )
        }
        
        val endTime = System.currentTimeMillis()
        MyLog.d("二维码生成完成，耗时: ${endTime - startTime}ms")
        
        // 缓存生成的二维码
        QRCodeCache.cacheQRCode(content, size, style, bitmap)
        
        bitmap
    }
    
    /**
     * 同步生成二维码（带缓存）
     */
    fun generateQRCode(
        content: String,
        size: Int = 400,
        style: String = "WHITE_BORDERED"
    ): Bitmap {
        
        MyLog.d("同步生成二维码: $content")
        
        // 首先检查缓存
        val cachedBitmap = QRCodeCache.getCachedQRCode(content, size, style)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            MyLog.d("使用缓存的二维码: $content")
            return cachedBitmap
        }
        
        // 缓存未命中，生成新的二维码
        MyLog.d("生成新的二维码: $content")
        val startTime = System.currentTimeMillis()
        
        val bitmap = when (style) {
            "WHITE_BORDERED" -> BeautifulQRGenerator.generateBeautifulQR(
                content = content,
                size = size,
                style = BeautifulQRGenerator.Styles.WHITE_BORDERED
            )
            "SIMPLE" -> BeautifulQRGenerator.generateBeautifulQR(
                content = content,
                size = size,
                style = BeautifulQRGenerator.Styles.SIMPLE
            )
            else -> BeautifulQRGenerator.generateBeautifulQR(
                content = content,
                size = size,
                style = BeautifulQRGenerator.Styles.WHITE_BORDERED
            )
        }
        
        val endTime = System.currentTimeMillis()
        MyLog.d("二维码生成完成，耗时: ${endTime - startTime}ms")
        
        // 缓存生成的二维码
        QRCodeCache.cacheQRCode(content, size, style, bitmap)
        
        return bitmap
    }
    
    /**
     * 预生成二维码（后台任务）
     */
    fun preGenerateQRCode(
        content: String,
        size: Int = 400,
        style: String = "WHITE_BORDERED"
    ) {
        // 如果已经缓存，则跳过
        if (QRCodeCache.isQRCodeCached(content, size, style)) {
            MyLog.d("二维码已缓存，跳过预生成: $content")
            return
        }
        
        // 在后台线程预生成
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MyLog.d("开始预生成二维码: $content")
                generateQRCodeAsync(content, size, style)
                MyLog.d("预生成二维码完成: $content")
            } catch (e: Exception) {
                MyLog.e("预生成二维码失败: $content", e)
            }
        }
    }
    
    /**
     * 批量预生成二维码
     */
    fun preGenerateQRCodes(
        contents: List<String>,
        size: Int = 400,
        style: String = "WHITE_BORDERED"
    ) {
        MyLog.d("开始批量预生成 ${contents.size} 个二维码")
        
        CoroutineScope(Dispatchers.IO).launch {
            contents.forEach { content ->
                try {
                    if (!QRCodeCache.isQRCodeCached(content, size, style)) {
                        generateQRCodeAsync(content, size, style)
                        // 添加小延迟，避免CPU过载
                        delay(50)
                    }
                } catch (e: Exception) {
                    MyLog.e("批量预生成二维码失败: $content", e)
                }
            }
            MyLog.d("批量预生成二维码完成")
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        QRCodeCache.cleanupExpiredCache()
        MyLog.d("二维码生成器资源清理完成")
    }
}
