package com.stwpower.powertap

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupLanguageButtons()
        setupPaymentButtons()
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
}