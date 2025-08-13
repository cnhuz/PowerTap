package com.stwpower.powertap.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 开机完成广播接收器
 * 用于实现应用开机自启功能
 */
class BootCompletedReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "powertap"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "接收到开机广播: $action")
        
        // 检查是否是开机完成广播
        if (Intent.ACTION_BOOT_COMPLETED == action || 
            ACTION_QUICKBOOT_POWERON == action || 
            HTC_QUICKBOOT_POWERON == action) {
            
            Log.d(TAG, "设备开机完成，启动应用...")
            
            // 延迟启动应用，确保系统完全启动
            Handler(Looper.getMainLooper()).postDelayed({
                startMainActivity(context)
            }, 3000) // 延迟3秒启动
        }
    }
    
    /**
     * 启动主Activity
     */
    private fun startMainActivity(context: Context) {
        try {
            Log.d(TAG, "启动主Activity...")
            val intent = Intent(context, com.stwpower.powertap.MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.startActivity(intent)
            Log.d(TAG, "主Activity启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动主Activity失败", e)
        }
    }
}