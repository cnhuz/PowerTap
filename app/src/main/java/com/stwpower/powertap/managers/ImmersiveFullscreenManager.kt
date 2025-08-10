package com.stwpower.powertap.managers

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.WindowCompat
import com.stwpower.powertap.MainActivity

class ImmersiveFullscreenManager(private val activity: Activity) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var fullscreenRunnable: Runnable? = null
    private var isFullscreenEnabled = false
    
    fun enableFullscreen() {
        isFullscreenEnabled = true
        applyFullscreen()
        startFullscreenMonitor()
    }
    
    fun disableFullscreen() {
        isFullscreenEnabled = false
        stopFullscreenMonitor()
    }
    
    private fun applyFullscreen() {
        if (!isFullscreenEnabled) return
        
        // 设置窗口标志
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        // 设置窗口不适配系统窗口
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用新的WindowInsetsController
            val controller = activity.window.insetsController
            controller?.let {
                // 隐藏状态栏和导航栏
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                
                // 设置行为：禁用滑动呼出
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                // 尝试完全禁用系统手势
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        // Android 12+ 可以更严格地控制
                        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
            }
        } else {
            // 兼容旧版本
            @Suppress("DEPRECATION")
            activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LOW_PROFILE
            )
        }
        
        // 设置系统UI监听器
        setupSystemUiListener()
    }
    
    private fun setupSystemUiListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                // 简化处理
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            activity.window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                // 简化处理
            }
        }
    }
    
    private fun startFullscreenMonitor() {
        fullscreenRunnable = object : Runnable {
            override fun run() {
                if (isFullscreenEnabled && !MainActivity.isAdminExiting) {
                    applyFullscreen()
                    handler.postDelayed(this, 1000) // 每1秒检查一次，降低频率
                }
            }
        }
        handler.post(fullscreenRunnable!!)
    }
    
    private fun stopFullscreenMonitor() {
        fullscreenRunnable?.let { handler.removeCallbacks(it) }
    }
    
    fun onWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus && isFullscreenEnabled) {
            // 重新获得焦点时立即应用全屏
            handler.postDelayed({ applyFullscreen() }, 50)
        }
    }
    
    fun onResume() {
        if (isFullscreenEnabled) {
            applyFullscreen()
        }
    }
}
