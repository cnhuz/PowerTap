package com.stwpower.powertap.core.kiosk

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.stwpower.powertap.MainActivity

class KioskWatchdogService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var isWatching = false

    // 允许的Activity白名单
    private val allowedActivities = setOf(
        "com.stwpower.powertap.MainActivity",
        "com.stwpower.powertap.ui.TerminalPaymentActivity",
        "com.stwpower.powertap.ui.AppPaymentActivity",
        "com.stwpower.powertap.ui.AdminSettingsActivity"
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

        // 使用混合检测策略
        val isAppForeground = isAppInForeground()
        val isAllowedActivity = isAllowedActivityInForeground()

//        Log.d("KioskWatchdog", "应用在前台: $isAppForeground, 允许的Activity: $isAllowedActivity")

        // 如果应用在前台但Activity不被允许，或者应用不在前台，都需要重启
        if (isAppForeground && !isAllowedActivity) {
//            Log.w("KioskWatchdog", "应用在前台但Activity不被允许，重新启动")
            restartMainActivity()
        } else if (!isAppForeground) {
//            Log.w("KioskWatchdog", "应用不在前台，重新启动")
            restartMainActivity()
        }
    }

    private fun restartMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        startActivity(intent)
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
//            Log.w("KioskWatchdog", "无法获取运行任务: ${e.message}")
            return isAppInForeground() // 如果无法获取任务信息，回退到进程检查
        }

        if (runningTasks.isNotEmpty()) {
            val topActivity = runningTasks[0].topActivity
            val topActivityName = topActivity?.className
            val topPackageName = topActivity?.packageName

//            Log.d("KioskWatchdog", "当前前台Activity: $topActivityName, 包名: $topPackageName")

            // 如果是我们自己的应用，检查Activity是否在白名单中
            if (topPackageName == packageName) {
                val isAllowed = topActivityName != null && allowedActivities.contains(topActivityName)
//                Log.d("KioskWatchdog", "我们的应用Activity检查结果: $isAllowed")
                return isAllowed
            } else {
                // 如果不是我们的应用，说明需要重新启动
//                Log.d("KioskWatchdog", "检测到其他应用在前台: $topPackageName")
                return false
            }
        }

//        Log.d("KioskWatchdog", "没有运行任务，回退到进程检查")
        return isAppInForeground()
    }
}
