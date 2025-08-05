package com.stwpower.powertap

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class AppPaymentActivity : AppCompatActivity() {
    
    private lateinit var backButton: Button
    private lateinit var statusText: TextView
    private lateinit var qrCodeImage: ImageView
    private var isProcessing = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏
        setupFullscreen()

        setContentView(R.layout.activity_app_payment)

        setupViews()
        generateQRCode()
        simulatePaymentProcess()
    }
    
    private fun setupViews() {
        backButton = findViewById(R.id.btn_back)
        statusText = findViewById(R.id.tv_status)
        qrCodeImage = findViewById(R.id.iv_qr_code)
        
        backButton.isEnabled = false
        backButton.alpha = 0.5f
        
        backButton.setOnClickListener {
            if (!isProcessing) {
                finish()
            }
        }
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
        }, 5000) // 5秒后完成
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