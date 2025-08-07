package com.stwpower.powertap.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 高性能进度条组件
 * 专为低性能设备优化，使用直接Canvas绘制，避免复杂的Drawable层级
 *
 * 性能优化特性：
 * - 预创建Paint和RectF对象，避免在onDraw中重复创建
 * - 智能重绘：只在进度真正改变时才调用invalidate()
 * - 直接Canvas绘制，无Drawable层级开销
 * - 无抗锯齿计算（可选），进一步提升性能
 */
class HighPerformanceProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 100f
    private var maxProgress = 100f

    // 性能模式：true = 最高性能（无抗锯齿），false = 平衡模式（有抗锯齿）
    private var highPerformanceMode = true

    // 使用预创建的Paint对象避免重复创建
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#181E22") // 暗灰色背景
        style = Paint.Style.FILL
        isAntiAlias = !highPerformanceMode
    }

    private val progressPaint = Paint().apply {
        color = Color.parseColor("#29A472") // 绿色进度
        style = Paint.Style.FILL
        isAntiAlias = !highPerformanceMode
    }
    
    // 预创建RectF对象避免在onDraw中创建
    private val backgroundRect = RectF()
    private val progressRect = RectF()
    
    fun setProgress(newProgress: Float) {
        if (progress != newProgress) {
            progress = newProgress.coerceIn(0f, maxProgress)
            // 只在进度真正改变时才重绘
            invalidate()
        }
    }
    
    fun getProgress(): Float = progress
    
    fun setMaxProgress(max: Float) {
        maxProgress = max
        invalidate()
    }

    /**
     * 设置性能模式
     * @param highPerformance true = 最高性能模式（无抗锯齿），false = 平衡模式（有抗锯齿）
     */
    fun setHighPerformanceMode(highPerformance: Boolean) {
        if (highPerformanceMode != highPerformance) {
            highPerformanceMode = highPerformance
            backgroundPaint.isAntiAlias = !highPerformance
            progressPaint.isAntiAlias = !highPerformance
            invalidate()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // 绘制背景
        backgroundRect.set(0f, 0f, width, height)
        canvas.drawRect(backgroundRect, backgroundPaint)
        
        // 绘制进度条
        if (progress > 0) {
            val progressWidth = (progress / maxProgress) * width
            progressRect.set(0f, 0f, progressWidth, height)
            canvas.drawRect(progressRect, progressPaint)
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }
}
