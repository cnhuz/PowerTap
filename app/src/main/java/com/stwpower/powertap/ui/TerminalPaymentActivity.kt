package com.stwpower.powertap.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stwpower.powertap.HomeKeyInterceptor
import com.stwpower.powertap.R
import com.stwpower.powertap.terminal.StripeTerminalManager
import com.stwpower.powertap.terminal.TerminalState
import com.stwpower.powertap.utils.PermissionManager
import com.stwpower.powertap.utils.PreferenceManager

class TerminalPaymentActivity : AppCompatActivity(), StripeTerminalManager.TerminalStateListener {

    private lateinit var backButton: Button
    private lateinit var progressTimer: HighPerformanceProgressBar
    private lateinit var loadingLayout: LinearLayout
    private lateinit var completedLayout: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private lateinit var terminalManager: StripeTerminalManager
    private var isProcessing = true
    private var countDownTimer: CountDownTimer? = null
    private val timeoutDuration = 60000L // 60秒
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化PreferenceManager
        PreferenceManager.init(this)

        // 设置全屏
        setupFullscreen()

        // 初始化Home键拦截器
        homeKeyInterceptor = HomeKeyInterceptor(this)

        setContentView(R.layout.activity_terminal_payment)

        setupViews()

        // 显示初始加载状态
        showLoadingState()
        loadingText.text = "正在初始化..."

        startSmoothCountdown()

        // 初始化Terminal管理器
        terminalManager = StripeTerminalManager(this, this)

