package com.stwpower.powertap.utils

import android.graphics.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * 美化二维码生成器
 * 基于ZXing实现，添加渐变色、圆角、边框等美化效果
 */
object BeautifulQRGenerator {

    /**
     * 生成美化的二维码
     * @param content 二维码内容
     * @param size 二维码尺寸
     * @param style 样式配置
     */
    fun generateBeautifulQR(
        content: String,
        size: Int,
        style: QRStyle = QRStyle()
    ): Bitmap {
        // 1. 生成基础二维码
        val writer = QRCodeWriter()
        val hints = hashMapOf<EncodeHintType, Any>().apply {
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H) // 高容错率，支持Logo
            put(EncodeHintType.MARGIN, 1) // 减少边距
        }
        
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        
        // 2. 创建画布
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 3. 如果有边框，先绘制边框内的白色背景
        if (style.borderWidth > 0) {
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            val borderInset = style.borderWidth
            val backgroundRect = RectF(
                borderInset, borderInset,
                width - borderInset, height - borderInset
            )
            canvas.drawRoundRect(backgroundRect, style.borderRadius - borderInset/2, style.borderRadius - borderInset/2, backgroundPaint)
        } else if (style.backgroundColor != Color.TRANSPARENT) {
            // 如果没有边框但有背景色，绘制整体背景
            canvas.drawColor(style.backgroundColor)
        }
        // 如果背景是透明且没有边框，则不绘制任何背景

        // 4. 创建渐变画笔
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            style.foregroundStartColor, style.foregroundEndColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        
        // 5. 绘制二维码点
        val dotSize = style.dotSize
        val cornerRadius = style.cornerRadius
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (bitMatrix[x, y]) {
                    val left = x.toFloat()
                    val top = y.toFloat()
                    val right = left + dotSize
                    val bottom = top + dotSize
                    
                    if (style.roundedDots) {
                        // 绘制圆角矩形点
                        val rect = RectF(left, top, right, bottom)
                        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
                    } else {
                        // 绘制普通矩形点
                        canvas.drawRect(left, top, right, bottom, paint)
                    }
                }
            }
        }
        
        // 6. 添加边框
        if (style.borderWidth > 0) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = style.borderColor
                this.style = Paint.Style.STROKE
                strokeWidth = style.borderWidth
            }
            
            val borderRect = RectF(
                style.borderWidth / 2,
                style.borderWidth / 2,
                width - style.borderWidth / 2,
                height - style.borderWidth / 2
            )
            canvas.drawRoundRect(borderRect, style.borderRadius, style.borderRadius, borderPaint)
        }
        
        // 7. 添加Logo（如果有）
        style.logoResourceId?.let { logoResId ->
            // 这里需要Context来加载资源，暂时跳过
            // 可以通过传入Context参数来实现
        }

        return bitmap
    }

    /**
     * 生成带Logo的美化二维码
     */
    fun generateBeautifulQRWithLogo(
        content: String,
        size: Int,
        logoBitmap: Bitmap?,
        style: QRStyle = QRStyle()
    ): Bitmap {
        val qrBitmap = generateBeautifulQR(content, size, style)

        // 如果有Logo，添加到中心
        logoBitmap?.let { logo ->
            val canvas = Canvas(qrBitmap)
            val logoSize = (size * 0.2f).toInt() // Logo占二维码的20%
            val logoX = (size - logoSize) / 2f
            val logoY = (size - logoSize) / 2f

            // 创建圆角Logo
            val scaledLogo = Bitmap.createScaledBitmap(logo, logoSize, logoSize, true)
            val roundedLogo = createRoundedBitmap(scaledLogo, style.logoCornerRadius)

            // 绘制白色背景
            val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            val logoBackgroundSize = logoSize + 16f
            val logoBackgroundX = logoX - 8f
            val logoBackgroundY = logoY - 8f
            val logoRect = RectF(
                logoBackgroundX, logoBackgroundY,
                logoBackgroundX + logoBackgroundSize,
                logoBackgroundY + logoBackgroundSize
            )
            canvas.drawRoundRect(logoRect, style.logoCornerRadius + 4f, style.logoCornerRadius + 4f, logoPaint)

            // 绘制Logo
            canvas.drawBitmap(roundedLogo, logoX, logoY, null)
        }

        return qrBitmap
    }

    /**
     * 创建圆角Bitmap
     */
    private fun createRoundedBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }
    
    /**
     * 二维码样式配置
     */
    data class QRStyle(
        // 背景色
        val backgroundColor: Int = Color.WHITE,

        // 前景色渐变
        val foregroundStartColor: Int = 0xFF29A472.toInt(), // 绿色渐变开始
        val foregroundEndColor: Int = 0xFF1E7A5F.toInt(),   // 绿色渐变结束

        // 点的样式
        val dotSize: Float = 1f,
        val roundedDots: Boolean = true,
        val cornerRadius: Float = 2f,

        // 边框
        val borderWidth: Float = 8f,
        val borderColor: Int = 0xFF29A472.toInt(),
        val borderRadius: Float = 12f,

        // Logo配置
        val logoResourceId: Int? = null,
        val logoCornerRadius: Float = 8f
    )
    
    /**
     * 预设样式
     */
    object Styles {
        // 经典绿色主题（匹配应用主色调）
        val CLASSIC_GREEN = QRStyle(
            backgroundColor = Color.WHITE,
            foregroundStartColor = 0xFF29A472.toInt(),
            foregroundEndColor = 0xFF1E7A5F.toInt(),
            roundedDots = true,
            borderWidth = 8f,
            borderColor = 0xFF29A472.toInt()
        )
        
        // 现代蓝色主题
        val MODERN_BLUE = QRStyle(
            backgroundColor = Color.WHITE,
            foregroundStartColor = 0xFF2196F3.toInt(),
            foregroundEndColor = 0xFF1976D2.toInt(),
            roundedDots = true,
            borderWidth = 6f,
            borderColor = 0xFF2196F3.toInt()
        )
        
        // 简约黑白主题
        val MINIMAL_BLACK = QRStyle(
            backgroundColor = Color.WHITE,
            foregroundStartColor = Color.BLACK,
            foregroundEndColor = 0xFF424242.toInt(),
            roundedDots = false,
            borderWidth = 4f,
            borderColor = Color.BLACK,
            borderRadius = 8f
        )

        // 绿色边框主题（透明外部，边框内白色背景）
        val BORDERED_GREEN = QRStyle(
            backgroundColor = Color.TRANSPARENT, // 外部透明
            foregroundStartColor = 0xFF29A472.toInt(),
            foregroundEndColor = 0xFF1E7A5F.toInt(),
            roundedDots = true,
            borderWidth = 8f,
            borderColor = 0xFF29A472.toInt(),
            borderRadius = 12f
        )

        // 纯净绿色主题（无边框，透明背景）
        val PURE_GREEN = QRStyle(
            backgroundColor = Color.TRANSPARENT,
            foregroundStartColor = 0xFF29A472.toInt(),
            foregroundEndColor = 0xFF1E7A5F.toInt(),
            roundedDots = true,
            borderWidth = 0f,
            borderColor = Color.TRANSPARENT,
            cornerRadius = 2f
        )

        // 白色边框主题（白色边框，白色背景）
        val WHITE_BORDERED = QRStyle(
            backgroundColor = Color.WHITE,
            foregroundStartColor = 0xFF29A472.toInt(),
            foregroundEndColor = 0xFF1E7A5F.toInt(),
            roundedDots = true,
            borderWidth = 8f,
            borderColor = Color.WHITE,
            borderRadius = 12f
        )
    }
}
