package com.stwpower.powertap

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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class TerminalPaymentActivity : AppCompatActivity() {

    private lateinit var backButton: Button
    private lateinit var statusText: TextView
    private lateinit var instructionsText: TextView
    private lateinit var homeKeyInterceptor: HomeKeyInterceptor
    private var isProcessing = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏
        setupFullscreen()

        // 初始化Home键拦截器
        homeKeyInterceptor = HomeKeyInterceptor(this)

        setContentView(R.layout.activity_terminal_payment)

        setupViews()
        simulatePaymentProcess()
    }
    
    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        statusText = findViewById(R.id.tv_status)
        instructionsText = findViewById(R.id.tv_instructions)
        
        // 初始状态：按钮禁用，显示为灰色
        backButton.isEnabled = false
        backButton.alpha = 0.5f
        
        backButton.setOnClickListener {
            if (!isProcessing) {
                finish()
            }
        }
    }
    
    private fun simulatePaymentProcess() {
        // 初始状态
        statusText.text = getString(R.string.processing_payment)
        instructionsText.text = getString(R.string.terminal_instructions)
        
        // 模拟支付处理过程，3秒后完成
        Handler(Looper.getMainLooper()).postDelayed({
            isProcessing = false
            backButton.isEnabled = true
            backButton.alpha = 1.0f
            statusText.text = getString(R.string.payment_completed)
        }, 1000) // 3秒后完成支付
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
