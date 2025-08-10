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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.stwpower.powertap.core.kiosk.HomeKeyInterceptor
import com.stwpower.powertap.R
import com.stwpower.powertap.config.ConfigLoader
import com.stwpower.powertap.utils.OptimizedQRGenerator
import com.stwpower.powertap.managers.PreferenceManager
import com.stwpower.powertap.utils.QRCodeUrlProcessor
import kotlinx.coroutines.*

class AppPaymentActivity : AppCompatActivity() {

    private lateinit var backButton: Button
    private lateinit var qrCodeImage: ImageView
    private lateinit var qrCodeText: TextView
    private lateinit var progressTimer: HighPerformanceProgressBar

    // QR码加载相关
    private lateinit var qrLoadingLayout: LinearLayout
    private lateinit var qrLoadingProgress: ProgressBar
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private var isProcessing = true
    private var countDownTimer: CountDownTimer? = null
    private val timeoutDuration = 60000L // 60秒

    // 二维码相关
    private var currentQRCodeUrl: String = ""
    private var currentQRCode: String = ""
    private var currentQRCodeContent: String = ""
    private var qrCodeJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("AppPayment", "AppPaymentActivity onCreate started")
        super.onCreate(savedInstanceState)

        try {
            // 设置全屏
            Log.d("AppPayment", "Setting up fullscreen")
            setupFullscreen()

            // 初始化Home键拦截器
            Log.d("AppPayment", "Initializing home key interceptor")
            homeKeyInterceptor = HomeKeyInterceptor(this)

            Log.d("AppPayment", "Setting content view")
            setContentView(R.layout.activity_app_payment)

            Log.d("AppPayment", "Setting up views")
            setupViews()

            Log.d("AppPayment", "Starting countdown")
            startSmoothCountdown()

            Log.d("AppPayment", "Showing initial loading state")
            showQRCodeLoading()

            Log.d("AppPayment", "Generating QR code")
            generateQRCode()

            Log.d("AppPayment", "Simulating payment process")
            simulatePaymentProcess()

            Log.d("AppPayment", "AppPaymentActivity onCreate completed successfully")
        } catch (e: Exception) {
            Log.e("AppPayment", "Error in AppPaymentActivity onCreate", e)
            finish()
        }
    }
    
    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        qrCodeImage = findViewById(R.id.iv_qr_code)
        qrCodeText = findViewById(R.id.tv_qr_code_content)
        progressTimer = findViewById(R.id.progress_timer)

        // QR码加载相关
        qrLoadingLayout = findViewById(R.id.qr_loading_layout)
        qrLoadingProgress = findViewById(R.id.qr_loading_progress)

        // 为弱设备启用高性能模式
        progressTimer.setHighPerformanceMode(true)

        // 设置圆角背景
        setRoundedBackground(backButton, Color.parseColor("#868D91"), 10f)

        backButton.isEnabled = false
        backButton.alpha = 0.5f

        backButton.setOnClickListener {
            if (!isProcessing) {
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

    /**
     * 显示QR码加载状态
     */
    private fun showQRCodeLoading() {
        Log.d("AppPayment", "显示QR码加载状态")
        // 二维码区域显示加载环（无文字）
        qrLoadingLayout.visibility = View.VISIBLE
        qrCodeImage.visibility = View.GONE

        // 下方文字显示loading说明
        qrCodeText.text = "loading"
    }

    /**
     * 隐藏QR码加载状态，显示二维码
     */
    private fun hideQRCodeLoading() {
        Log.d("AppPayment", "隐藏QR码加载状态，显示二维码")
        // 隐藏加载环，显示二维码
        qrLoadingLayout.visibility = View.GONE
        qrCodeImage.visibility = View.VISIBLE
        // qrCodeText会在生成成功后更新为实际的qrCode内容
    }

    private fun generateQRCode() {
        // 获取qrCodeUrl和qrCode
        val rawQrCodeUrl = ConfigLoader.qrCodeUrl
        val qrCode = PreferenceManager.getQrCode()

        // 检查必要参数
        if (rawQrCodeUrl.isEmpty() || qrCode.isNullOrEmpty()) {
            Log.w("AppPayment", "qrCodeUrl或qrCode为空，无法生成二维码")
            // 保持loading状态，下方显示loading文字
            qrCodeText.text = "loading"
            return
        }

        // 使用工具类处理qrCodeUrl和生成完整内容
        val qrCodeUrl = QRCodeUrlProcessor.processQrCodeUrl(rawQrCodeUrl)
        val fullQRCodeContent = QRCodeUrlProcessor.generateQRCodeContent(rawQrCodeUrl, qrCode)

        // 检查是否需要更新二维码（任一组成部分发生变化）
        if (qrCodeUrl == currentQRCodeUrl &&
            qrCode == currentQRCode &&
            fullQRCodeContent == currentQRCodeContent) {
            Log.d("AppPayment", "二维码内容未变化，跳过生成")
            // 如果内容未变化，直接隐藏加载状态
            hideQRCodeLoading()
            return
        }

        Log.d("AppPayment", "二维码内容发生变化，开始生成新的二维码")
        Log.d("AppPayment", "变化详情:")
        Log.d("AppPayment", "  qrCodeUrl: $currentQRCodeUrl -> $qrCodeUrl")
        Log.d("AppPayment", "  qrCode: $currentQRCode -> $qrCode")
        Log.d("AppPayment", "  完整内容: $currentQRCodeContent -> $fullQRCodeContent")

        // 取消之前的二维码生成任务
        qrCodeJob?.cancel()

        // 异步生成二维码
        qrCodeJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // 在后台线程生成二维码
                val bitmap = withContext(Dispatchers.IO) {
                    OptimizedQRGenerator.generateQRCodeAsync(
                        content = fullQRCodeContent,
                        size = 400,
                        style = "WHITE_BORDERED"
                    )
                }

                // 检查bitmap是否生成成功
                if (bitmap != null && !bitmap.isRecycled) {
                    // 隐藏加载状态，显示二维码
                    hideQRCodeLoading()

                    // 在主线程更新UI
                    qrCodeImage.setImageBitmap(bitmap)
                    qrCodeText.text = qrCode ?: "" // 显示qrCode部分，安全处理null

                    // 更新当前状态
                    currentQRCodeUrl = qrCodeUrl
                    currentQRCode = qrCode ?: ""
                    currentQRCodeContent = fullQRCodeContent

                    Log.d("AppPayment", "二维码生成并显示完成")
                } else {
                    Log.e("AppPayment", "二维码bitmap生成失败")
                    // 保持loading状态，下方显示loading文字
                    qrCodeText.text = "loading"
                }

            } catch (e: Exception) {
                Log.e("AppPayment", "生成二维码失败", e)
                // 保持loading状态，下方显示loading文字
                qrCodeText.text = "loading"
            }
        }
    }


    
    private fun simulatePaymentProcess() {
        Handler(Looper.getMainLooper()).postDelayed({
            isProcessing = false
            backButton.isEnabled = true
            backButton.alpha = 1.0f
        }, 0) // 1秒后完成
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

        // 取消二维码生成任务
        qrCodeJob?.cancel()

        Log.d("AppPayment", "AppPaymentActivity destroyed")
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
}