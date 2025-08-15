package com.stwpower.powertap.ui

import android.app.PendingIntent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.stwpower.powertap.core.kiosk.HomeKeyInterceptor
import com.stwpower.powertap.R
import com.stwpower.powertap.terminal.StripeTerminalManager
import com.stwpower.powertap.terminal.TerminalConnectionManager
import com.stwpower.powertap.terminal.DisplayState
import com.stwpower.powertap.terminal.UIType
import com.stwpower.powertap.managers.PermissionManager
import com.stwpower.powertap.managers.PreferenceManager
import com.stwpower.powertap.utils.ChargeRuleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TerminalPaymentActivity : AppCompatActivity(), StripeTerminalManager.TerminalStateListener {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.stwpower.powertap.USB_PERMISSION"
    }

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
    
    // 价格信息相关视图
    private lateinit var pricePerHourValue: TextView
    private lateinit var pricePerHourText: TextView
    private lateinit var pricePerDayValue: TextView
    private lateinit var pricePerDayText: TextView
    private lateinit var depositValue: TextView
    
    // USB设备监听相关
    private var usbManager: UsbManager? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var isUsbReceiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    
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
//        terminalManager.setUserLeftTerminalPage(false)

        // 注册USB设备连接状态监听器
        registerUsbDeviceListener()

        // 检查是否是Activity重建
        if (savedInstanceState != null) {
            Log.d("TerminalPayment", "Activity重建，恢复状态")
            // 如果Terminal已经在运行，不要重新初始化
            val terminalManager = TerminalConnectionManager.getTerminalManager(this, this)
            this.terminalManager = terminalManager
            
            // 确保视图已经完全加载后再更新UI
            terminalManager.getCurrentDisplayState().let { currentDisplayState ->
                Log.d("TerminalPayment", "Activity重建，当前显示状态: $currentDisplayState")
                // 使用post确保UI更新在布局完成之后执行
                loadingLayout.post {
                    Log.d("TerminalPayment", "Activity重建，延迟更新UI")
                    updateUIForDisplayState(currentDisplayState, null)
                }
            }
            
            return
        }
        updateUIForDisplayState(DisplayState.LOADING)
        // 初始化Terminal
        initializeTerminal()
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
                Log.d("TerminalPayment", "设置用户离开Terminal页面标志")

                // 设置用户离开标识，防止自动重新进入收集付款方式
                if (::terminalManager.isInitialized) {
                    terminalManager.setUserLeftTerminalPage(true)
                }

                terminalManager.cancel()
                // 取消倒计时器
                countDownTimer?.cancel()
                finish()
            }
        }
        
        // 更新价格信息
        updatePriceInfo()
        
        // 注册USB设备监听器
        registerUsbDeviceListener()
    }

    /**
     * 更新价格信息
     */
    private fun updatePriceInfo() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val chargeRule = ChargeRuleManager.getChargeRule(this@TerminalPaymentActivity)
                if (chargeRule != null) {
                    // 更新单位时间价格
                    val pricePerHourValue = findViewById<TextView>(R.id.price_per_hour_value)
                    pricePerHourValue.text = ChargeRuleManager.formatPrice(chargeRule.oneMoneyUnit)
                    
                    // 更新单位时间描述
                    val pricePerHourText = findViewById<TextView>(R.id.price_per_hour_text)
                    pricePerHourText.text = ChargeRuleManager.getPerHourText(chargeRule.oneMoneyUnit, chargeRule.hourUnit)
                    
                    // 更新每天最大金额
                    val pricePerDayValue = findViewById<TextView>(R.id.price_per_day_value)
                    pricePerDayValue.text = ChargeRuleManager.formatPrice(chargeRule.maxPerMoney)
                    
                    // 更新每天价格描述
                    val pricePerDayText = findViewById<TextView>(R.id.price_per_day_text)
                    pricePerDayText.text = ChargeRuleManager.getPerDayText(chargeRule.maxPerMoney)
                    
                    // 更新押金金额
                    val depositValue = findViewById<TextView>(R.id.deposit_value)
                    depositValue.text = ChargeRuleManager.formatPrice(chargeRule.reportLoss)
                    
                    Log.d("TerminalPayment", "价格信息更新成功")
                } else {
                    Log.w("TerminalPayment", "无法获取充电规则，使用默认价格")
                }
            } catch (e: Exception) {
                Log.e("TerminalPayment", "更新价格信息时出错", e)
            }
        }
    }

    /**
     * 注册USB设备监听器
     */
    private fun registerUsbDeviceListener() {
        try {
            // 检查是否已经注册过
            if (isUsbReceiverRegistered) {
                Log.d("TerminalPayment", "USB设备监听器已注册，跳过重复注册")
                return
            }
            
            usbManager = getSystemService(USB_SERVICE) as UsbManager
            Log.d("TerminalPayment", "USB管理器初始化成功")
            
            // 创建USB设备连接状态变化的广播接收器
            usbReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            Log.d("TerminalPayment", "USB设备已连接: ${device?.deviceName}, VendorId: ${device?.vendorId}, ProductId: ${device?.productId}")
                            
                            // 检查是否是Stripe阅读器设备
                            if (device != null) {
                                checkAndRequestUsbPermission(device)
                                
                                // 如果是Stripe阅读器设备连接，延迟处理以确保设备稳定
                                if (isStripeReaderDevice(device)) {
                                    handler.postDelayed({
                                        Log.d("TerminalPayment", "Stripe阅读器设备连接稳定，开始处理")
                                        // Stripe SDK会自动处理设备连接事件
                                    }, 2000) // 延迟2秒确保设备连接稳定
                                }
                            }
                        }
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            Log.d("TerminalPayment", "USB设备已断开: ${device?.deviceName}, VendorId: ${device?.vendorId}, ProductId: ${device?.productId}")
                            
                            // 如果是Stripe阅读器设备断开，处理断开连接
                            if (device != null) {
                                handleUsbDeviceDetached(device)
                            }
                        }
                        ACTION_USB_PERMISSION -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            
                            if (permissionGranted) {
                                Log.d("TerminalPayment", "USB设备权限已授予: ${device?.deviceName}")
                                // Stripe SDK会自动处理权限授予事件
                            } else {
                                Log.w("TerminalPayment", "USB设备权限被拒绝: ${device?.deviceName}")
                            }
                        }
                    }
                }
            }
            
            // 注册广播接收器
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            
            registerReceiver(usbReceiver, filter)
            isUsbReceiverRegistered = true
            Log.d("TerminalPayment", "USB设备监听器注册成功")
            
            // 检查当前连接的USB设备
            val deviceList = usbManager?.deviceList
            if (deviceList != null && deviceList.isNotEmpty()) {
                Log.d("TerminalPayment", "当前连接的USB设备数量: ${deviceList.size}")
                for ((name, device) in deviceList) {
                    Log.d("TerminalPayment", "USB设备: $name, VendorId: ${device.vendorId}, ProductId: ${device.productId}")
                    // 检查并请求USB权限
                    checkAndRequestUsbPermission(device)
                }
            } else {
                Log.d("TerminalPayment", "当前没有连接USB设备")
            }
        } catch (e: Exception) {
            Log.e("TerminalPayment", "USB管理器初始化失败", e)
        }
    }
    
    /**
     * 检查并请求USB设备权限
     */
    private fun checkAndRequestUsbPermission(device: UsbDevice) {
        try {
            usbManager?.let { manager ->
                if (manager.hasPermission(device)) {
                    Log.d("TerminalPayment", "已拥有USB设备权限: ${device.deviceName}")
                } else {
                    Log.d("TerminalPayment", "请求USB设备权限: ${device.deviceName}")
                    // 注意：在实际应用中，可能需要用户交互来授予权限
                    // 这里我们只是记录日志，Stripe SDK会处理权限请求
                    
                    // 创建权限请求Intent
                    val permissionIntent = PendingIntent.getBroadcast(
                        this, 
                        0, 
                        Intent(ACTION_USB_PERMISSION), 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE
                        } else {
                            0
                        }
                    )
                    
                    // 请求权限
                    manager.requestPermission(device, permissionIntent)
                }
            }
        } catch (e: Exception) {
            Log.e("TerminalPayment", "检查USB设备权限时出错", e)
        }
    }
    
    /**
     * 处理USB设备断开连接
     */
    private fun handleUsbDeviceDetached(device: UsbDevice) {
        try {
            Log.d("TerminalPayment", "处理USB设备断开连接: ${device.deviceName}")
            
            // 检查是否是Stripe阅读器设备断开
            if (isStripeReaderDevice(device)) {
                Log.d("TerminalPayment", "Stripe阅读器设备断开连接: ${device.deviceName}")
                
                // 检查是否是正常的Activity暂停/销毁过程
                if (isFinishing || isChangingConfigurations) {
                    Log.d("TerminalPayment", "Activity正在finish或配置更改，不处理设备断开")
                    return
                }
                
                // 延迟处理，避免设备重新连接时的误判
                handler.postDelayed({
                    // 检查设备是否真的断开（没有重新连接）
                    if (!isUsbDeviceConnected(device)) {
                        Log.d("TerminalPayment", "确认设备已断开，通知TerminalManager")
                        // 通知TerminalManager设备断开
                        if (::terminalManager.isInitialized) {
                            // 注意：Stripe SDK会自动处理设备断开连接的事件
                            // 我们只需要记录日志即可
                        }
                    } else {
                        Log.d("TerminalPayment", "设备已重新连接，忽略断开事件")
                    }
                }, 2000) // 延迟2秒检查
            }
        } catch (e: Exception) {
            Log.e("TerminalPayment", "处理USB设备断开连接时出错", e)
        }
    }
    
    /**
     * 检查是否是Stripe阅读器设备
     */
    private fun isStripeReaderDevice(device: UsbDevice): Boolean {
        // Stripe阅读器的VendorId通常是5538 (0x15A2)
        return device.vendorId == 5538 || device.vendorId == 0x15A2
    }
    
    /**
     * 检查USB设备是否仍连接
     */
    private fun isUsbDeviceConnected(device: UsbDevice): Boolean {
        try {
            val deviceList = usbManager?.deviceList
            return deviceList?.values?.any { it.deviceName == device.deviceName } ?: false
        } catch (e: Exception) {
            Log.e("TerminalPayment", "检查USB设备连接状态时出错", e)
            return false
        }
    }
    
    /**
     * 注销USB设备监听器
     */
    private fun unregisterUsbDeviceListener() {
        if (isUsbReceiverRegistered && usbReceiver != null) {
            try {
                unregisterReceiver(usbReceiver)
                isUsbReceiverRegistered = false
                usbReceiver = null
                Log.d("TerminalPayment", "USB设备监听器注销成功")
            } catch (e: Exception) {
                Log.e("TerminalPayment", "注销USB设备监听器时出错", e)
            }
        } else {
            Log.d("TerminalPayment", "USB设备监听器未注册或已注销")
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
                Log.d("TerminalPayment", "倒计时结束，设置用户离开Terminal页面标志")
                // 确保进度条到0，然后返回主页面
                progressTimer.setProgress(0f)
                
                // 倒计时结束表示用户离开了页面，设置用户离开标志
                if (::terminalManager.isInitialized) {
                    terminalManager.setUserLeftTerminalPage(true)
                }
                
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
     * 重置进度条为10分钟
     * 用于升级过程
     */
    private fun resetProgressTimerTo10Minutes() {
        Log.d("TerminalPayment", "重置进度条为10分钟")
        countDownTimer?.cancel()

        // 重新开始10分钟倒计时 (600000ms)
        val tenMinutesInMillis = 10 * 60 * 1000L
        countDownTimer = object : CountDownTimer(tenMinutesInMillis, 33) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (millisUntilFinished.toFloat() / tenMinutesInMillis) * 100f
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
    override fun onDisplayStateChanged(displayState: DisplayState, vararg message: Any?) {
        runOnUiThread {
            updateUIForDisplayState(displayState, message)
        }
    }

    // 进度条重置监听器实现
    override fun onProgressTimerReset() {
        runOnUiThread {
            // 统一重置进度条为20秒（用于错误显示）
            Log.d("TerminalPayment", "收到进度条重置信号，重置为20秒")
            resetProgressTimerTo20Seconds()
        }
    }

    // 升级期间的10分钟进度条重置监听器实现
    override fun onProgressTimerResetTo10Minutes() {
        runOnUiThread {
            // 重置进度条为10分钟（用于升级过程）
            Log.d("TerminalPayment", "收到升级期间进度条重置信号，重置为10分钟")
            resetProgressTimerTo10Minutes()
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
    private fun updateUIForDisplayState(displayState: DisplayState, vararg message: Any?) {
        Log.d("TerminalPayment", "更新UI为状态: $displayState (UIType: ${displayState.uiType})")

        // 根据UIType决定UI展示方式
        when (displayState.uiType) {
            UIType.LOADING -> {
                // Type 1: 进度环+文字
                showLoadingState()
                // 如果message只有一个元素且是数组，则展开该数组作为参数
                loadingText.text = if (message.size == 1 && message[0] is Array<*>) {
                    displayState.getFormattedText(this, *(message[0] as Array<*>))
                } else {
                    displayState.getFormattedText(this, *message)
                }
                loadingText.setTextColor(getColor(R.color.text_primary))

                isProcessing = true
                backButton.isEnabled = false
                backButton.alpha = 0.5f
            }

            UIType.TAP_TO_PAY -> {
                // Type 2: 文字+图片
                try {
                    Log.d("TerminalPayment", "开始更新TAP_TO_PAY页面")
                    showCompletedState()
                    
                    // 记录当前布局状态用于调试
                    Log.d("TerminalPayment", "loadingLayout.visibility: ${loadingLayout.visibility}, completedLayout.visibility: ${completedLayout.visibility}")
                    
                    // 在TAP_TO_PAY状态下，completed_layout应该显示固定文本和图片
                    // 我们不需要更新任何动态文本，因为completed_layout中的文本是固定的
                    
                    // 设置处理状态和返回按钮
                    isProcessing = false
                    backButton.isEnabled = displayState.canGoBack
                    backButton.alpha = if (displayState.canGoBack) 1.0f else 0.5f
                    
                    Log.d("TerminalPayment", "成功更新TAP_TO_PAY页面")
                } catch (e: Exception) {
                    Log.e("TerminalPayment", "进入TapToPay UI异常", e)
                }
            }

            UIType.MESSAGE -> {
                // Type 3: 只展示白色粗体文字
                // 如果message只有一个元素且是数组，则展开该数组作为参数
                val formattedText = if (message.size == 1 && message[0] is Array<*>) {
                    displayState.getFormattedText(this, *(message[0] as Array<*>))
                } else {
                    displayState.getFormattedText(this, *message)
                }
                showMessage(formattedText)

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
        Log.d("TerminalPayment", "显示完成状态: 隐藏loadingLayout，显示completedLayout")
        loadingLayout.visibility = View.GONE
        completedLayout.visibility = View.VISIBLE
        messageLayout.visibility = View.GONE
        Log.d("TerminalPayment", "完成状态显示完成: loadingLayout.visibility=${loadingLayout.visibility}, completedLayout.visibility=${completedLayout.visibility}")
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
        
        // 重新注册USB设备监听器（如果需要）
        registerUsbDeviceListener()
    }

    override fun onPause() {
        Log.d("TerminalPayment", "=== onPause 被调用 ===")
        super.onPause()
        // 不要停止拦截，保持监控

        // 检查是否是用户主动离开页面（通过返回按钮）
        // 注意：不要在普通的onPause中无条件设置用户离开标志
        // 只有在用户明确表示要离开页面时才设置
        Log.d("TerminalPayment", "onPause被调用，检查是否需要设置用户离开标志")
        
        // 只有在真正finish时才应该设置用户离开标志
        // 这个检查会在onDestroy中进行更精确的判断
    }

    override fun onStop() {
        Log.d("TerminalPayment", "=== onStop 被调用 ===")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d("TerminalPayment", "=== onDestroy 被调用 ===")
        Log.d("TerminalPayment", "isFinishing: $isFinishing")
        Log.d("TerminalPayment", "isChangingConfigurations: $isChangingConfigurations")
        Log.d("TerminalPayment", "调用堆栈: ${Thread.currentThread().stackTrace.take(5).joinToString("\n")}")

        super.onDestroy()
        countDownTimer?.cancel()
        homeKeyInterceptor.stopIntercepting()

        // 清理消息延迟任务（如果有的话）
        messageDelayRunnable?.let { messageDelayHandler?.removeCallbacks(it) }

        // 注销USB设备监听器
        unregisterUsbDeviceListener()

        // 检查是否是因为配置更改（如语言切换）导致的Activity重建
        val isConfigurationChange = isChangingConfigurations
        
        // 如果不是正常finish，或者是因为配置更改导致的销毁，说明Activity被系统意外销毁或正在重建
        if (!isFinishing || isConfigurationChange) {
            if (isConfigurationChange) {
                Log.d("TerminalPayment", "Activity因配置更改（如语言切换）而重建，保持支付收集")
            } else {
                Log.w("TerminalPayment", "Activity被系统意外销毁，但用户可能还在Terminal页面")
                // 在这种情况下，我们不确定用户是否真的离开了页面
                // 不设置用户离开标志，让系统决定是否需要重新连接
            }
            // 不要暂停支付收集，让Terminal继续运行
            // 用户可能还期望Terminal功能继续工作
        } else {
            Log.d("TerminalPayment", "Activity正常结束，暂停支付收集")
            // 只有在用户主动离开页面时才设置用户离开标志
            // 这包括：点击返回按钮、倒计时结束等用户明确表示要离开的情况
            if (::terminalManager.isInitialized) {
                terminalManager.setUserLeftTerminalPage(true)
                Log.d("TerminalPayment", "设置用户离开Terminal页面标志")
            }
            
            // 重置配置更改标志
            TerminalConnectionManager.setPausedForConfigurationChange(false)
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
     * 初始化Terminal
     */
    private fun initializeTerminal() {
        Log.d("TerminalPayment", "Initializing Terminal...")

        // 打印Terminal连接状态报告
        Log.d("TerminalPayment", TerminalConnectionManager.getStatusReport())

        // 使用TerminalConnectionManager初始化
        Log.d("TerminalPayment", "Initializing via TerminalConnectionManager...")
        TerminalConnectionManager.initializeIfNeeded(this, this)
    }
}
