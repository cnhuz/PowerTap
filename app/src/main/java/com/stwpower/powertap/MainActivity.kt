package com.stwpower.powertap

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.stwpower.powertap.ui.AdminSettingsActivity
import com.stwpower.powertap.ui.AppPaymentActivity
import com.stwpower.powertap.ui.TerminalPaymentActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null
    private lateinit var kioskModeManager: KioskModeManager
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private lateinit var fullscreenManager: ImmersiveFullscreenManager
    private var adminClickArea: View? = null

    companion object {
        @JvmStatic
        var isAdminExiting = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 重置管理员退出标志
        isAdminExiting = false

        // 初始化所有管理器
        kioskModeManager = KioskModeManager(this)
        homeKeyInterceptor = HomeKeyInterceptor(this)
        fullscreenManager = ImmersiveFullscreenManager(this)

        // 应用默认语言设置
        applyDefaultLanguage()

        // 设置基础全屏
        setupFullscreen()

        // 启动看门狗服务
        startService(Intent(this, KioskWatchdogService::class.java))

        setContentView(R.layout.activity_main)

        setupLanguageButtons()
        setupPaymentButtons()
        setupAdminExit()
        setupAdminClickArea()
    }
    
    private fun setupLanguageButtons() {
        val englishBtn = findViewById<ImageButton>(R.id.btn_english)
        val chineseBtn = findViewById<ImageButton>(R.id.btn_chinese)
        val japaneseBtn = findViewById<ImageButton>(R.id.btn_japanese)
        val germanBtn = findViewById<ImageButton>(R.id.btn_german)
        val russianBtn = findViewById<ImageButton>(R.id.btn_russian)

        // 设置圆角
         setRoundedBackground(englishBtn, Color.parseColor("#1E2428"), 12f)
         setRoundedBackground(chineseBtn, Color.parseColor("#1E2428"), 12f)
         setRoundedBackground(japaneseBtn, Color.parseColor("#1E2428"), 12f)
         setRoundedBackground(germanBtn, Color.parseColor("#1E2428"), 12f)
         setRoundedBackground(russianBtn, Color.parseColor("#1E2428"), 12f)

        englishBtn.setOnClickListener {
            changeLanguage("en")
            saveCurrentLanguageAsDefault("en")
        }
        chineseBtn.setOnClickListener {
            changeLanguage("zh")
            saveCurrentLanguageAsDefault("zh")
        }
        japaneseBtn.setOnClickListener {
            changeLanguage("ja")
            saveCurrentLanguageAsDefault("ja")
        }
        germanBtn.setOnClickListener {
            changeLanguage("de")
            saveCurrentLanguageAsDefault("de")
        }
        russianBtn.setOnClickListener {
            changeLanguage("ru")
            saveCurrentLanguageAsDefault("ru")
        }
    }
    
    private fun setupPaymentButtons() {
        val terminalButton = findViewById<TextView>(R.id.btn_pay_terminal)
        val appButton = findViewById<TextView>(R.id.btn_pay_app)

        // 设置圆角
        setRoundedBackground(terminalButton, Color.parseColor("#29A472"), 18f)

        terminalButton.setOnClickListener {
            startActivity(Intent(this, TerminalPaymentActivity::class.java))
        }

        appButton.setOnClickListener {
            startActivity(Intent(this, AppPaymentActivity::class.java))
        }
    }

    private fun setRoundedBackground(view: TextView, color: Int, radius: Float) {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius * resources.displayMetrics.density
        view.background = drawable
    }

    private fun setRoundedBackground(view: ImageButton, color: Int, radius: Float) {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius * resources.displayMetrics.density
        view.background = drawable
        view.clipToOutline = true
    }
    
    private fun changeLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.locale = locale
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    private fun applyDefaultLanguage() {
        val defaultLanguage = AdminSettingsActivity.getDefaultLanguage(this)
        val currentLanguage = Locale.getDefault().language
        if (currentLanguage != defaultLanguage) {
            changeLanguage(defaultLanguage)
        }
    }

    private fun saveCurrentLanguageAsDefault(languageCode: String) {
        val sharedPreferences = getSharedPreferences("admin_settings", MODE_PRIVATE)
        sharedPreferences.edit().putString("default_language", languageCode).apply()
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
        // 这个方法现在只处理逻辑，UI在setupAdminClickArea中处理
    }

    private fun setupAdminClickArea() {
        // 创建半透明的点击区域
        adminClickArea = View(this).apply {
            setBackgroundColor(Color.argb(30, 255, 0, 0)) // 半透明红色
            layoutParams = FrameLayout.LayoutParams(100, 100).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }

            setOnClickListener {
                handleAdminClick()
            }
        }

        // 添加到根布局
        val rootLayout = findViewById<FrameLayout>(android.R.id.content)
        rootLayout.addView(adminClickArea)
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
            .setMessage(getString(R.string.select_operation))
            .setView(editText)
            .setPositiveButton(getString(R.string.admin_settings)) { _, _ ->
                val inputPassword = editText.text.toString()
                val adminPassword = AdminSettingsActivity.getAdminPassword(this@MainActivity)
                if (inputPassword == adminPassword) {
                    // 密码正确，进入管理员设置页面
                    val intent = Intent(this@MainActivity, AdminSettingsActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.password_incorrect), Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(getString(R.string.direct_exit)) { _, _ ->
                val inputPassword = editText.text.toString()
                val adminPassword = AdminSettingsActivity.getAdminPassword(this@MainActivity)
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
        // 只有在非管理员退出且应用真正离开前台的情况下才重新启动
        // 添加延迟检查，避免在应用内Activity切换时误重启
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isAdminExiting && ::kioskModeManager.isInitialized) {
                // 检查是否真的离开了应用（而不是切换到应用内其他Activity）
                if (!isAppInForeground()) {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                }
            }
        }, 200) // 增加延迟时间，给Activity切换更多时间
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