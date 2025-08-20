package com.stwpower.powertap.terminal

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.log.LogLevel
import com.stwpower.powertap.config.ConfigLoader
import com.stwpower.powertap.data.api.MyApiClient
import com.stwpower.powertap.data.provider.TokenProvider
import com.stwpower.powertap.managers.PreferenceManager
import com.stwpower.powertap.utils.MyLog
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/** 
 * Stripe Terminal 管理器
 * 负责管理 Terminal 的完整生命周期和支付流程
 */
class StripeTerminalManager(
    private val context: Context,
    private var stateListener: TerminalStateListener
) : TerminalListener, DiscoveryListener, BluetoothReaderListener, UsbReaderListener, UsbDeviceManager.UsbDeviceListener {

    companion object {
        private const val DISCOVERY_TIMEOUT = 2 * 60 * 1000L // 2分钟
        private const val RETRY_DELAY = 10 * 1000L // 重试延迟10秒
        private const val DISCOVERY_RETRY_DELAY = 30 * 1000L // 发现重试延迟30秒
    }

    interface TerminalStateListener {
        fun onDisplayStateChanged(displayState: DisplayState, vararg message: Any?)
        fun onProgressTimerReset() // 统一的进度条重置监听器
        fun onProgressTimerResetTo10Minutes() // 升级期间的10分钟进度条重置监听器
        fun onRestartPayment() // 统一的重试进入收集付款方式监听器
    }

    // 使用新的状态管理器
    private val stripeStateManager = StripeStateManager()
    private val usbDeviceManager = UsbDeviceManager(context)

    // 标志：用户是否已经离开了Terminal页面
    private var userLeftTerminalPage = true
    private var paymentJob: Job? = null
    private var discoveryCancelable: Cancelable? = null
    private var collectCancelable: Cancelable? = null
    private var connectedReader: Reader? = null
    private var currentReader: Reader? = null // 当前尝试连接的阅读器
    private var currentPaymentIntent: PaymentIntent? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 重试机制相关
    private var discoveryRetryCount = 0
    private var connectionRetryCount = 0
    private var paymentRetryCount = 0
    
    // 时间戳跟踪
    private var lastDiscoveryStartTime: Long = 0L

    // 状态保持相关
    private var isReaderConnected = false
    private var shouldMaintainConnection = true

    /**
     * 初始化 Terminal
     */
    fun initialize() {
        // 初始化USB设备管理器
        usbDeviceManager.setDeviceListener(this)
        usbDeviceManager.initialize()
        
        // 设置状态管理器监听器
        stripeStateManager.setStateListener(object : StripeStateManager.StripeStateListener {
            override fun onDisplayStateChanged(displayState: DisplayState, vararg message: Any?) {
                // 如果message只有一个元素且是数组，则展开该数组
                if (message.size == 1 && message[0] is Array<*>) {
                    stateListener.onDisplayStateChanged(displayState, *(message[0] as Array<*>))
                } else {
                    stateListener.onDisplayStateChanged(displayState, *message)
                }
            }
        })

        // 开始初始化
        updateDisplayState(DisplayState.LOADING, null)
        resetRetryCounters() // 重置所有重试计数器

        if (Terminal.isInitialized()) {
            MyLog.d("Terminal已经初始化")

            // 检查是否已有连接的阅读器
            if (checkExistingConnection()) {
                MyLog.d("阅读器已连接，不进行扫描")
                return
            }

            startDiscovery()
            return
        }

        // 检查权限
        if (!hasRequiredPermissions()) {
            updateDisplayState(DisplayState.LOADING, null)
            return
        }

        // 检查GPS
        if (!isGpsEnabled()) {
            updateDisplayState(DisplayState.LOADING, null)
            return
        }

        // 启用蓝牙
        enableBluetoothIfNeeded()

        try {
            Terminal.initTerminal(
                context.applicationContext,
                LogLevel.VERBOSE,
                TokenProvider(),
                this
            )
            MyLog.d("Terminal初始化成功")

            // 初始化完成，开始发现读卡器
            startDiscovery()
        } catch (e: TerminalException) {
            MyLog.e("Terminal初始化失败", e)
            updateDisplayState(DisplayState.INIT_FAILED, null)
        }
    }

    /**
     * 开始发现阅读器
     */
    private fun startDiscovery() {
        MyLog.d("开始扫描阅读器前检查")

        // 发现读卡器是Stripe的技术状态，会通过ConnectionStatus自动更新
        updateDisplayState(DisplayState.SCANNING_READER, null)

        // 记录发现开始时间
        lastDiscoveryStartTime = System.currentTimeMillis()

        // 检查二维码编号
        val qrCode = getDeviceQrCode()
        if (qrCode.isEmpty()) {
            MyLog.e("二维码编号为空，10s后重试")
            handler.postDelayed({
                startDiscovery()
            }, 10000)
            return
        }

        // 获取位置ID
        val locationId = getLocationId()
        if (locationId.isNullOrEmpty()) {
            MyLog.e("该设备的locationId为空，10s重试")
            handler.postDelayed({
                startDiscovery()
            }, 10000)
            return
        }

        MyLog.d("开始扫描阅读器，位置id：$locationId")

        // 使用USB发现方式
        val config = DiscoveryConfiguration(
            timeout = 0,
            discoveryMethod = DiscoveryMethod.USB,
            isSimulated = false, // 使用真实设备
            location = locationId
        )

        discoveryCancelable = Terminal.getInstance().discoverReaders(
            config,
            this,
            object : Callback {
                override fun onSuccess() {
                    MyLog.d("扫描成功")
                }

                override fun onFailure(e: TerminalException) {
                    MyLog.e("扫描失败", e)
                    retryDiscovery(e)
                }
            }
        )
        //移除自动超时设置
    }

    /**
     * 连接到阅读器
     */
    private fun connectToReader(reader: Reader) {
        // 连接读卡器是Stripe的技术状态，会通过ConnectionStatus自动更新
        currentReader = reader // 保存当前尝试连接的阅读器

        val locationId = getLocationId()
        if (locationId.isNullOrEmpty()) {
            updateDisplayState(DisplayState.CONNECT_READER_FAILED, null)
            return
        }

        MyLog.d("连接到阅读器: ${reader.serialNumber}")
        
        // 检查当前连接状态，避免在连接过程中重复连接
        val currentConnectionStatus = Terminal.getInstance().connectionStatus
        if (currentConnectionStatus == ConnectionStatus.CONNECTING) {
            MyLog.d("阅读器正在连接中，跳过重复连接请求")
            return
        }
        
        // 检查是否已经连接到同一个阅读器
        if (currentConnectionStatus == ConnectionStatus.CONNECTED && 
            connectedReader?.serialNumber == reader.serialNumber) {
            MyLog.d("已经连接到同一个阅读器，跳过重复连接请求")
            return
        }

        // 参考Example.kt，使用USB连接
        val config = ConnectionConfiguration.UsbConnectionConfiguration(
            locationId,
            false, // autoReconnectOnUnexpectedDisconnect
            createReaderReconnectionListener()
        )

        Terminal.getInstance().connectUsbReader(reader, config, this, createReaderCallback())
    }

    /**
     * 开始支付流程（当支付状态为READY时自动调用）
     */
    private fun startPaymentCollection() {
        MyLog.d("开始支付收集流程")

        // 直接更新为等待刷卡状态
        updateDisplayState(DisplayState.ENTER_COLLECTION_METHOD, null)

        scope.launch(Dispatchers.IO) {
            try {
                // 创建支付意图是支付流程的一部分，保持当前状态

                // 创建支付意图
                val sno = getDeviceQrCode()
                val paymentIntentResult = MyApiClient.createPaymentIntent(sno)

                withContext(Dispatchers.Main) {
                    if (paymentIntentResult?.get("clientSecret") != null) {
                        MyLog.d("服务端创建PaymentIntent成功，取回PaymentIntent")

                        // 获取支付意图
                        Terminal.getInstance().retrievePaymentIntent(
                            paymentIntentResult["clientSecret"]!!,
                            createPaymentIntentCallback()
                        )
                    } else {
                        updateDisplayState(DisplayState.ENTER_COLLECTION_METHOD_FAILED, null)
                    }
                }
            } catch (e: Exception) {
                MyLog.e("开始收集支付流程失败", e)
                withContext(Dispatchers.Main) {
                    updateDisplayState(DisplayState.ENTER_COLLECTION_METHOD_FAILED, null)
                    stateListener.onRestartPayment()
                }
            }
        }
    }

    /**
     * 安全地取消当前操作
     */
    fun cancel() {
        // 取消操作，回到准备状态
        updateDisplayState(DisplayState.LOADING, null)

        // 安全地取消发现操作
        MyLog.d("取消扫描阅读器操作")
        discoveryCancelable?.let { cancelable ->
            try {
                if (!cancelable.isCompleted) {
                    cancelable.cancel(object : Callback {
                        override fun onSuccess() {
                            MyLog.d("扫描阅读器已取消")
                        }
                        override fun onFailure(e: TerminalException) {
                            MyLog.w("取消扫描阅读器操作失败: ${e.message}")
                        }
                    })
                } else {
                    MyLog.d("取消扫描阅读器操作已完成，无需操作")
                }
            } catch (e: Exception) {
                MyLog.w("取消扫描阅读器时发生异常: ${e.message}")
            }
            discoveryCancelable = null
        }

        // 安全地取消支付收集操作
        MyLog.d("取消收集支付方式操作")
        collectCancelable?.let { cancelable ->
            try {
                if (!cancelable.isCompleted) {
                    cancelable.cancel(object : Callback {
                        override fun onSuccess() {
                            MyLog.d("支付收集已取消")
                        }
                        override fun onFailure(e: TerminalException) {
                            MyLog.w("取消支付收集失败: ${e.message}")
                        }
                    })
                } else {
                    MyLog.d("支付收集操作已完成，无需取消")
                }
            } catch (e: Exception) {
                MyLog.w("取消支付收集时发生异常: ${e.message}")
            }
            collectCancelable = null
        }

        // 取消协程
        paymentJob?.cancel()
        paymentJob = null
    }

    /**
     * 检查现有连接
     */
    private fun checkExistingConnection(): Boolean {
        return try {
            val connectionStatus = Terminal.getInstance().connectionStatus
            val paymentStatus = Terminal.getInstance().paymentStatus
            val connectedReader = Terminal.getInstance().connectedReader

            MyLog.d("检查现有连接状态:")
            MyLog.d("  连接状态: $connectionStatus")
            MyLog.d("  支付状态: $paymentStatus")
            MyLog.d("  连接的阅读器: ${connectedReader?.serialNumber}")

            if (connectionStatus == ConnectionStatus.CONNECTED && connectedReader != null) {
                this.connectedReader = connectedReader
                isReaderConnected = true

                when (paymentStatus) {
                    PaymentStatus.READY -> {
                        MyLog.d("阅读器已连接且支付状态为READY，需要重新开始支付收集流程")
                        // 支付状态会通过PaymentStatus自动更新UI

                        // 延迟一下再开始支付收集，确保UI状态正确
                        handler.postDelayed({
                            MyLog.d("开始重新收集支付方式")
                            startPaymentCollection()
                        }, 1000)

                        return true
                    }
                    PaymentStatus.NOT_READY -> {
                        MyLog.d("阅读器已连接但支付状态为NOT_READY，等待状态变化")
                        // 支付状态会通过PaymentStatus自动更新UI
                        return true
                    }
                    PaymentStatus.WAITING_FOR_INPUT -> {
                        MyLog.d("阅读器已连接且正在等待输入，直接进入等待刷卡状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                        return true
                    }
                    else -> {
                        MyLog.d("阅读器已连接，支付状态: $paymentStatus，显示连接状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                        return true
                    }
                }
            }

            MyLog.d("没有找到有效的阅读器连接")
            return false
        } catch (e: Exception) {
            MyLog.e("检查现有连接时发生异常", e)
            false
        }
    }

    /**
     * 暂停支付收集（保持连接）
     */
    fun pausePaymentCollection() {
        MyLog.d("暂停支付收集，保持阅读器连接")

        try {
            // 安全地取消当前的支付收集
            collectCancelable?.let { cancelable ->
                try {
                    if (!cancelable.isCompleted) {
                        cancelable.cancel(object : Callback {
                            override fun onSuccess() {
                                MyLog.d("支付收集已取消")
                            }
                            override fun onFailure(e: TerminalException) {
                                MyLog.w("取消支付收集失败: ${e.message}")
                            }
                        })
                    } else {
                        MyLog.d("支付收集操作已完成，无需取消")
                    }
                } catch (e: Exception) {
                    MyLog.w("取消支付收集时发生异常: ${e.message}")
                }
                collectCancelable = null
            }

            // 取消支付相关的协程
            paymentJob?.cancel()
            paymentJob = null

            // 清理Handler中的支付相关回调
            handler.removeCallbacksAndMessages(null)

            // 保持连接状态
            shouldMaintainConnection = true

            MyLog.d("支付收集已暂停，阅读器连接保持")
            updateDisplayState(DisplayState.LOADING, null)

        } catch (e: Exception) {
            MyLog.e("暂停支付收集时发生异常", e)
        }
    }

    /**
     * 恢复支付收集
     */
    fun resumePaymentCollection() {
        MyLog.d("恢复支付收集")

        try {
            val connectionStatus = Terminal.getInstance().connectionStatus
            val paymentStatus = Terminal.getInstance().paymentStatus

            MyLog.d("当前状态 - 连接: $connectionStatus, 支付: $paymentStatus")

            if (connectionStatus == ConnectionStatus.CONNECTED) {
                when (paymentStatus) {
                    PaymentStatus.READY -> {
                        MyLog.d("支付状态为READY，开始支付收集流程")
                        // 支付状态会通过PaymentStatus自动更新UI

                        // 延迟一下再开始支付收集
                        handler.postDelayed({
                            startPaymentCollection()
                        }, 1000)
                    }
                    PaymentStatus.NOT_READY -> {
                        MyLog.d("支付状态为NOT_READY，等待状态变化")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    PaymentStatus.WAITING_FOR_INPUT -> {
                        MyLog.d("支付状态为WAITING_FOR_INPUT，直接进入等待刷卡状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    else -> {
                        MyLog.d("当前支付状态: $paymentStatus，显示连接状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                }
            } else {
                MyLog.w("阅读器未连接，重新开始发现流程")
                startDiscovery()
            }

        } catch (e: Exception) {
            MyLog.e("恢复支付收集时发生异常", e)
            startDiscovery()
        }
    }

    /**
     * 完全断开连接（仅在应用退出时调用）
     */
    fun disconnect() {
        MyLog.d("完全断开阅读器")
        shouldMaintainConnection = false

        cancel()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)

        if (Terminal.isInitialized() && connectedReader != null) {
            Terminal.getInstance().disconnectReader(object : Callback {
                override fun onSuccess() {
                    MyLog.d("阅读器断开成功")
                }
                override fun onFailure(e: TerminalException) {
                    MyLog.e("阅读器断开失败", e)
                }
            })
        }

        // 重置状态
        connectedReader = null
        isReaderConnected = false

        MyLog.d("阅读器断开完成")
    }

    // TerminalListener 实现
    override fun onUnexpectedReaderDisconnect(reader: Reader) {
        MyLog.w("阅读器意外断开连接: ${reader.deviceType}")
        MyLog.w("阅读器序列号: ${reader.serialNumber}")
        MyLog.w("阅读器类型: ${reader.deviceType}")
        
        currentReader = null

        // 根据状态管理逻辑：阅读器断开连接需要重新扫描连接
        MyLog.d("阅读器断开连接，开始重新扫描连接")
        // 断开连接会通过ConnectionStatus自动更新UI

        // 重新开始扫描和连接流程，但添加延迟避免频繁重连
        handler.postDelayed({
            startDiscovery()
        }, 5000) // 延迟5秒再开始扫描
    }

    override fun onConnectionStatusChange(status: ConnectionStatus) {
        MyLog.d("连接状态改变: $status")

        when (status) {
            ConnectionStatus.CONNECTING -> {
                MyLog.d("正在连接阅读器")
                updateDisplayState(DisplayState.CONNECTING_READER, null)
            }
            ConnectionStatus.CONNECTED -> {
                MyLog.d("阅读器连接成功")
                updateDisplayState(DisplayState.ENTER_COLLECTION_METHOD, null)
                
                // 重置发现重试计数器
                discoveryRetryCount = 0
                
                // 检查用户是否已离开页面
                if (userLeftTerminalPage) {
                    MyLog.d("用户已离开页面，但阅读器连接成功")
                    // 在这种情况下，我们可能需要暂停连接以节省资源
                }
            }
            ConnectionStatus.NOT_CONNECTED -> {
                MyLog.w("阅读器断开连接")
                updateDisplayState(DisplayState.LOADING, null)

                MyLog.d("重新开始发现流程")
                handler.postDelayed({
                    startDiscovery()
                }, 3000) // 延迟3秒再开始扫描
            }
        }
    }

    override fun onPaymentStatusChange(status: PaymentStatus) {
        MyLog.d("Payment status changed: $status")

        when (status) {
            PaymentStatus.NOT_READY -> {
                updateDisplayState(DisplayState.LOADING, null)
                // 如果处于NOT_READY，30s后还是处于NOT_READY，进行重连
                handler.postDelayed({
                    if (Terminal.getInstance().paymentStatus == PaymentStatus.NOT_READY) {
                        MyLog.d("支付状态超过30s处于NOT_READY，重新扫描")
                        startDiscovery()
                    }
                }, 30000)
            }
            PaymentStatus.READY -> {
                MyLog.d("支付状态为READY，准备接收支付")

                // 检查是否应该自动开始收集付款方式
                val shouldAutoStart = shouldAutoStart()

                if (shouldAutoStart) {
                    MyLog.d("自动进入收集付款方式")
                    startPaymentCollection()
                }
            }
            PaymentStatus.WAITING_FOR_INPUT -> {
                MyLog.d("支付状态为WAITING_FOR_INPUT，等待用户输入")
                updateDisplayState(DisplayState.COLLECTING_PAYMENT_METHOD, null)
            }
            PaymentStatus.PROCESSING -> {
                MyLog.d("支付状态为PROCESSING，正在处理支付")
            }
        }
    }

    override fun onStartInstallingUpdate(update: ReaderSoftwareUpdate, cancelable: Cancelable?) {
        MyLog.d("开始升级")
        // 更新过程可以考虑添加到BusinessPhase，暂时保持现状
        updateDisplayState(DisplayState.START_UPGRADING_READER, null)
        // 通知Activity重置倒计时器为10分钟
        MyLog.d("通知Activity重置倒计时器为10分钟（升级开始）")
        stateListener.onProgressTimerResetTo10Minutes()
    }

    override fun onReportReaderSoftwareUpdateProgress(progress: Float) {
        try{
            val percentage = (progress * 100).roundToInt() // 四舍五入而不是直接截断
            MyLog.d("Update progress: ${percentage}%")
            // 直接传递整数值而不是数组
            updateDisplayState(DisplayState.UPGRADING, percentage)
        }catch (e:Exception){
            MyLog.e("升级异常",e)
        }

    }

    override fun onFinishInstallingUpdate(update: ReaderSoftwareUpdate?, e: TerminalException?) {
        MyLog.d("升级结束，重置倒计时器为60s")
        if (e != null) {
            MyLog.e("升级结果：失败", e)
            updateDisplayState(DisplayState.UPGRADE_FAILED, e.message)
            // 升级失败，重置倒计时器为60秒
            stateListener.onProgressTimerReset()
        } else {
            MyLog.d("升级结果：成功")
            // 更新成功，重置倒计时器为60秒
            stateListener.onProgressTimerReset()
        }
    }

    // DiscoveryListener 实现
    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
        MyLog.d("扫描到阅读器数量： ${readers.size}，进行连接")
        if (readers.isNotEmpty()) {
            discoveryRetryCount = 0 // 重置发现重试计数器
            connectToReader(readers[0])
        } else {
            handleNoReadersFound()
        }
    }

    // 创建专门的回调对象来避免接口冲突

    /**
     * 创建阅读器连接回调
     */
    private fun createReaderCallback(): ReaderCallback {
        return object : ReaderCallback {
            override fun onSuccess(reader: Reader) {
                MyLog.d("连接阅读器成功，序列号: ${reader.serialNumber}")
                connectedReader = reader
                connectionRetryCount = 0 // 重置连接重试计数器
                // 连接成功会通过ConnectionStatus自动更新UI
            }

            override fun onFailure(e: TerminalException) {
                MyLog.e("连接阅读器失败，${DISCOVERY_RETRY_DELAY}s后重新扫描", e)
                currentReader?.let { reader ->
                    retryDiscovery(e)
                } ?: run {
                    updateDisplayState(DisplayState.CONNECT_READER_FAILED, null)
                }
            }
        }
    }

    /**
     * 创建支付意图回调
     */
    private fun createPaymentIntentCallback(): PaymentIntentCallback {
        return object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                MyLog.d("取回PaymentIntent成功，paymentIntent: $paymentIntent，开始收集付款方式")
                currentPaymentIntent = paymentIntent
                collectPaymentMethod(paymentIntent)
            }

            override fun onFailure(e: TerminalException) {
                MyLog.e("取回PaymentIntent失败", e)
                updateDisplayState(DisplayState.ENTER_COLLECTION_METHOD_FAILED, null)
                stateListener.onRestartPayment()
            }
        }
    }

    /**
     * 创建重连监听器
     */
    private fun createReaderReconnectionListener(): ReaderReconnectionListener {
        return object : ReaderReconnectionListener {
            override fun onReaderReconnectFailed(reader: Reader) {
                MyLog.w("阅读器重连失败: ${reader.serialNumber}")
                handler.postDelayed({
                    startDiscovery()
                }, 10000)
            }

            override fun onReaderReconnectStarted(reader: Reader, cancelReconnect: Cancelable) {
                MyLog.d("阅读器重连开始: ${reader.serialNumber}")
            }

            override fun onReaderReconnectSucceeded(reader: Reader) {
                MyLog.d("阅读器重连成功: ${reader.serialNumber}")
                connectedReader = reader
                // 重连成功会通过ConnectionStatus自动更新UI
            }
        }
    }

    // BluetoothReaderListener 实现
    override fun onReportReaderEvent(event: ReaderEvent) {
        MyLog.d("阅读器事件: $event")
        // 处理阅读器事件，如卡片插入、移除等
    }

    override fun onRequestReaderInput(options: ReaderInputOptions) {
        MyLog.d("阅读器输入操作: $options")
        // 处理阅读器输入请求
    }

    override fun onRequestReaderDisplayMessage(message: ReaderDisplayMessage) {
        MyLog.d("阅读器显示消息: $message")
        // 处理阅读器显示消息
    }

    // UsbReaderListener 实现 (与BluetoothReaderListener共享相同的方法)

    // 私有辅助方法
    private fun collectPaymentMethod(paymentIntent: PaymentIntent) {
        // 开始收集付款方式，这是支付流程的一部分

        val collectConfig = CollectConfiguration.Builder()
            .skipTipping(false)
            .build()

        collectCancelable = Terminal.getInstance().collectPaymentMethod(
            paymentIntent,
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    MyLog.d("收集付款方式成功")
                    updateDisplayState(DisplayState.RENTING, null)
                    confirmPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    MyLog.e("收集付款方式失败", e)

                    // 检查是否是用户取消操作
                    val isCancelled = e.errorCode == TerminalException.TerminalErrorCode.CANCELED ||
                                     e.message?.contains("cancel", ignoreCase = true) == true ||
                                     e.message?.contains("abort", ignoreCase = true) == true

                    updateDisplayState(DisplayState.COLLECT_PAYMENT_METHOD_FAILED, null)
                    if(!isCancelled){
                        stateListener.onRestartPayment()
                    }
                }
            },
            collectConfig
        )

        // 等待刷卡状态会通过PaymentStatus自动更新
    }

    private fun confirmPayment(paymentIntent: PaymentIntent) {
        // 确认支付状态会通过PaymentStatus自动更新

        Terminal.getInstance().processPayment(
            paymentIntent,
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    MyLog.d("确认付款方式成功，开始租借")
                    // 支付成功后调用租借接口
                    callRentalApiAfterPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    MyLog.e("确认付款方式失败", e)
                    updateDisplayState(DisplayState.COLLECT_PAYMENT_METHOD_FAILED, null)
                    stateListener.onRestartPayment()
                }
            }
        )
    }

    /**
     * 更新状态监听器
     */
    fun updateStateListener(newListener: TerminalStateListener) {
        MyLog.d("更新状态监听器")
        stateListener = newListener
        // 立即通知当前显示状态
        stateListener.onDisplayStateChanged(stripeStateManager.getCurrentDisplayState(), null)
    }

    /**
     * 支付成功后调用租借接口
     */
    private fun callRentalApiAfterPayment(paymentIntent: PaymentIntent) {
        MyLog.d("=== 开始调用租借接口 ===")

        updateDisplayState(DisplayState.RENTING, null)

        scope.launch {
            try {
                // 获取设备信息
                val qrCode = PreferenceManager.getQrCode()

                if (qrCode.isNullOrEmpty()) {
                    MyLog.e("设备序列号为空")
                    updateDisplayState(DisplayState.RENT_FAILED, null)
                    stateListener.onProgressTimerReset() // 配置错误，重置进度条
                    return@launch
                }

                MyLog.d("qrCode: $qrCode")
                MyLog.d("PaymentIntent ID: ${paymentIntent.id}")
                MyLog.d("appSecretKey: ${ConfigLoader.secretKey}")

                // 检查PaymentIntent的必要字段
                if (paymentIntent.id.isNullOrEmpty()) {
                    MyLog.e("PaymentIntent ID为空")
                    updateDisplayState(DisplayState.RENT_FAILED, null)
                    stateListener.onProgressTimerReset() // PaymentIntent无效，重置进度条
                    return@launch
                }

                // 构建请求参数（确保所有值都是非null的String）
                val requestBody = mapOf(
                    "qrCode" to qrCode,
                    "paymentIntentId" to (paymentIntent.id ?: ""),
                    "appSecretKey" to ConfigLoader.secretKey
                )

                // 在IO线程调用MyApiClient的租借接口
                val response = withContext(Dispatchers.IO) {
                    MyApiClient.lendPowerStripeTerminal(requestBody as Map<String, String>)
                }

                MyLog.d("租借接口调用完成")
                MyLog.d("响应结果: success=${response.success}, code=${response.code}, message=${response.message}")
                MyLog.d("响应数据: ${response.data}")

                // 直接使用响应中的成功状态和消息
                val success = response.success
                val message = response.message

                if (success) {
                    MyLog.d("租借接口调用成功: $message")
                    updateDisplayState(DisplayState.RENT_SUCCESS, null)
                    stateListener.onProgressTimerReset() // 租借成功，重置进度条
                } else {
                    MyLog.e("租借接口调用失败: $message")
                    updateDisplayState(DisplayState.RENT_FAILED, message)
                    stateListener.onProgressTimerReset() // 租借失败，重置进度条
                }

            } catch (e: Exception) {
                MyLog.e("调用租借接口时发生异常", e)
                updateDisplayState(DisplayState.RENT_FAILED, null)
                stateListener.onProgressTimerReset() // 租借异常，重置进度条
            }
        }
    }

    /**
     * 更新显示状态 - 统一入口
     * 供内部和外部调用的统一状态更新入口
     */
    fun updateDisplayState(displayState: DisplayState, vararg message: Any?) {
        MyLog.d("Display state changed to: $displayState")
        stripeStateManager.updateDisplayState(displayState, message)
    }

    private fun hasRequiredPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        return try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun enableBluetoothIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
                    if (!adapter.isEnabled) {
                        adapter.enable()
                    }
                }
            }
        } else {
            BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
                if (!adapter.isEnabled) {
                    adapter.enable()
                }
            }
        }
    }

    private fun getLocationId(): String? {
        return try {
            PreferenceManager.getLocationId()
        } catch (e: Exception) {
            MyLog.d("获取location ID异常", e)
            null
        }
    }

    private fun getDeviceQrCode(): String {
        return try {
            PreferenceManager.getDeviceSno() ?: ""
        } catch (e: Exception) {
            MyLog.d("获取QRCode异常", e)
            ""
        }
    }

    /**
     * 重试发现阅读器
     */
    private fun retryDiscovery(error: TerminalException) {
        MyLog.e("发现错误: ${error.errorMessage}", error)
        // 重试过程保持当前状态
        handler.postDelayed({
            // 检查当前连接状态
            val connectionStatus = Terminal.getInstance().connectionStatus
            if (connectionStatus == ConnectionStatus.CONNECTED) {
                MyLog.d("阅读器已连接，取消发现阅读器重试")
                return@postDelayed
            }

            startDiscovery()
        }, DISCOVERY_RETRY_DELAY)
    }

    /**
     * 重置重试计数器
     */
    private fun resetRetryCounters() {
        discoveryRetryCount = 0
        connectionRetryCount = 0
        paymentRetryCount = 0
    }

    /**
     * 处理未找到阅读器
     */
    private fun handleNoReadersFound() {
        MyLog.d("未找到阅读器，重试")
        // 重试过程保持当前状态
        handler.postDelayed({
            startDiscovery()
        }, RETRY_DELAY)
    }

    /**
     * 获取当前显示状态
     */
    fun getCurrentDisplayState(): DisplayState {
        return stripeStateManager.getCurrentDisplayState()
    }

    /**
     * 设置用户离开Terminal页面标志
     * 当用户退出Terminal页面时调用
     */
    fun setUserLeftTerminalPage(left: Boolean) {
        userLeftTerminalPage = left
        MyLog.d("用户离开Terminal页面标志设置为: $left")
    }

    /**
     * 用户重新进入Terminal页面
     * 当用户重新进入Terminal页面时调用，重置离开标志
     */
    fun onUserEnteredTerminalPage() {
        userLeftTerminalPage = false
        MyLog.d("用户重新进入Terminal页面，重置离开标志")
        
        // 检查当前连接状态并决定是否需要重新开始发现流程
        val connectionStatus = Terminal.getInstance().connectionStatus
        val paymentStatus = Terminal.getInstance().paymentStatus
        
        MyLog.d("用户重新进入页面时的当前状态 - 连接: $connectionStatus, 支付: $paymentStatus")
        
        when (connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                MyLog.d("阅读器已连接，检查支付状态")
                // 阅读器已连接，检查支付状态
                when (paymentStatus) {
                    PaymentStatus.READY -> {
                        MyLog.d("支付状态为READY，等待用户操作")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    PaymentStatus.NOT_READY -> {
                        MyLog.d("支付状态为NOT_READY，等待状态变化")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    PaymentStatus.WAITING_FOR_INPUT -> {
                        MyLog.d("支付状态为WAITING_FOR_INPUT，直接进入等待刷卡状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    else -> {
                        MyLog.d("未知支付状态: $paymentStatus，显示连接状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                }
            }
            ConnectionStatus.CONNECTING -> {
                MyLog.d("正在连接阅读器")
                updateDisplayState(DisplayState.CONNECTING_READER, null)
            }
            ConnectionStatus.NOT_CONNECTED -> {
                MyLog.d("阅读器未连接，检查是否需要重新开始发现流程")
                // 检查是否真的需要重新开始发现流程
                val currentTime = System.currentTimeMillis()
                val timeSinceLastDiscovery = currentTime - lastDiscoveryStartTime
                
                // 如果距离上次发现已经超过30秒，或者从未开始过发现，则重新开始
                if (timeSinceLastDiscovery > 30000 || lastDiscoveryStartTime == 0L) {
                    MyLog.d("距离上次发现已经超过30秒或从未开始过发现，重新开始发现流程")
                    // 阅读器未连接，重新开始发现流程
                    startDiscovery()
                } else {
                    MyLog.d("距离上次发现时间较短($timeSinceLastDiscovery ms)，不重新开始发现流程")
                    // 显示当前状态
                    updateDisplayState(DisplayState.LOADING, null)
                }
            }
        }
    }

    /**
     * 检查是否有支付正在进行中
     */
    fun isPaymentInProgress(): Boolean {
        val currentState = stripeStateManager.getCurrentDisplayState()

        return when (currentState) {
            DisplayState.ENTER_COLLECTION_METHOD,
            DisplayState.COLLECTING_PAYMENT_METHOD,
            DisplayState.RENTING -> true
            else -> false
        }
    }

    /**
     * 检查用户是否已离开Terminal页面
     */
    fun isUserLeftTerminalPage(): Boolean {
        return userLeftTerminalPage
    }

    private fun shouldAutoStart(): Boolean {
        val currentState = stripeStateManager.getCurrentDisplayState()

        // 如果用户已离开页面，不自动开始
        if (userLeftTerminalPage) {
            MyLog.d("用户已离开Terminal页面标志设置为true，不自动开始收集付款方式")
            return false
        }
        
        MyLog.d("用户未离开Terminal页面，检查是否应该自动开始，当前状态: $currentState")
        return when (currentState) {
            DisplayState.ENTER_COLLECTION_METHOD_FAILED,
            DisplayState.COLLECTING_PAYMENT_METHOD,
            DisplayState.COLLECT_PAYMENT_METHOD_FAILED,
            DisplayState.RENTING,
            DisplayState.RENT_SUCCESS,
            DisplayState.RENT_FAILED -> false
            else -> {
                MyLog.d("自动进入收集付款方式")
                true
            }
        }
    }
    
    // UsbDeviceListener 实现
    override fun onDeviceAttached(device: UsbDevice) {
        MyLog.d("USB设备已连接: ${device.deviceName}")
        // 检查是否是Stripe阅读器设备
        if (usbDeviceManager.isStripeReaderDevice(device)) {
            MyLog.d("检测到Stripe阅读器设备连接: ${device.deviceName}")
            // 请求USB权限
            usbDeviceManager.checkAndRequestUsbPermission(device)
        }
    }
    
    override fun onDeviceDetached(device: UsbDevice) {
        MyLog.d("USB设备已断开: ${device.deviceName}")
        // 检查是否是Stripe阅读器设备断开
        if (usbDeviceManager.isStripeReaderDevice(device)) {
            MyLog.d("Stripe阅读器设备断开连接: ${device.deviceName}")
            // 可以在这里添加设备断开的处理逻辑
        }
    }
    
    override fun onPermissionGranted(device: UsbDevice) {
        MyLog.d("USB设备权限已授予: ${device.deviceName}")
        // 权限授予后，Stripe SDK会自动处理设备连接
    }
    
    override fun onPermissionDenied(device: UsbDevice) {
        MyLog.w("USB设备权限被拒绝: ${device.deviceName}")
        // 可以在这里添加权限被拒绝的处理逻辑
    }
}
