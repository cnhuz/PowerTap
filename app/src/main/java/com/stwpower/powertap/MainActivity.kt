package com.stwpower.powertap

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var clickCount = 0
    private val adminPassword = "admin123"
    private val clickHandler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null
    private lateinit var kioskModeManager: KioskModeManager
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private lateinit var fullscreenManager: ImmersiveFullscreenManager

    companion object {
        @JvmStatic
        var isAdminExiting = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化所有管理器
        kioskModeManager = KioskModeManager(this)
        homeKeyInterceptor = HomeKeyInterceptor(this)
        fullscreenManager = ImmersiveFullscreenManager(this)

        // 设置基础全屏
        setupFullscreen()

        // 启动看门狗服务
        startService(Intent(this, KioskWatchdogService::class.java))

        setContentView(R.layout.activity_main)

        setupLanguageButtons()
        setupPaymentButtons()
        setupAdminExit()
    }
    
    private fun setupLanguageButtons() {
        findViewById<ImageButton>(R.id.btn_english).setOnClickListener {
            changeLanguage("en")
        }
        findViewById<ImageButton>(R.id.btn_chinese).setOnClickListener {
            changeLanguage("zh")
        }
        findViewById<ImageButton>(R.id.btn_japanese).setOnClickListener {
            changeLanguage("ja")
        }
    }
    
    private fun setupPaymentButtons() {
        findViewById<Button>(R.id.btn_pay_terminal).setOnClickListener {
            startActivity(Intent(this, TerminalPaymentActivity::class.java))
        }
        
        findViewById<Button>(R.id.btn_pay_app).setOnClickListener {
            startActivity(Intent(this, AppPaymentActivity::class.java))
        }
    }
    
    private fun changeLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.locale = locale
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
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

    private fun setupAdminExit() {
        // 在右下角添加隐藏的点击区域
        val rootView = findViewById<View>(android.R.id.content)
        rootView.setOnTouchListener { _, event ->
            val x = event.x
            val y = event.y
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            // 检查是否点击在右下角区域 (100x100像素)
            if (x > screenWidth - 100 && y > screenHeight - 100) {
                handleAdminClick()
            }
            false
        }
    }

    private fun handleAdminClick() {
        clickCount++

        // 取消之前的重置任务
        clickRunnable?.let { clickHandler.removeCallbacks(it) }

        if (clickCount >= 5) {
            showAdminPasswordDialog()
            clickCount = 0
        } else {
            // 3秒后重置点击计数
            clickRunnable = Runnable { clickCount = 0 }
            clickHandler.postDelayed(clickRunnable!!, 3000)
        }
    }

    private fun showAdminPasswordDialog() {
        val editText = EditText(this)
        editText.hint = getString(R.string.admin_password_hint)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_password_title))
            .setMessage(getString(R.string.admin_password_message))
            .setView(editText)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val inputPassword = editText.text.toString()
                if (inputPassword == adminPassword) {
                    // 密码正确，设置管理员退出标志，停止看门狗服务并退出应用
                    isAdminExiting = true
                    stopService(Intent(this@MainActivity, KioskWatchdogService::class.java))
                    kioskModeManager.disableKioskMode()
                    finishAffinity()
                } else {
                    Toast.makeText(this, getString(R.string.password_incorrect), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .setCancelable(false)
            .show()
    }

    override fun onBackPressed() {
        // 禁用返回键
        // 不调用 super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()

        // 启用kiosk模式
        enableKioskMode()
    }

    private fun enableKioskMode() {
        // 启用所有保护机制
        fullscreenManager.enableFullscreen()
        kioskModeManager.enableKioskMode()
        homeKeyInterceptor.startIntercepting()
        fullscreenManager.onResume()

        // 启动看门狗服务
        startService(Intent(this, KioskWatchdogService::class.java))
    }



    override fun onPause() {
        super.onPause()
        // 不要在onPause中禁用kiosk模式，保持监控
        // kioskModeManager.disableKioskMode()
    }

    override fun onStop() {
        super.onStop()
        // 只有在非管理员退出的情况下才重新启动
        if (!isAdminExiting && ::kioskModeManager.isInitialized) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 只有在管理员退出时才真正禁用
        if (::fullscreenManager.isInitialized) {
            fullscreenManager.disableFullscreen()
        }
        if (::kioskModeManager.isInitialized) {
            kioskModeManager.disableKioskMode()
        }
        if (::homeKeyInterceptor.isInitialized) {
            homeKeyInterceptor.stopIntercepting()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 使用Home键拦截器处理按键事件
        if (::homeKeyInterceptor.isInitialized && homeKeyInterceptor.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }



    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 重新设置全屏模式
            if (::fullscreenManager.isInitialized) {
                fullscreenManager.onWindowFocusChanged(hasFocus)
            }
            setupFullscreen()
        }
    }
}