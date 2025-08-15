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
import android.provider.Settings
import android.util.Log
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.stwpower.powertap.config.ConfigLoader
import com.stwpower.powertap.core.kiosk.HomeKeyInterceptor
import com.stwpower.powertap.core.kiosk.KioskModeManager
import com.stwpower.powertap.core.kiosk.KioskWatchdogService
import com.stwpower.powertap.ui.AdminSettingsActivity
import com.stwpower.powertap.ui.AppPaymentActivity
import com.stwpower.powertap.ui.TerminalPaymentActivity
import com.stwpower.powertap.data.api.MyApiClient
import com.stwpower.powertap.managers.ImmersiveFullscreenManager
import com.stwpower.powertap.terminal.TerminalConnectionManager
import com.stwpower.powertap.managers.DirectPermissionManager
import com.stwpower.powertap.utils.OptimizedQRGenerator
import com.stwpower.powertap.managers.PermissionManager
import com.stwpower.powertap.managers.PreferenceManager
import com.stwpower.powertap.utils.QRCodeUrlProcessor
import com.stwpower.powertap.managers.SystemPermissionManager
import com.stwpower.powertap.utils.ChargeRuleManager
import com.stwpower.powertap.utils.QRCodeCacheManager
import com.stwpower.powertap.terminal.StripeTerminalManager
import com.stwpower.powertap.terminal.DisplayState
import com.stwpower.powertap.terminal.UIType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private var clickCount = 0
    private val clickHandler = Handler(Looper.getMainLooper())
    private var clickRunnable: Runnable? = null
    private lateinit var kioskModeManager: KioskModeManager
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private lateinit var fullscreenManager: ImmersiveFullscreenManager
    private var adminClickArea: View? = null
    private var permissionsReady = false // 权限是否准备完成

    // 防抖动相关
    private var lastClickTime = 0L
    private val clickDebounceTime = 1000L // 1秒防抖动

    // 不再需要单独的Terminal管理器，使用TerminalConnectionManager

    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    companion object {
        @JvmStatic
        var isAdminExiting = false
        private const val TAG = "powertap"
    }

    private fun loadAndUseConfig() {
        try {
            // 直接使用静态变量 - 更简洁！
            Log.d(TAG, "=== 配置信息 ===")
            Log.d(TAG, "API URL: ${ConfigLoader.apiUrl}")
            Log.d(TAG, "qrCodeUrl: ${ConfigLoader.qrCodeUrl}")
            Log.d(TAG, "IMEI: ${ConfigLoader.imei}")
            Log.d(TAG, "Debug Mode: ${ConfigLoader.enableDebug}")
            Log.d(TAG, "currency: ${ConfigLoader.currency}")
            Log.d(TAG, "===============")

            // 根据配置调整应用行为
            if (ConfigLoader.enableDebug) {
                // 调试模式下的特殊处理
                Log.d(TAG, "Debug mode enabled - showing detailed logs")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config in MainActivity", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化PreferenceManager
        PreferenceManager.init(this)

        // 使用配置 - 此时Application.onCreate()已经执行完毕，配置已加载
        loadAndUseConfig()

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
        setupDebugFeatures()
        setupBrandName()

        // 检查和请求权限
        checkAndRequestPermissions()

        // 获取设备信息和QR码
        getDeviceInfoAndQrCode()

        // 预生成二维码（提升性能）
        preGenerateQRCodes()
        
        // 确保配置加载完成后再更新价格信息
        updatePriceInfoAfterConfigLoaded()
    }

    /**
     * 在配置加载完成后再更新价格信息
     */
    private fun updatePriceInfoAfterConfigLoaded() {
        if (com.stwpower.powertap.config.ConfigLoader.isConfigLoaded) {
            // 配置已加载，直接更新价格信息
            updatePriceInfo()
        } else {
            // 配置还未加载完成，等待一段时间后重试
            Handler(Looper.getMainLooper()).postDelayed({
                updatePriceInfo()
            }, 100)
        }
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

    /**
     * 检查点击是否被允许（防抖动）
     */
    private fun isClickAllowed(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < clickDebounceTime) {
            return false
        }
        lastClickTime = currentTime
        return true
    }

    private fun setupPaymentButtons() {
        val terminalButton = findViewById<TextView>(R.id.btn_pay_terminal)
        val appButton = findViewById<TextView>(R.id.btn_pay_app)

        // 设置圆角
        setRoundedBackground(terminalButton, Color.parseColor("#29A472"), 18f)

        terminalButton.setOnClickListener {
            if (isClickAllowed()) {
                Log.d(TAG, "Terminal button clicked")

                // 立即提供视觉反馈
                terminalButton.alpha = 0.7f
                terminalButton.isEnabled = false

                // 检查Terminal权限和GPS状态
                if (PermissionManager.isTerminalReady(this)) {
                    // 检查Terminal是否已经连接
                    if (TerminalConnectionManager.hasActiveConnection()) {
                        // Terminal已经连接，直接启动TerminalPaymentActivity
                        // 使用Handler延迟启动Activity
                        Handler(Looper.getMainLooper()).post {
                            try {
                                Log.d(TAG, "Starting TerminalPaymentActivity...")
                                val intent = Intent(this, TerminalPaymentActivity::class.java)
                                startActivity(intent)
                                // 添加过渡动画
                                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                                Log.d(TAG, "Terminal activity started successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to start Terminal activity", e)
                            } finally {
                                // 恢复按钮状态
                                Handler(Looper.getMainLooper()).postDelayed({
                                    terminalButton.alpha = 1.0f
                                    terminalButton.isEnabled = true
                                }, 1000)
                            }
                        }
                    } else {
                        // Terminal未连接，显示提示信息
                        Log.w(TAG, "Terminal not connected, showing connection dialog")
                        showTerminalNotConnectedDialog()
                        
                        // 恢复按钮状态
                        Handler(Looper.getMainLooper()).postDelayed({
                            terminalButton.alpha = 1.0f
                            terminalButton.isEnabled = true
                        }, 1000)
                    }
                } else {
                    // 权限或GPS未准备好，显示提示对话框
                    Log.w(TAG, "Terminal not ready, showing permission dialog")
                    showTerminalNotReadyDialog()
                    
                    // 恢复按钮状态
                    Handler(Looper.getMainLooper()).postDelayed({
                        terminalButton.alpha = 1.0f
                        terminalButton.isEnabled = true
                    }, 1000)
                }
            } else {
                Log.d(TAG, "Terminal button click ignored (debounce)")
            }
        }

        appButton.setOnClickListener {
            if (isClickAllowed()) {
                Log.d(TAG, "App payment button clicked")

                // 立即提供视觉反馈
                appButton.alpha = 0.7f
                appButton.isEnabled = false

                // 使用Handler延迟启动Activity，避免UI阻塞
                Handler(Looper.getMainLooper()).post {
                    try {
                        Log.d(TAG, "Starting AppPaymentActivity...")
                        val intent = Intent(this, AppPaymentActivity::class.java)
                        startActivity(intent)
                        // 添加过渡动画，减少黑屏
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        Log.d(TAG, "App payment activity started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start App payment activity", e)
                    } finally {
                        // 恢复按钮状态
                        Handler(Looper.getMainLooper()).postDelayed({
                            appButton.alpha = 1.0f
                            appButton.isEnabled = true
                        }, 1000)
                    }
                }
            } else {
                Log.d(TAG, "App payment button click ignored (debounce)")
            }
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

    /**
     * 触发管理员密码框区域
     */
    private fun setupAdminClickArea() {
        // 创建半透明的点击区域
        adminClickArea = View(this).apply {
//            setBackgroundColor(Color.argb(30, 255, 0, 0)) // 半透明红色
            setBackgroundColor(Color.argb(0, 0, 0, 0)) // 透明
            layoutParams = FrameLayout.LayoutParams(125, 125).apply {
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
        // 创建输入框
        val editText = EditText(this)
        editText.hint = getString(R.string.admin_password_hint)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        // 设置输入框样式
        val density = resources.displayMetrics.density
        val paddingHorizontal = (12 * density).toInt() // 12dp
        val paddingVertical = (8 * density).toInt() // 8dp
        editText.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        editText.textSize = 16f
        editText.minHeight = (48 * density).toInt() // 48dp 最小高度

        // 创建容器布局并设置边距
        val container = android.widget.LinearLayout(this)
        container.orientation = android.widget.LinearLayout.VERTICAL
        val layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )

        // 设置边距 (left, top, right, bottom)
        val margin = (20 * density).toInt() // 20dp转换为像素
        layoutParams.setMargins(margin, margin, margin, margin)
        editText.layoutParams = layoutParams

        container.addView(editText)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.admin_password_title))
            .setView(container)
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

        // 只有在权限准备完成后才启用kiosk模式
        if (permissionsReady) {
            enableKioskMode()
        } else {
            // 权限未准备好时，只启用基础的全屏模式
            enableBasicFullscreen()
        }
    }

    private fun enableKioskMode() {
        Log.d(TAG, "Enabling full kiosk mode...")
        // 启用所有保护机制
        fullscreenManager.enableFullscreen()
        kioskModeManager.enableKioskMode()
        homeKeyInterceptor.startIntercepting()
        fullscreenManager.onResume()

        // 启动看门狗服务
        startService(Intent(this, KioskWatchdogService::class.java))
    }

    private fun enableBasicFullscreen() {
        Log.d(TAG, "Enabling basic fullscreen mode (no kiosk restrictions)...")
        // 只启用全屏模式，不启用Kiosk限制
        fullscreenManager.enableFullscreen()
        fullscreenManager.onResume()

        // 不启动看门狗服务，不启用Home键拦截
        // 这样权限对话框可以正常显示
    }

    private fun temporarilyDisableKioskMode() {
        Log.d(TAG, "Temporarily disabling kiosk mode for permission request...")

        // 停止看门狗服务
        stopService(Intent(this, KioskWatchdogService::class.java))

        // 禁用Kiosk限制
        if (::kioskModeManager.isInitialized) {
            kioskModeManager.disableKioskMode()
        }

        // 停止Home键拦截
        if (::homeKeyInterceptor.isInitialized) {
            homeKeyInterceptor.stopIntercepting()
        }

        // 保持基础全屏模式
        enableBasicFullscreen()
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

        // 检查是否是因为配置更改（如语言切换）导致的Activity重建
        if (isChangingConfigurations) {
            Log.d(TAG, "MainActivity onDestroy - 因配置更改重建，保持Terminal连接")
            TerminalConnectionManager.setPausedForConfigurationChange(true)
        } else {
            Log.d(TAG, "MainActivity onDestroy - 应用退出，完全断开Terminal连接")
            // 完全断开Terminal连接
            TerminalConnectionManager.disconnect()
        }

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

    /**
     * 检查和请求权限
     */
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Starting permission check...")

        // 首先尝试系统级权限管理
        if (SystemPermissionManager.isSystemApp(this)) {
            Log.d(TAG, "Detected system app, using system permission management")

            val systemPermissionsReady = SystemPermissionManager.initializeSystemPermissions(this)

            if (systemPermissionsReady) {
                Log.d(TAG, "System permissions initialized successfully")
                onPermissionsReady()
                return
            } else {
                Log.w(TAG, "System permission initialization failed, falling back to regular permissions")
            }
        }

        // 回退到常规权限管理
        Log.d(TAG, "Using regular permission management")

        // 打印详细的权限状态报告
        Log.d(TAG, PermissionManager.getPermissionReport(this))

        val missingPermissions = PermissionManager.getMissingPermissions(this)
        Log.d(TAG, "Missing permissions count: ${missingPermissions.size}")
        Log.d(TAG, "Missing permissions: ${missingPermissions.joinToString()}")

        if (missingPermissions.isEmpty()) {
            // 所有权限已授予，检查GPS
            Log.d(TAG, "All permissions granted, checking GPS...")
            checkGpsStatus()
        } else {
            // 请求缺失的权限前，暂时禁用Kiosk模式
            Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
            temporarilyDisableKioskMode()
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    /**
     * 处理权限请求结果
     */
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()

        Log.d(TAG, "Permission request result:")
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "  $permission: ${if (granted) "GRANTED" else "DENIED"}")
        }

        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All requested permissions granted")
            checkGpsStatus()
        } else {
            Log.w(TAG, "Some permissions denied: ${deniedPermissions.joinToString()}")
            showPermissionDeniedDialog(deniedPermissions)
        }
    }

    /**
     * 检查GPS状态
     */
    private fun checkGpsStatus() {
        if (!PermissionManager.isGpsEnabled(this)) {
            showGpsRequiredDialog()
        } else {
            Log.d(TAG, "All permissions and GPS ready")
            // 权限和GPS都准备好了，可以正常使用Terminal功能
            onPermissionsReady()
        }
    }

    /**
     * 权限准备完成
     */
    private fun onPermissionsReady() {
        Log.d(TAG, "Permissions ready, Terminal functionality available")
        permissionsReady = true

        // 现在可以安全地启用Kiosk模式
        enableKioskMode()

        // 打印权限状态报告
        Log.d(TAG, PermissionManager.getPermissionReport(this))

        // 预初始化Terminal
        preInitializeTerminal()
    }
    
    /**
     * 预初始化Terminal
     */
    private fun preInitializeTerminal() {
        if (PermissionManager.isTerminalReady(this)) {
            Log.d(TAG, "开始预初始化Terminal")
            // 使用TerminalConnectionManager初始化Terminal
            TerminalConnectionManager.initializeIfNeeded(this, object : StripeTerminalManager.TerminalStateListener {
                override fun onDisplayStateChanged(displayState: DisplayState, vararg message: Any?) {
                    // 在MainActivity中我们只关心初始化是否成功，不更新UI
                    Log.d(TAG, "Terminal状态更新: $displayState")
                    
                    // 只要连接成功就停止监听
                    if (TerminalConnectionManager.hasActiveConnection()) {
                        Log.d(TAG, "Terminal预初始化完成，已连接")
                        // 可以在这里添加一些预初始化完成的处理逻辑
                    }
                }
                
                override fun onProgressTimerReset() {
                    // 不需要处理
                }
                
                override fun onProgressTimerResetTo10Minutes() {
                    // 不需要处理
                }
                
                override fun onRestartPayment() {
                    // 不需要处理
                }
            })
        } else {
            Log.d(TAG, "Terminal未准备好，跳过预初始化")
        }
    }

    /**
     * 显示权限被拒绝的对话框
     */
    private fun showPermissionDeniedDialog(deniedPermissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("权限需求")
            .setMessage("应用需要以下权限才能正常工作：\n\n${deniedPermissions.joinToString("\n")}\n\n请在设置中手动授予这些权限。")
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("稍后") { _, _ ->
                // 用户选择稍后，可以继续使用应用的其他功能
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 显示GPS需求对话框
     */
    private fun showGpsRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("GPS需求")
            .setMessage("Terminal支付功能需要启用GPS定位服务。\n\n请在设置中开启位置服务。")
            .setPositiveButton("去设置") { _, _ ->
                openLocationSettings()
            }
            .setNegativeButton("稍后") { _, _ ->
                // 用户选择稍后，可以继续使用应用的其他功能
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 打开应用设置
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.fromParts("package", packageName, null)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开位置设置
     */
    private fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open location settings", e)
            Toast.makeText(this, "无法打开位置设置", Toast.LENGTH_SHORT).show()
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

    /**
     * 设置调试功能
     */
    private fun setupDebugFeatures() {
        // 只在调试模式下启用长按日志功能
        if (ConfigLoader.enableDebug) {
            val priceInfoText = findViewById<TextView>(R.id.tv_price_info)
            priceInfoText.setOnLongClickListener {
                Log.d(TAG, "=== 应用配置信息 ===")
                Log.d(TAG, "API URL: ${ConfigLoader.apiUrl}")
                Log.d(TAG, "QR Code URL: ${ConfigLoader.qrCodeUrl}")
                Log.d(TAG, "Secret Key: ${ConfigLoader.secretKey}")
                Log.d(TAG, "IMEI: ${ConfigLoader.imei}")
                Log.d(TAG, "Currency: ${ConfigLoader.currency}")
                Log.d(TAG, "Brand Name: ${ConfigLoader.brandName}")
                Log.d(TAG, "Debug Mode: ${ConfigLoader.enableDebug}")
                Log.d(TAG, "==================")
                true
            }
        }
    }
    
    /**
     * 设置品牌名称
     */
    private fun setupBrandName() {
        try {
            val brandNameTextView = findViewById<TextView>(R.id.tv_brand_name)
            brandNameTextView.text = ConfigLoader.brandName
        } catch (e: Exception) {
            Log.e(TAG, "设置品牌名称时出错", e)
        }
    }

    /**
     * 获取设备信息和QR码
     */
    private fun getDeviceInfoAndQrCode() {
        Log.d(TAG, "=== 开始获取设备信息和QR码 ===")

        // 异步获取设备信息
        Thread {
            try {
                Log.d(TAG, "开始获取设备IMEI...")
                val imei = getDeviceImei()
                Log.d(TAG, "Device IMEI: $imei")

                if (imei.isNotEmpty()) {
                    Log.d(TAG, "开始调用API获取QR码，IMEI: $imei")

                    // 根据IMEI获取QR码
                    val qrCode = MyApiClient.getQrCode(imei)
                    Log.d(TAG, "API调用完成，返回的QR码: $qrCode")

                    if (qrCode != null && qrCode.isNotEmpty()) {
                        // 存储QR码信息
                        PreferenceManager.setQrCode(qrCode)
                        PreferenceManager.setDeviceSno(qrCode)

                        Log.d(TAG, "QR码保存成功: $qrCode")

                        // 获取位置ID
                        getLocationId(qrCode)
                    } else {
                        Log.w(TAG, "从服务器获取QR码失败，返回值为空")
                    }
                } else {
                    Log.w(TAG, "获取设备IMEI失败，IMEI为空")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取设备信息和QR码时发生异常", e)
            }
        }.start()

        Log.d(TAG, "已启动异步线程获取设备信息")
    }

    /**
     * 获取设备IMEI（使用ConfigLoader中已加载的IMEI）
     */
    private fun getDeviceImei(): String {
        return try {
            // 首先使用ConfigLoader中已经加载的IMEI
            val configImei = ConfigLoader.imei
            if (configImei.isNotEmpty() && configImei != "123456") {
                Log.d(TAG, "使用ConfigLoader中的IMEI: $configImei")
                return configImei
            }

            // 如果ConfigLoader中的IMEI是默认值，尝试重新从文件读取
            Log.d(TAG, "ConfigLoader中的IMEI是默认值，尝试重新读取devinfo.txt")
            val devinfoFile = java.io.File("/sdcard/devinfo.txt")
            if (devinfoFile.exists() && devinfoFile.canRead()) {
                val content = devinfoFile.readText().trim()
                Log.d(TAG, "从devinfo.txt读取到内容: $content")

                if (content.isNotEmpty()) {
                    Log.d(TAG, "使用devinfo.txt中的IMEI: $content")
                    return content
                }
            } else {
                Log.w(TAG, "devinfo.txt文件不存在或不可读: /sdcard/devinfo.txt")
            }

            // 备用方案：使用Android ID
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            Log.d(TAG, "使用Android ID作为备用: $androidId")
            return androidId ?: ""

        } catch (e: Exception) {
            Log.e(TAG, "获取IMEI时发生异常", e)
            // 最终备用方案：使用Android ID
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        }
    }

    /**
     * 获取位置ID
     */
    private fun getLocationId(qrCode: String) {
        Thread {
            try {
                val result = MyApiClient.getLocationId(qrCode)
                if (result?.code == 200 && result.data != null) {
                    val locationId = result.data as String
                    PreferenceManager.setLocationId(locationId)
                    Log.d(TAG, "Location ID saved: $locationId")
                } else {
                    Log.w(TAG, "Failed to get location ID: ${result?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location ID", e)
            }
        }.start()
    }

    /**
     * 预生成二维码（提升性能）
     */
    private fun preGenerateQRCodes() {
        Thread {
            try {
                Log.d(TAG, "开始预生成二维码...")
                val qrCode = PreferenceManager.getQrCode()
                if (!qrCode.isNullOrEmpty()) {
                    // 处理URL
                    val processedUrl = QRCodeUrlProcessor.processQrCodeUrl(ConfigLoader.qrCodeUrl)
                    val fullQRCodeContent = QRCodeUrlProcessor.generateQRCodeContent(ConfigLoader.qrCodeUrl, qrCode)
                    Log.d(TAG, "处理后的URL: $fullQRCodeContent")

                    // 预生成二维码
                    val qrCodeBitmap = OptimizedQRGenerator.generateQRCode(fullQRCodeContent, 800, "WHITE_BORDERED")
                    if (qrCodeBitmap != null) {
                        Log.d(TAG, "二维码预生成成功")
                        // 将生成的二维码存储到缓存管理器中
                        QRCodeCacheManager.setCachedQRCode(qrCodeBitmap, processedUrl, qrCode, fullQRCodeContent)
                    } else {
                        Log.w(TAG, "二维码预生成失败")
                    }
                } else {
                    Log.w(TAG, "QR码为空，跳过二维码预生成")
                }
            } catch (e: Exception) {
                Log.e(TAG, "预生成二维码时出错", e)
            }
        }.start()
    }

    /**
     * 更新主页价格信息
     */
    private fun updatePriceInfo() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val chargeRule = ChargeRuleManager.getChargeRule(this@MainActivity)
                if (chargeRule != null) {
                    val priceInfoText = findViewById<TextView>(R.id.tv_price_info)
                    val priceText = ChargeRuleManager.formatPrice(chargeRule.oneMoneyUnit)
                    val timeText = "${chargeRule.hourUnit} Min"
                    priceInfoText.text = "$priceText / $timeText"
                    Log.d(TAG, "主页价格信息更新成功: $priceText / $timeText")
                } else {
                    Log.w(TAG, "无法获取充电规则，使用默认价格信息")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新主页价格信息时出错", e)
            }
        }
    }
    
    /**
     * 显示Terminal未准备好的对话框
     */
    private fun showTerminalNotReadyDialog() {
        val missingPermissions = PermissionManager.getMissingPermissions(this, PermissionManager.TERMINAL_PERMISSIONS)
        val gpsEnabled = PermissionManager.isGpsEnabled(this)
        
        val errorMessage = buildString {
            if (missingPermissions.isNotEmpty()) {
                append("缺少权限: ${missingPermissions.joinToString()}")
            }
            if (!gpsEnabled) {
                if (isNotEmpty()) append("\n")
                append("GPS未启用")
            }
            if (isEmpty()) {
                append("Terminal初始化失败")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("Terminal未准备好")
            .setMessage(errorMessage)
            .setPositiveButton("确定", null)
            .show()
    }
    
    /**
     * 显示Terminal未连接的对话框
     */
    private fun showTerminalNotConnectedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Terminal未连接")
            .setMessage("阅读器未连接，请检查设备连接并稍后重试。")
            .setPositiveButton("确定", null)
            .show()
    }
}