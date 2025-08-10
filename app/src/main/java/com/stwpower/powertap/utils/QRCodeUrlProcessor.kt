package com.stwpower.powertap.utils

import android.util.Log

/**
 * 二维码URL处理工具
 * 用于处理qrCodeUrl中的格式化字符串
 */
object QRCodeUrlProcessor {
    
    private const val TAG = "powertap"
    
    /**
     * 处理qrCodeUrl，去除末尾的%s等格式化字符
     */
    fun processQrCodeUrl(rawUrl: String): String {
        if (rawUrl.isEmpty()) {
            Log.w(TAG, "qrCodeUrl为空")
            return rawUrl
        }
        
        var processedUrl = rawUrl
        
        // 去除末尾的%s
        if (processedUrl.endsWith("%s")) {
            processedUrl = processedUrl.removeSuffix("%s")
            Log.d(TAG, "去除末尾的%s: $rawUrl -> $processedUrl")
        }
        
        // 可以在这里添加其他格式化字符的处理
        // 例如：%d, %f 等
        
        return processedUrl
    }
    
    /**
     * 生成完整的二维码内容
     */
    fun generateQRCodeContent(rawQrCodeUrl: String, qrCode: String): String {
        val processedUrl = processQrCodeUrl(rawQrCodeUrl)
        val fullContent = processedUrl + qrCode
        
        Log.d(TAG, "生成二维码内容:")
        Log.d(TAG, "  原始URL: $rawQrCodeUrl")
        Log.d(TAG, "  处理后URL: $processedUrl")
        Log.d(TAG, "  QR码: $qrCode")
        Log.d(TAG, "  完整内容: $fullContent")
        
        return fullContent
    }
    
    /**
     * 验证qrCodeUrl格式
     */
    fun validateQrCodeUrl(url: String): Boolean {
        if (url.isEmpty()) {
            Log.w(TAG, "qrCodeUrl为空")
            return false
        }
        
        // 检查是否为有效的URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.w(TAG, "qrCodeUrl格式无效，不是有效的URL: $url")
            return false
        }
        
        return true
    }
    
    /**
     * 获取处理后的qrCodeUrl用于日志显示
     */
    fun getProcessedUrlForLogging(rawUrl: String): String {
        val processed = processQrCodeUrl(rawUrl)
        return if (rawUrl != processed) {
            "$rawUrl -> $processed"
        } else {
            processed
        }
    }
}
