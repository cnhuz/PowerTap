package com.stwpower.powertap

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {

    private var clickCount = 0
    private val adminPassword = "admin123"
    private val clickHandler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null
    private lateinit var kioskModeManager: KioskModeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化Kiosk模式管理器
        kioskModeManager = KioskModeManager(this)

        // 设置全屏和隐藏状态栏、导航栏
        setupFullscreen()

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
        // 隐藏状态栏和导航栏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 设置沉浸式全屏模式
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
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
                    // 密码正确，禁用kiosk模式并退出应用
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
        kioskModeManager.enableKioskMode()
    }

    override fun onPause() {
        super.onPause()
        // 暂停kiosk模式监控
        kioskModeManager.disableKioskMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 重新设置全屏模式
            setupFullscreen()
        }
    }
}