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
import com.stwpower.powertap.terminal.TerminalConnectionManager
import com.stwpower.powertap.terminal.DisplayState
import com.stwpower.powertap.terminal.UIType
import com.stwpower.powertap.utils.PermissionManager
import com.stwpower.powertap.utils.PreferenceManager

class TerminalPaymentActivity : AppCompatActivity(), StripeTerminalManager.TerminalStateListener {

    private lateinit var backButton: Button
    private lateinit var progressTimer: HighPerformanceProgressBar
    private lateinit var loadingLayout: LinearLayout
    private lateinit var completedLayout: LinearLayout
    private lateinit var messageLayout: LinearLayout
    private lateinit var loadingText: TextView
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor

    // 消息显示相关
    private lateinit var messageText: TextView

    // 消息延迟清除任务
    private var messageDelayHandler: Handler? = null
    private var messageDelayRunnable: Runnable? = null
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
        Log.d("TerminalPayment", "显示初始加载状态")
        showLoadingState()
        loadingText.text = "正在初始化..."

        Log.d("TerminalPayment", "开始倒计时")
        startSmoothCountdown()

        // 使用TerminalConnectionManager获取Terminal管理器
        Log.d("TerminalPayment", "获取Terminal管理器")
        terminalManager = TerminalConnectionManager.getTerminalManager(this, this)

        // 检查是否是Activity重建
        if (savedInstanceState != null) {
            Log.d("TerminalPayment", "Activity重建，恢复状态")
            // 如果Terminal已经在运行，不要重新初始化
            val terminalManager = TerminalConnectionManager.getTerminalManager(this, this)
            if (terminalManager.isPaymentInProgress()) {
                Log.d("TerminalPayment", "Terminal支付正在进行中，恢复监听")
                this.terminalManager = terminalManager
                return
            }
        }

