package com.stwpower.powertap

import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class KioskModeManager(private val activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var homeWatcher: Runnable? = null
    private var homeKeyReceiver: BroadcastReceiver? = null
    private var isKioskEnabled = false
    
    fun enableKioskMode() {
        isKioskEnabled = true

        // 设置全屏
        setupFullscreen()

        // 开始监控Home键
        startHomeWatcher()

        // 注册Home键监听
        registerHomeKeyReceiver()

        // 设置窗口监听器来防止状态栏和导航栏的滑动
        setupWindowInsetsListener()
    }

    fun disableKioskMode() {
        isKioskEnabled = false

        // 停止监控
        stopHomeWatcher()

        // 注销Home键监听
        unregisterHomeKeyReceiver()
    }
    
    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用新的WindowInsetsController
            val controller = activity.window.insetsController
            controller?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            )
        }

        // 设置窗口标志
        activity.window.setFlags(
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
    
    private fun startHomeWatcher() {
        homeWatcher = object : Runnable {
            override fun run() {
                if (isKioskEnabled) {
                    // 检查当前是否为前台应用
                    if (!isAppInForeground()) {
                        // 如果不是，重新启动MainActivity
                        val intent = Intent(activity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        activity.startActivity(intent)
                    }

                    // 重新应用全屏设置
                    setupFullscreen()

                    // 每200ms检查一次，更频繁的检查
                    handler.postDelayed(this, 200)
                }
            }
        }
        handler.post(homeWatcher!!)
    }
    
    private fun stopHomeWatcher() {
        homeWatcher?.let { handler.removeCallbacks(it) }
    }
    
    private fun isAppInForeground(): Boolean {
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        
        val packageName = activity.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun registerHomeKeyReceiver() {
        homeKeyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey" || reason == "recentapps") {
                        // 检测到Home键或最近任务键，重新启动应用
                        handler.postDelayed({
                            if (isKioskEnabled) {
                                val restartIntent = Intent(activity, MainActivity::class.java)
                                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                activity.startActivity(restartIntent)
                            }
                        }, 100)
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        activity.registerReceiver(homeKeyReceiver, filter)
    }

    private fun unregisterHomeKeyReceiver() {
        homeKeyReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (e: Exception) {
                // 忽略注销错误
            }
            homeKeyReceiver = null
        }
    }

    private fun setupWindowInsetsListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.decorView.setOnApplyWindowInsetsListener { view, insets ->
                if (isKioskEnabled) {
                    // 当系统栏显示时，立即隐藏它们
                    val controller = activity.window.insetsController
                    controller?.let {
                        it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
                insets
            }
        } else {
            // 对于旧版本，使用系统UI可见性监听器
            activity.window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (isKioskEnabled) {
                    // 当系统UI变为可见时，重新隐藏
                    handler.postDelayed({
                        if (isKioskEnabled) {
                            setupFullscreen()
                        }
                    }, 100)
                }
            }
        }
    }
}
