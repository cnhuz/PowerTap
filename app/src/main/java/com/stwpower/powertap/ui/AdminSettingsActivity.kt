package com.stwpower.powertap.ui

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.stwpower.powertap.core.kiosk.HomeKeyInterceptor
import com.stwpower.powertap.core.kiosk.KioskWatchdogService
import com.stwpower.powertap.MainActivity
import com.stwpower.powertap.R
import java.util.*

class AdminSettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var btnChangePassword: Button
    private lateinit var btnSetDefaultLanguage: Button
    private lateinit var btnExitApp: Button
    private lateinit var btnBackToMain: Button
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏
        setupFullscreen()

        setContentView(R.layout.activity_admin_settings)

        sharedPreferences = getSharedPreferences("admin_settings", MODE_PRIVATE)
        homeKeyInterceptor = HomeKeyInterceptor(this)

        setupViews()
        setupClickListeners()
    }
    
    private fun setupViews() {
        btnChangePassword = findViewById(R.id.btn_change_password)
        btnSetDefaultLanguage = findViewById(R.id.btn_set_default_language)
        btnExitApp = findViewById(R.id.btn_exit_app)
        btnBackToMain = findViewById(R.id.btn_back_to_main)
    }
    
    private fun setupClickListeners() {
        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        
        btnSetDefaultLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }
        
        btnExitApp.setOnClickListener {
            showExitConfirmationDialog()
        }
        
        btnBackToMain.setOnClickListener {
            finish()
        }
    }
    
    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.et_current_password)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.et_new_password)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.et_confirm_password)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_password_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                val currentPassword = etCurrentPassword.text.toString()
                val newPassword = etNewPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()

                val savedPassword = sharedPreferences.getString("admin_password", "admin123") ?: "admin123"

                when {
                    currentPassword != savedPassword -> {
                        Toast.makeText(this, getString(R.string.current_password_incorrect), Toast.LENGTH_SHORT).show()
                    }
                    newPassword.isEmpty() -> {
                        Toast.makeText(this, getString(R.string.new_password_empty), Toast.LENGTH_SHORT).show()
                    }
                    newPassword != confirmPassword -> {
                        Toast.makeText(this, getString(R.string.password_mismatch), Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        sharedPreferences.edit().putString("admin_password", newPassword).apply()
                        Toast.makeText(this, getString(R.string.password_changed_success), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showLanguageSelectionDialog() {
        val languages = arrayOf(
            getString(R.string.language_chinese),
            getString(R.string.language_english),
            getString(R.string.language_japanese)
        )
        val languageCodes = arrayOf("zh", "en", "ja")

        val currentLanguage = sharedPreferences.getString("default_language", "zh") ?: "zh"
        val currentIndex = languageCodes.indexOf(currentLanguage)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.set_default_language_title))
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val selectedLanguageCode = languageCodes[which]
                sharedPreferences.edit().putString("default_language", selectedLanguageCode).apply()

                // 立即应用语言设置
                changeLanguage(selectedLanguageCode)

                Toast.makeText(this, getString(R.string.default_language_set, languages[which]), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exit_confirmation_title))
            .setMessage(getString(R.string.exit_confirmation_message))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                // 设置管理员退出标志
                MainActivity.isAdminExiting = true

                // 停止看门狗服务
                stopService(Intent(this, KioskWatchdogService::class.java))

                // 退出应用
                finishAffinity()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    override fun onResume() {
        super.onResume()
        homeKeyInterceptor.startIntercepting()
        setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        // 保持Home键拦截
    }

    override fun onDestroy() {
        super.onDestroy()
        homeKeyInterceptor.stopIntercepting()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (homeKeyInterceptor.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        // 允许返回到主界面
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        }
    }
    
    companion object {
        fun getAdminPassword(context: android.content.Context): String {
            val sharedPreferences = context.getSharedPreferences("admin_settings", MODE_PRIVATE)
            return sharedPreferences.getString("admin_password", "stw2025") ?: "stw2025"
        }
        
        fun getDefaultLanguage(context: android.content.Context): String {
            val sharedPreferences = context.getSharedPreferences("admin_settings", MODE_PRIVATE)
            return sharedPreferences.getString("default_language", "en") ?: "en"
        }
    }
}
