package com.stwpower.powertap

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TerminalPaymentActivity : AppCompatActivity() {
    
    private lateinit var backButton: Button
    private lateinit var statusText: TextView
    private lateinit var instructionsText: TextView
    private var isProcessing = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏
        setupFullscreen()

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
        
        // 模拟支付处理过程，5秒后完成
        Handler(Looper.getMainLooper()).postDelayed({
            isProcessing = false
            backButton.isEnabled = true
            backButton.alpha = 1.0f
            statusText.text = getString(R.string.payment_completed)
        }, 5000) // 5秒后完成支付
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

    override fun onBackPressed() {
        // 禁用返回键，只能通过Back按钮返回
        // 不调用 super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullscreen()
        }
    }
}
