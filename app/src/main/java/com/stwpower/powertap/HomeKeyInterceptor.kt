package com.stwpower.powertap

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

class HomeKeyInterceptor(private val activity: Activity) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var homeKeyReceiver: BroadcastReceiver? = null
    private var isIntercepting = false
    
    fun startIntercepting() {
        isIntercepting = true
        registerHomeKeyReceiver()
    }
    
    fun stopIntercepting() {
        isIntercepting = false
        unregisterHomeKeyReceiver()
    }
    
    private fun registerHomeKeyReceiver() {
        homeKeyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                if (action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS && isIntercepting) {
                    val reason = intent.getStringExtra("reason")
                    when (reason) {
                        "homekey" -> {
                            // Home键被按下，立即重新启动应用
                            restartApp("Home键")
                        }
                        "recentapps" -> {
                            // 最近任务键被按下，立即重新启动应用
                            restartApp("最近任务键")
                        }
                        "assist" -> {
                            // 助手键被按下，立即重新启动应用
                            restartApp("助手键")
                        }
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
    
    private fun restartApp(trigger: String) {
        if (!MainActivity.isAdminExiting) {
            // 立即重新启动应用
            handler.post {
                val intent = Intent(activity, MainActivity::class.java)
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                activity.startActivity(intent)

                // 强制结束当前Activity
                if (activity !is MainActivity) {
                    activity.finish()
                }
            }
        }
    }
    
    // 处理物理按键事件
    fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!isIntercepting || MainActivity.isAdminExiting) {
            return false
        }
        
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME -> {
                // 拦截Home键
                restartApp("物理Home键")
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                // 拦截返回键
                true
            }
            KeyEvent.KEYCODE_APP_SWITCH -> {
                // 拦截应用切换键
                restartApp("应用切换键")
                true
            }
            KeyEvent.KEYCODE_MENU -> {
                // 拦截菜单键
                true
            }
            else -> false
        }
    }
}
