package com.stwpower.powertap

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager

class KioskModeManager(private val activity: Activity) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var homeWatcher: Runnable? = null
    
    fun enableKioskMode() {
        // 设置全屏
        setupFullscreen()
        
        // 开始监控Home键
        startHomeWatcher()
    }
    
    fun disableKioskMode() {
        // 停止监控
        stopHomeWatcher()
    }
    
    private fun setupFullscreen() {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
    }
    
    private fun startHomeWatcher() {
        homeWatcher = object : Runnable {
            override fun run() {
                // 检查当前是否为前台应用
                if (!isAppInForeground()) {
                    // 如果不是，重新启动MainActivity
                    val intent = Intent(activity, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    activity.startActivity(intent)
                }
                
                // 每500ms检查一次
                handler.postDelayed(this, 500)
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
}