        // 检查权限状态并初始化Terminal
        checkPermissionsAndInitialize()
    }
    
    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        progressTimer = findViewById(R.id.progress_timer)
        loadingLayout = findViewById(R.id.loading_layout)
        completedLayout = findViewById(R.id.completed_layout)
        loadingText = findViewById(R.id.loading_text)

        // 为弱设备启用高性能模式
        progressTimer.setHighPerformanceMode(true)

        // 设置圆角背景
        setRoundedBackground(backButton, Color.parseColor("#868D91"), 10f)

        // 初始状态：按钮禁用，显示为灰色
        backButton.isEnabled = false
        backButton.alpha = 0.5f

        backButton.setOnClickListener {
            if (!isProcessing) {
                // 取消Terminal操作
                terminalManager.cancel()
                // 取消倒计时器
                countDownTimer?.cancel()
                finish()
            }
        }
    }

    private fun setRoundedBackground(button: Button, color: Int, radius: Float) {
        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius * resources.displayMetrics.density
        button.background = drawable
    }
    
    private fun startSmoothCountdown() {
        // 使用优化的更新频率，在性能和丝滑度之间取得平衡
        // 对于弱设备，使用33ms间隔（约30fps）既保证丝滑又节省性能
        countDownTimer = object : CountDownTimer(timeoutDuration, 33) {
            override fun onTick(millisUntilFinished: Long) {
                // 计算当前进度百分比
                val progress = (millisUntilFinished.toFloat() / timeoutDuration) * 100f
                progressTimer.setProgress(progress)
            }

            override fun onFinish() {
                // 确保进度条到0，然后返回主页面
                progressTimer.setProgress(0f)
                returnToMainActivity()
            }
        }
        countDownTimer?.start()
    }

    private fun returnToMainActivity() {
        // 时间到后返回主页面，使用简单的finish()即可
        // 因为MainActivity应该还在任务栈中
        finish()
    }

    // TerminalStateListener 实现
    override fun onStateChanged(state: TerminalState, progress: Int) {
        runOnUiThread {
            updateUIForState(state, progress)
        }
    }

    override fun onPaymentSuccess(paymentIntent: PaymentIntent) {
        runOnUiThread {
            isProcessing = false
            backButton.isEnabled = true
            backButton.alpha = 1.0f
            showCompletedState()

            // 显示成功消息
            val successMessage = getString(R.string.payment_successful) + "\n" +
                                getString(R.string.payment_completed)
            loadingText.text = successMessage

            // 可以在这里添加成功的视觉效果，比如绿色背景等
            loadingText.setTextColor(getColor(android.R.color.holo_green_dark))
        }
    }

    override fun onPaymentFailed(error: String) {
        runOnUiThread {
            isProcessing = false
            backButton.isEnabled = true
            backButton.alpha = 1.0f
            showCompletedState()

            // 显示错误消息
            val errorMessage = getString(R.string.payment_failed) + "\n$error"
            loadingText.text = errorMessage

            // 添加错误的视觉效果
            loadingText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    private fun updateUIForState(state: TerminalState, progress: Int = 0) {
        // 更新加载文本
        loadingText.text = state.getFormattedText(this, progress)

        // 重置文本颜色
        loadingText.setTextColor(getColor(R.color.text_primary))

        // 根据状态更新UI
        when (state) {
            // 等待刷卡状态 - 这时才算完成加载，显示完成状态
            TerminalState.WAITING_FOR_CARD -> {
                showCompletedState()
                isProcessing = false
                backButton.isEnabled = true
                backButton.alpha = 1.0f
                // 等待刷卡状态显示蓝色文本
                loadingText.setTextColor(getColor(android.R.color.holo_blue_dark))
            }

            // 最终成功状态
            TerminalState.PAYMENT_SUCCESSFUL -> {
                showCompletedState()
                isProcessing = false
                backButton.isEnabled = true
                backButton.alpha = 1.0f
                loadingText.setTextColor(getColor(android.R.color.holo_green_dark))
            }

            // 错误状态
            TerminalState.PAYMENT_FAILED,
            TerminalState.INITIALIZATION_FAILED,
            TerminalState.READER_NOT_FOUND,
            TerminalState.CONNECTION_FAILED,
            TerminalState.PAYMENT_CANCELLED,
            TerminalState.TIMEOUT -> {
                showCompletedState()
                isProcessing = false
                backButton.isEnabled = true
                backButton.alpha = 1.0f
                // 错误状态显示红色文本
                loadingText.setTextColor(getColor(android.R.color.holo_red_dark))
            }

            // 所有其他状态都保持加载状态
            else -> {
                showLoadingState()
                // 加载状态下禁用返回按钮
                if (state.isLoading) {
                    isProcessing = true
                    backButton.isEnabled = false
                    backButton.alpha = 0.5f
                }
            }
        }
    }

    private fun showLoadingState() {
        loadingLayout.visibility = View.VISIBLE
        completedLayout.visibility = View.GONE
    }

    private fun showCompletedState() {
        loadingLayout.visibility = View.GONE
        completedLayout.visibility = View.VISIBLE
    }

    private fun setupFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            controller?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
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

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }

    override fun onBackPressed() {
        // 禁用返回键，只能通过Back按钮返回
        // 不调用 super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        homeKeyInterceptor.startIntercepting()
        // 重新应用全屏设置
        setupFullscreen()
    }

    override fun onPause() {
        super.onPause()
        // 不要停止拦截，保持监控
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        homeKeyInterceptor.stopIntercepting()
        terminalManager.cleanup()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (homeKeyInterceptor.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        }
    }

    /**
     * 检查权限状态并初始化Terminal
     */
    private fun checkPermissionsAndInitialize() {
        Log.d("TerminalPayment", "Checking permissions and initializing...")

        // 打印权限状态报告
        Log.d("TerminalPayment", PermissionManager.getPermissionReport(this))

        if (PermissionManager.isTerminalReady(this)) {
            // 权限和GPS都准备好了，开始初始化Terminal
            Log.d("TerminalPayment", "Terminal ready, initializing...")
            terminalManager.initialize()
        } else {
            // 权限或GPS未准备好，显示错误状态
            Log.w("TerminalPayment", "Terminal not ready, showing error state")
            updateUIForState(TerminalState.INITIALIZATION_FAILED)

            val missingPermissions = PermissionManager.getMissingPermissions(this, PermissionManager.TERMINAL_PERMISSIONS)
            val gpsEnabled = PermissionManager.isGpsEnabled(this)

            Log.d("TerminalPayment", "Missing permissions: ${missingPermissions.joinToString()}")
            Log.d("TerminalPayment", "GPS enabled: $gpsEnabled")

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

            loadingText.text = errorMessage
            Log.d("TerminalPayment", "Error message: $errorMessage")
        }
    }
}