        // 检查权限状态并初始化Terminal
        Log.d("TerminalPayment", "检查权限并初始化Terminal")
        checkPermissionsAndInitialize()
    }
    
    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        progressTimer = findViewById(R.id.progress_timer)
        loadingLayout = findViewById(R.id.loading_layout)
        completedLayout = findViewById(R.id.completed_layout)
        messageLayout = findViewById(R.id.message_layout)
        loadingText = findViewById(R.id.loading_text)

        // 消息显示相关
        messageText = findViewById(R.id.message_text)

        // 为弱设备启用高性能模式
        progressTimer.setHighPerformanceMode(true)

        // 设置圆角背景
        setRoundedBackground(backButton, Color.parseColor("#868D91"), 10f)

        // 初始状态：按钮禁用，显示为灰色
        backButton.isEnabled = false
        backButton.alpha = 0.5f

        backButton.setOnClickListener {
            if (!isProcessing) {
                // 根据状态管理逻辑：离开terminal页面需要主动取消收集付款方式
                Log.d("TerminalPayment", "用户主动离开Terminal页面，取消收集付款方式")

                // 设置用户离开标识，防止自动重新进入收集付款方式
                terminalManager.setUserLeftTerminalPage(true)

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
        Log.d("TerminalPayment", "开始倒计时，取消之前的倒计时器")
        countDownTimer?.cancel()

        // 使用优化的更新频率，在性能和丝滑度之间取得平衡
        // 对于弱设备，使用33ms间隔（约30fps）既保证丝滑又节省性能
        countDownTimer = object : CountDownTimer(timeoutDuration, 33) {
            override fun onTick(millisUntilFinished: Long) {
                // 计算当前进度百分比
                val progress = (millisUntilFinished.toFloat() / timeoutDuration) * 100f
                progressTimer.setProgress(progress)
            }

            override fun onFinish() {
                Log.d("TerminalPayment", "倒计时结束，返回主页面")
                // 确保进度条到0，然后返回主页面
                progressTimer.setProgress(0f)
                returnToMainActivity()
            }
        }
        countDownTimer?.start()
        Log.d("TerminalPayment", "倒计时器已启动，持续时间: ${timeoutDuration}ms")
    }

    private fun returnToMainActivity() {
        Log.d("TerminalPayment", "=== 准备返回主页面 ===")
        Log.d("TerminalPayment", "调用堆栈: ${Thread.currentThread().stackTrace.take(5).joinToString("\n")}")

        // 设置用户离开标识，防止自动重新进入收集付款方式
        if (::terminalManager.isInitialized) {
            terminalManager.setUserLeftTerminalPage(true)
        }

        // 时间到后返回主页面，使用简单的finish()即可
        // 因为MainActivity应该还在任务栈中
        finish()
    }

    /**
     * 重置60s的进度条
     * 用于支付失败或租借失败后重新进入收集付款方式
     */
    private fun restartPaymentTimer() {
        Log.d("TerminalPayment", "重置进度条为60秒")
        countDownTimer?.cancel()

        // 重新开始60秒倒计时
        countDownTimer = object : CountDownTimer(timeoutDuration, 33) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (millisUntilFinished.toFloat() / timeoutDuration) * 100f
                progressTimer.setProgress(progress)
            }

            override fun onFinish() {
                progressTimer.setProgress(0f)
                returnToMainActivity()
            }
        }
        countDownTimer?.start()

        // 重新开始收集付款方式
        Handler(Looper.getMainLooper()).postDelayed({
            restartPaymentCollection()
        }, 2000) // 2秒后重新开始
    }



    /**
     * 重新开始收集付款方式
     */
    private fun restartPaymentCollection() {
        Log.d("TerminalPayment", "重新开始收集付款方式")

        // 重置UI状态
        isProcessing = true
        backButton.isEnabled = false
        backButton.alpha = 0.5f
        showLoadingState()

        // 重新开始收集付款方式
        terminalManager.resumePaymentCollection()
    }

    /**
     * 重置进度条为20秒
     * 用于租借失败后显示错误信息
     */
    private fun resetProgressTimerTo20Seconds() {
        Log.d("TerminalPayment", "重置进度条为20秒")
        countDownTimer?.cancel()

        // 重新开始20秒倒计时
        countDownTimer = object : CountDownTimer(20000L, 33) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (millisUntilFinished.toFloat() / 20000L) * 100f
                progressTimer.setProgress(progress)
            }

            override fun onFinish() {
                progressTimer.setProgress(0f)
                returnToMainActivity()
            }
        }
        countDownTimer?.start()
    }

    /**
     * 统一UI更新监听器实现
     *
     * 完整调用链：
     * StripeTerminalManager.updateDisplayState（业务层统一入口）
     *    ↓
     * StripeStateManager.updateDisplayState（状态层存储和通知）
     *    ↓
     * StripeStateManager.StripeStateListener.onDisplayStateChanged（状态层监听器接口）
     *    ↓
     * StripeTerminalManager匿名监听器（业务层监听器实现（init阶段），负责转发）
     *    ↓
     * TerminalPaymentActivity.onDisplayStateChanged（UI层监听器实现）
     *    ↓
     * updateUIForDisplayState（最终UI更新执行）
     */
    override fun onDisplayStateChanged(displayState: DisplayState) {
        runOnUiThread {
            updateUIForDisplayState(displayState)
        }
    }

    // 进度条重置监听器实现
    override fun onProgressTimerReset() {
        runOnUiThread {
            // 统一重置进度条为20秒
            Log.d("TerminalPayment", "收到进度条重置信号，重置为20秒")
            resetProgressTimerTo20Seconds()
        }
    }

    // 重试进入收集付款方式监听器实现
    override fun onRestartPayment() {
        runOnUiThread {
            // 统一重置进度条为60秒
            Log.d("TerminalPayment", "收到重试进入收集付款方式信号，重置为60秒并重试")
            restartPaymentTimer()
        }
    }


    // 根据DisplayState更新UI
    private fun updateUIForDisplayState(displayState: DisplayState) {
        Log.d("TerminalPayment", "更新UI为状态: $displayState (UIType: ${displayState.uiType})")

        // 根据UIType决定UI展示方式
        when (displayState.uiType) {
            UIType.LOADING -> {
                // Type 1: 进度环+文字
                showLoadingState()
                loadingText.text = displayState.getFormattedText(this)
                loadingText.setTextColor(getColor(R.color.text_primary))

                isProcessing = true
                backButton.isEnabled = false
                backButton.alpha = 0.5f
            }

            UIType.TAP_TO_PAY -> {
                // Type 2: 文字+图片
                showCompletedState()
                loadingText.text = displayState.getFormattedText(this)

                // 等待刷卡状态显示蓝色文本
                if (displayState == DisplayState.WAITING_FOR_CARD) {
                    loadingText.setTextColor(getColor(android.R.color.holo_blue_dark))
                } else {
                    loadingText.setTextColor(getColor(R.color.text_primary))
                }

                isProcessing = false
                backButton.isEnabled = displayState.canGoBack
                backButton.alpha = if (displayState.canGoBack) 1.0f else 0.5f
            }

            UIType.MESSAGE -> {
                // Type 3: 只展示白色粗体文字
                showMessage(displayState.getFormattedText(this))

                isProcessing = false
                backButton.isEnabled = displayState.canGoBack
                backButton.alpha = if (displayState.canGoBack) 1.0f else 0.5f
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
        messageLayout.visibility = View.GONE
    }

    /**
     * 显示消息状态
     */
    private fun showMessageState() {
        loadingLayout.visibility = View.GONE
        completedLayout.visibility = View.GONE
        messageLayout.visibility = View.VISIBLE
    }

    /**
     * 显示消息提示 - 统一白色粗体样式
     * @param message 消息文字
     */
    private fun showMessage(message: String) {
        showMessageState()

        // 设置消息文字 - 统一白色粗体样式
        messageText.text = message
        messageText.setTextColor(getColor(android.R.color.white))
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

        // 通知TerminalManager用户重新进入了Terminal页面
        if (::terminalManager.isInitialized) {
            terminalManager.onUserEnteredTerminalPage()
        }
    }

    override fun onPause() {
        Log.d("TerminalPayment", "=== onPause 被调用 ===")
        super.onPause()
        // 不要停止拦截，保持监控

        // 通知TerminalManager用户离开了Terminal页面
        if (::terminalManager.isInitialized) {
            terminalManager.setUserLeftTerminalPage(true)
        }
    }

    override fun onStop() {
        Log.d("TerminalPayment", "=== onStop 被调用 ===")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d("TerminalPayment", "=== onDestroy 被调用 ===")
        Log.d("TerminalPayment", "isFinishing: $isFinishing")
        Log.d("TerminalPayment", "调用堆栈: ${Thread.currentThread().stackTrace.take(5).joinToString("\n")}")

        super.onDestroy()
        countDownTimer?.cancel()
        homeKeyInterceptor.stopIntercepting()

        // 清理消息延迟任务（如果有的话）
        messageDelayRunnable?.let { messageDelayHandler?.removeCallbacks(it) }

        // 如果不是正常finish，说明Activity被系统意外销毁
        if (!isFinishing) {
            Log.w("TerminalPayment", "Activity被系统意外销毁，但用户可能还在Terminal页面")
            // 不要暂停支付收集，让Terminal继续运行
            // 用户可能还期望Terminal功能继续工作
        } else {
            Log.d("TerminalPayment", "Activity正常结束，暂停支付收集")
            // 暂停支付收集但保持阅读器连接
            TerminalConnectionManager.pausePaymentCollection()
        }

        Log.d("TerminalPayment", "TerminalPaymentActivity destroyed, connection maintained")
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

        // 打印Terminal连接状态报告
        Log.d("TerminalPayment", TerminalConnectionManager.getStatusReport())

        if (PermissionManager.isTerminalReady(this)) {
            // 权限和GPS都准备好了，使用TerminalConnectionManager初始化
            Log.d("TerminalPayment", "Terminal ready, initializing via TerminalConnectionManager...")
            TerminalConnectionManager.initializeIfNeeded(this, this)
        } else {
            // 权限或GPS未准备好，通过TerminalManager更新状态
            Log.w("TerminalPayment", "Terminal not ready, updating state through TerminalManager")
            if (::terminalManager.isInitialized) {
                // 通过统一入口更新状态
                terminalManager.updateDisplayState(DisplayState.INITIALIZATION_FAILED)
            } else {
                // 如果TerminalManager还没初始化，直接更新UI（这种情况很少见）
                updateUIForDisplayState(DisplayState.INITIALIZATION_FAILED)
            }

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
