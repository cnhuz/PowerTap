package com.stwpower.powertap

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class AppPaymentActivity : AppCompatActivity() {

    private lateinit var backButton: Button
    private lateinit var statusText: TextView
    private lateinit var qrCodeImage: ImageView
    private lateinit var progressTimer: HighPerformanceProgressBar
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private var isProcessing = true
    private var countDownTimer: CountDownTimer? = null
    private val timeoutDuration = 60000L // 60秒
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏
        setupFullscreen()

        // 初始化Home键拦截器
        homeKeyInterceptor = HomeKeyInterceptor(this)

        setContentView(R.layout.activity_app_payment)

        setupViews()
        startSmoothCountdown()
        generateQRCode()
        simulatePaymentProcess()
    }
    
    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        statusText = findViewById(R.id.tv_status)
        qrCodeImage = findViewById(R.id.iv_qr_code)
        progressTimer = findViewById(R.id.progress_timer)

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
    
    private fun generateQRCode() {
        val qrCodeContent = "powertap://payment?id=12345&amount=5.00"
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrCodeContent, BarcodeFormat.QR_CODE, 300, 300)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        
        qrCodeImage.setImageBitmap(bitmap)
    }
    
    private fun simulatePaymentProcess() {
        statusText.text = getString(R.string.scan_qr_code)
        
        Handler(Looper.getMainLooper()).postDelayed({
            isProcessing = false
            backButton.isEnabled = true
            backButton.alpha = 1.0f
            statusText.text = getString(R.string.payment_completed)
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