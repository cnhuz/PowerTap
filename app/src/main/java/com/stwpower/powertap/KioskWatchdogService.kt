package com.stwpower.powertap

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class KioskWatchdogService : Service() {
    
    private val handler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var isWatching = false
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startWatchdog()
        return START_STICKY // 服务被杀死后自动重启
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopWatchdog()
    }
    
    private fun startWatchdog() {
        isWatching = true
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (isWatching) {
                    checkAndRestoreApp()
                    handler.postDelayed(this, 1000) // 每秒检查一次
                }
            }
        }
        handler.post(watchdogRunnable!!)
    }
    
    private fun stopWatchdog() {
        isWatching = false
        watchdogRunnable?.let { handler.removeCallbacks(it) }
    }
    
    private fun checkAndRestoreApp() {
        // 检查是否是管理员退出，如果是则不重新启动
        if (MainActivity.isAdminExiting) {
            stopSelf()
            return
        }

        if (!isAppInForeground()) {
            // 应用不在前台，重新启动
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            startActivity(intent)
        }
    }
    
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        
        val packageName = packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }
}
