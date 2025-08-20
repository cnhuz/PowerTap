package com.stwpower.powertap.utils

import android.util.Log

/**
 * 二维码URL处理工具
 * 用于处理qrCodeUrl中的格式化字符串
 */
object QRCodeUrlProcessor {
    /**
     * 处理qrCodeUrl，去除末尾的%s等格式化字符
     */
    fun processQrCodeUrl(rawUrl: String): String {
        if (rawUrl.isEmpty()) {
            MyLog.w("qrCodeUrl为空")
            return rawUrl
        }
        
        var processedUrl = rawUrl
        
        // 去除末尾的%s
        if (processedUrl.endsWith("%s")) {
            processedUrl = processedUrl.removeSuffix("%s")
            MyLog.d("去除末尾的%s: $rawUrl -> $processedUrl")
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
        
        MyLog.d("生成二维码内容:")
        MyLog.d("  原始URL: $rawQrCodeUrl")
        MyLog.d("  处理后URL: $processedUrl")
        MyLog.d("  QR码: $qrCode")
        MyLog.d("  完整内容: $fullContent")
        
        return fullContent
    }
    
    /**
     * 验证qrCodeUrl格式
     */
    fun validateQrCodeUrl(url: String): Boolean {
        if (url.isEmpty()) {
            MyLog.w("qrCodeUrl为空")
            return false
        }
        
        // 检查是否为有效的URL格式
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            MyLog.w("qrCodeUrl格式无效，不是有效的URL: $url")
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
