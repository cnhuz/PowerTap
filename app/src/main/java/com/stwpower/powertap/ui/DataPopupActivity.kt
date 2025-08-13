package com.stwpower.powertap.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.stwpower.powertap.R

class DataPopupActivity : Activity() {
    
    companion object {
        private const val TAG = "DataPopupActivity"
        const val EXTRA_DATA_LIST = "extra_data_list"
        private const val AUTO_CLOSE_DELAY: Long = 10000 // 10秒后自动关闭
    }
    
    private val autoCloseHandler = Handler(Looper.getMainLooper())
    private val autoCloseRunnable = Runnable {
        Log.d(TAG, "弹窗显示10秒后自动关闭")
        finish()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口属性，使Activity以弹窗形式显示
        setupWindowAttributes()
        
        setContentView(R.layout.activity_data_popup)
        
        // 获取传递的数据
        val dataList = intent.getCharSequenceArrayExtra(EXTRA_DATA_LIST)
        
        if (dataList != null) {
            Log.d(TAG, "接收到数据，共${dataList.size}条记录")
            setupViews(dataList)
            // 启动自动关闭定时器
            startAutoCloseTimer()
        } else {
            Log.w(TAG, "未接收到数据")
            finish()
        }
    }
    
    private fun setupWindowAttributes() {
        // 设置窗口背景透明
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // 设置窗口位置居中
        val layoutParams = window.attributes
        layoutParams.gravity = Gravity.CENTER
        layoutParams.width = WindowManager.LayoutParams.MATCH_PARENT
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        window.attributes = layoutParams
        
        // 设置窗口标志，确保弹窗能正确显示
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        
        // 设置窗口输入模式
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED)
    }
    
    private fun setupViews(dataList: Array<CharSequence>) {
        val titleTextView = findViewById<TextView>(R.id.tv_popup_title)
        val contentTextView = findViewById<TextView>(R.id.tv_popup_content)
        val confirmButton = findViewById<Button>(R.id.btn_popup_confirm)
        
        // 设置标题
        titleTextView.text = "电源银行数据 (${dataList.size}条记录)"
        
        // 构建内容文本
        val contentBuilder = StringBuilder()
        for (i in dataList.indices) {
            contentBuilder.append("${i + 1}. ${dataList[i]}\n")
        }
        
        // 设置内容
        contentTextView.text = contentBuilder.toString()
        
        // 设置确认按钮点击事件
        confirmButton.setOnClickListener {
            // 用户点击确认按钮，取消自动关闭并立即关闭
            cancelAutoCloseTimer()
            finish()
        }
    }
    
    /**
     * 启动自动关闭定时器
     */
    private fun startAutoCloseTimer() {
        autoCloseHandler.postDelayed(autoCloseRunnable, AUTO_CLOSE_DELAY)
    }
    
    /**
     * 取消自动关闭定时器
     */
    private fun cancelAutoCloseTimer() {
        autoCloseHandler.removeCallbacks(autoCloseRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保在Activity销毁时取消定时器
        cancelAutoCloseTimer()
    }
}