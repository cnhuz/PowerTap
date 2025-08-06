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

    // 允许的Activity白名单
    private val allowedActivities = setOf(
        "com.stwpower.powertap.MainActivity",
        "com.stwpower.powertap.TerminalPaymentActivity",
        "com.stwpower.powertap.AppPaymentActivity",
        "com.stwpower.powertap.AdminSettingsActivity"
    )
    
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
                    handler.postDelayed(this, 1000) // 每1秒检查一次
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

    private fun isAllowedActivityInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 检查当前前台任务
        val runningTasks = try {
            activityManager.getRunningTasks(1)
        } catch (e: Exception) {
            return isAppInForeground() // 如果无法获取任务信息，回退到进程检查
        }

        if (runningTasks.isNotEmpty()) {
            val topActivity = runningTasks[0].topActivity
            val topActivityName = topActivity?.className

            // 检查顶部Activity是否在白名单中
            return topActivityName != null && allowedActivities.contains(topActivityName)
        }

        return isAppInForeground()
    }
}
