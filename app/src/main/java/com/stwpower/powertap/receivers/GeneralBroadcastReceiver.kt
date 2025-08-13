package com.stwpower.powertap.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通用广播接收器
 * 用于实现应用开机自启和接收自定义广播通知功能
 */
class GeneralBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "powertap"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_RECEIVE_DATA = "com.stwpower.player.ACTION_RECEIVE_DATA"
        private const val CHANNEL_ID = "powertap_notifications"
        private const val NOTIFICATION_ID = 1001
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "接收到广播: $action")
        
        when (action) {
            // 开机完成广播处理
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            HTC_QUICKBOOT_POWERON -> {
                Log.d(TAG, "设备开机完成，启动应用...")
                
                // 延迟启动应用，确保系统完全启动
                Handler(Looper.getMainLooper()).postDelayed({
                    startMainActivity(context)
                }, 3000) // 延迟3秒启动
            }
            
            // 自定义数据接收广播处理
            ACTION_RECEIVE_DATA -> {
                Log.d(TAG, "接收到自定义数据广播")
                handleCustomDataBroadcast(context, intent)
            }
            
            else -> {
                Log.w(TAG, "未知广播动作: $action")
            }
        }
    }
    
    /**
     * 处理自定义数据广播
     */
    private fun handleCustomDataBroadcast(context: Context, intent: Intent) {
        try {
            // 获取广播中的数据
            val listData = intent.getCharSequenceArrayExtra("listData")
            
            Log.d(TAG, "接收到自定义数据:")
            if (listData != null) {
                Log.d(TAG, "  listData长度: ${listData.size}")
                for (i in listData.indices) {
                    Log.d(TAG, "  listData[$i]: ${listData[i]}")
                }
                
                // 创建通知显示数据
                showDataNotification(context, listData)
                
                // 处理电源银行数据
                processPowerBankData(context, listData)
            } else {
                Log.d(TAG, "  listData为空")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理自定义数据广播时出错", e)
        }
    }
    
    /**
     * 显示数据通知
     */
    private fun showDataNotification(context: Context, data: Array<CharSequence>) {
        try {
            // 创建通知渠道（Android 8.0及以上）
            createNotificationChannel(context)
            
            // 构建通知内容
            val contentText = buildNotificationContent(data)
            
            // 创建点击意图（打开主Activity）
            val intent = Intent(context, com.stwpower.powertap.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 构建通知
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("电源银行数据更新")
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(buildDetailedContent(data)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setColor(Color.parseColor("#29A472"))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
            
            // 显示通知
            with(NotificationManagerCompat.from(context)) {
                // 检查通知权限
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    notify(NOTIFICATION_ID, builder.build())
                    Log.d(TAG, "通知显示成功")
                } else {
                    Log.w(TAG, "通知权限未开启，无法显示通知")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "显示通知时出错", e)
        }
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "PowerTap Notifications"
            val descriptionText = "PowerTap应用通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 100)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 构建通知简要内容
     */
    private fun buildNotificationContent(data: Array<CharSequence>): String {
        return if (data.size == 1) {
            "收到1条电源银行数据: ${data[0]}"
        } else {
            "收到${data.size}条电源银行数据"
        }
    }
    
    /**
     * 构建详细通知内容
     */
    private fun buildDetailedContent(data: Array<CharSequence>): String {
        val sb = StringBuilder()
        sb.append("电源银行数据详情:\\n")
        for (i in data.indices) {
            sb.append("${i + 1}. ${data[i]}\\n")
        }
        return sb.toString().trim()
    }
    
    /**
     * 处理电源银行数据
     */
    private fun processPowerBankData(context: Context, data: Array<CharSequence>) {
        try {
            Log.d(TAG, "处理电源银行数据，共${data.size}条记录")
            
            // 在这里处理电源银行数据
            // 例如：更新UI、保存数据到数据库、发送通知等
            for (item in data) {
                Log.d(TAG, "处理电源银行项: $item")
                // 根据实际需求处理每个电源银行项
                // 例如解析JSON数据、更新状态等
            }
            
            // 如果需要通知Activity更新UI，可以发送本地广播
            // 或者启动Service来处理数据
            
        } catch (e: Exception) {
            Log.e(TAG, "处理电源银行数据时出错", e)
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