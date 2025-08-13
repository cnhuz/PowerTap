package com.stwpower.powertap.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.stwpower.powertap.R

class DataPopupActivity : Activity() {
    
    companion object {
        private const val TAG = "DataPopupActivity"
        const val EXTRA_DATA_LIST = "extra_data_list"
        private const val AUTO_CLOSE_DELAY: Long = 20000 // 10秒后自动关闭
    }
    
    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable {
        Log.d(TAG, "弹窗显示20秒后自动关闭")
        finish()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口属性，使Activity以弹窗形式显示
        setupWindowAttributes()
        
        setContentView(R.layout.activity_data_popup)
        
        // 设置模糊背景
        setupBlurBackground()
        
        // 获取传递的数据
        val dataList = intent.getCharSequenceArrayExtra(EXTRA_DATA_LIST)
        
        if (dataList != null) {
            Log.d(TAG, "接收到数据，共${dataList.size}条记录")
            setupViews(dataList)
            // 启动自动关闭定时器
            startAutoCloseTimer()
        } else {
            Log.w(TAG, "未接收到数据")
            finish()
        }
    }
    
    private fun setupWindowAttributes() {
        // 设置窗口背景透明
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 设置窗口位置居中
        val layoutParams = window.attributes
        layoutParams.gravity = Gravity.CENTER
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        window.attributes = layoutParams
        
        // 设置窗口标志，确保弹窗能正确显示
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        
        // 设置窗口输入模式
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)
        
        // 设置全屏模式
        setupFullscreen()
    }
    
    private fun setupFullscreen() {
        // 使用现代API设置全屏
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用新的WindowInsetsController
            val controller = window.insetsController
            controller?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            // 兼容旧版本
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }

        // 设置窗口标志
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
    }
    
    private fun setupViews(dataList: Array<CharSequence>) {
        val contentTextView = findViewById<TextView>(R.id.tv_popup_content)
        val confirmButton = findViewById<Button>(R.id.btn_popup_confirm)
        
        // 设置确认按钮圆角背景
        setRoundedBackground(confirmButton, 0xFF29A472.toInt(), 8f)
        
        // 构建内容文本
        val contentBuilder = StringBuilder()
        for (i in dataList.indices) {
            contentBuilder.append("${i + 1}. ${dataList[i]}\n")
        }
        
        // 设置内容
        contentTextView.text = contentBuilder.toString()
        
        // 设置确认按钮点击事件
        confirmButton.setOnClickListener {
            // 用户点击确认按钮，取消自动关闭并立即关闭
            cancelAutoCloseTimer()
            finish()
        }
    }
    
    /**
     * 设置圆角背景
     */
    private fun setRoundedBackground(button: Button, color: Int, radius: Float) {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius * resources.displayMetrics.density
        button.background = drawable
    }
    
    /**
     * 设置模糊背景
     */
    private fun setupBlurBackground() {
        val blurBackground = findViewById<ImageView>(R.id.iv_blur_background)
        // 在实际应用中，这里可以设置一个模糊的背景图像
        // 由于这是一个弹窗，获取屏幕截图并模糊化会比较复杂
        // 这里我们使用一个半透明的深色背景作为替代
        blurBackground.setBackgroundColor(0x40000000) // 半透明黑色
    }
    
    /**
     * 启动自动关闭定时器
     */
    private fun startAutoCloseTimer() {
        autoCloseHandler.postDelayed(autoCloseRunnable, AUTO_CLOSE_DELAY)
    }
    
    /**
     * 取消自动关闭定时器
     */
    private fun cancelAutoCloseTimer() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时取消定时器
        cancelAutoCloseTimer()
    }
}