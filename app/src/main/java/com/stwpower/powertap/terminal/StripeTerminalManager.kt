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
import android.util.Log
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
        private const val TAG = "powertap"
        private const val DISCOVERY_TIMEOUT = 30000L // 30秒
        private const val CONNECTION_TIMEOUT = 20000L // 20秒
        private const val PAYMENT_TIMEOUT = 60000L // 60秒
        private const val MAX_RETRY_ATTEMPTS = 3 // 最大重试次数
        private const val RETRY_DELAY = 5000L // 重试延迟5秒
        private const val MAX_DISCOVERY_RETRY_ATTEMPTS = 3 // 最大发现重试次数
        private const val DISCOVERY_RETRY_DELAY = 5000L // 发现重试延迟5秒
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
    private var discoveryJob: Job? = null

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
    private var lastConnectionStartTime: Long = 0L
    private var lastPaymentStartTime: Long = 0L

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
            MyLog.d("Terminal already initialized")

            // 检查是否已有连接的阅读器
            if (checkExistingConnection()) {
                MyLog.d("Found existing reader connection, skipping discovery")
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
            MyLog.d("Terminal initialized successfully")

            // 初始化完成，开始发现读卡器
            startDiscovery()
        } catch (e: TerminalException) {
            MyLog.e("Failed to initialize Terminal", e)
            updateDisplayState(DisplayState.INIT_FAILED, null)
        }
    }

    /**
     * 开始发现阅读器
     */
    private fun startDiscovery() {
        // 发现读卡器是Stripe的技术状态，会通过ConnectionStatus自动更新
        updateDisplayState(DisplayState.SCANNING_READER, null)
        
        // 记录发现开始时间
        lastDiscoveryStartTime = System.currentTimeMillis()
        MyLog.d("开始扫描阅读器，位置id：${getLocationId()}")

        // 检查SNO
        val sno = getDeviceQrCode()
        if (sno.isEmpty()) {
            MyLog.e("SNO（二维码编号）为空，10s后重试")
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

        // 使用USB发现方式（参考Example.kt）
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
                    MyLog.d("Discovery completed successfully")
                }

                override fun onFailure(e: TerminalException) {
                    MyLog.e("Discovery failed", e)
                    retryDiscovery(e)
                }
            }
        )

        // 设置发现超时
        handler.postDelayed({
            if (discoveryCancelable?.isCompleted == false) {
                MyLog.w("Discovery timeout, retrying...")
                discoveryCancelable?.cancel(object : Callback {
                    override fun onSuccess() {
                        MyLog.d("Previous discovery cancelled successfully")
                        // 创建一个新的TerminalException实例用于重试
                        val errorCode = TerminalException.TerminalErrorCode.REQUEST_TIMED_OUT
                        val errorMessage = "Discovery timeout after cancellation"
                        val exception = TerminalException(errorCode, errorMessage)
                        retryDiscovery(exception)
                    }
                    override fun onFailure(e: TerminalException) {
                        MyLog.e("Failed to cancel previous discovery", e)
                        retryDiscovery(e)
                    }
                })
            }
        }, DISCOVERY_TIMEOUT)
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
                MyLog.e("Failed to start payment collection", e)
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
        discoveryCancelable?.let { cancelable ->
            try {
                if (!cancelable.isCompleted) {
                    cancelable.cancel(object : Callback {
                        override fun onSuccess() {
                            MyLog.d("Discovery cancelled successfully")
                        }
                        override fun onFailure(e: TerminalException) {
                            MyLog.w("Failed to cancel discovery: ${e.message}")
                        }
                    })
                } else {
                    MyLog.d("Discovery operation already completed")
                }
            } catch (e: Exception) {
                MyLog.w("Exception while cancelling discovery: ${e.message}")
            }
            discoveryCancelable = null
        }

        // 安全地取消支付收集操作
        collectCancelable?.let { cancelable ->
            try {
                if (!cancelable.isCompleted) {
                    cancelable.cancel(object : Callback {
                        override fun onSuccess() {
                            MyLog.d("Payment collection cancelled successfully")
                        }
                        override fun onFailure(e: TerminalException) {
                            MyLog.w("Failed to cancel payment collection: ${e.message}")
                        }
                    })
                } else {
                    MyLog.d("Payment collection operation already completed")
                }
            } catch (e: Exception) {
                MyLog.w("Exception while cancelling payment collection: ${e.message}")
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
     * 清理资源（保持连接）
     */
    fun cleanup() {
        MyLog.d("Cleaning up StripeTerminalManager (maintaining connection)")

        // 暂停支付收集但保持连接
        pausePaymentCollection()

        MyLog.d("StripeTerminalManager cleanup completed (connection maintained)")
    }

    /**
     * 完全断开连接（仅在应用退出时调用）
     */
    fun disconnect() {
        MyLog.d("Disconnecting StripeTerminalManager completely")
        shouldMaintainConnection = false

        cancel()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)

        if (Terminal.isInitialized() && connectedReader != null) {
            Terminal.getInstance().disconnectReader(object : Callback {
                override fun onSuccess() {
                    MyLog.d("Reader disconnected successfully")
                }
                override fun onFailure(e: TerminalException) {
                    MyLog.e("Failed to disconnect reader", e)
                }
            })
        }

        // 重置状态
        connectedReader = null
        isReaderConnected = false

        MyLog.d("StripeTerminalManager disconnected completely")
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
        }, 2000) // 延迟2秒再开始扫描
    }

    override fun onConnectionStatusChange(status: ConnectionStatus) {
        MyLog.d("Connection status changed: $status")
        
        // 记录当前时间，便于调试
        val currentTime = System.currentTimeMillis()
        MyLog.d("Connection status change time: $currentTime")

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
                }, 1000) // 延迟1秒再开始扫描
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
        MyLog.d("Starting reader update")
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
            Log.d(TAG,"升级异常",e)
        }

    }

    override fun onFinishInstallingUpdate(update: ReaderSoftwareUpdate?, e: TerminalException?) {
        MyLog.d("Update finished")
        if (e != null) {
            MyLog.e("Update failed", e)
            updateDisplayState(DisplayState.UPGRADE_FAILED, e.message)
            // 升级失败，重置倒计时器为60秒
            MyLog.d("通知Activity重置倒计时器为60秒（升级失败）")
            stateListener.onProgressTimerReset()
        } else {
            MyLog.d("Update succeeded, waiting for connection status change")
            // 更新成功，重置倒计时器为60秒
            MyLog.d("通知Activity重置倒计时器为60秒（升级成功）")
            stateListener.onProgressTimerReset()
        }
    }

    // DiscoveryListener 实现
    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
        MyLog.d("Discovered ${readers.size} readers")
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
                MyLog.d("Successfully connected to reader: ${reader.serialNumber}")
                connectedReader = reader
                connectionRetryCount = 0 // 重置连接重试计数器
                // 连接成功会通过ConnectionStatus自动更新UI
            }

            override fun onFailure(e: TerminalException) {
                MyLog.e("Failed to connect to reader", e)
                currentReader?.let { reader ->
                    retryConnection(reader, e)
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
                MyLog.e("Failed to retrieve payment intent", e)
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
                MyLog.w("Reader reconnect failed: ${reader.serialNumber}")
                handler.postDelayed({
                    startDiscovery()
                }, 10000)
            }

            override fun onReaderReconnectStarted(reader: Reader, cancelReconnect: Cancelable) {
                MyLog.d("Reader reconnect started: ${reader.serialNumber}")
            }

            override fun onReaderReconnectSucceeded(reader: Reader) {
                MyLog.d("Reader reconnect succeeded: ${reader.serialNumber}")
                connectedReader = reader
                // 重连成功会通过ConnectionStatus自动更新UI
            }
        }
    }

    // BluetoothReaderListener 实现
    override fun onReportReaderEvent(event: ReaderEvent) {
        MyLog.d("Reader event: $event")
        // 处理阅读器事件，如卡片插入、移除等
    }

    override fun onRequestReaderInput(options: ReaderInputOptions) {
        MyLog.d("Reader input requested: $options")
        // 处理阅读器输入请求
    }

    override fun onRequestReaderDisplayMessage(message: ReaderDisplayMessage) {
        MyLog.d("Reader display message: $message")
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
                    MyLog.d("Payment method collected")
                    updateDisplayState(DisplayState.RENTING, null)
                    confirmPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    MyLog.e("Failed to collect payment method", e)

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
                    MyLog.d("Payment processed successfully")
                    // 支付成功后调用租借接口
                    callRentalApiAfterPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    MyLog.e("Failed to process payment", e)
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
            MyLog.e("Failed to get location ID", e)
            null
        }
    }

    private fun getDeviceQrCode(): String {
        return try {
            PreferenceManager.getDeviceSno() ?: ""
        } catch (e: Exception) {
            MyLog.e("Failed to get device QR code", e)
            ""
        }
    }

    /**
     * 重试发现阅读器
     */
    private fun retryDiscovery(error: TerminalException) {
        if (discoveryRetryCount < MAX_DISCOVERY_RETRY_ATTEMPTS) {
            discoveryRetryCount++
            MyLog.d("重试发现阅读器，第${discoveryRetryCount}次尝试/$MAX_DISCOVERY_RETRY_ATTEMPTS")
            MyLog.d("发现错误: ${error.errorMessage}", error)
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
        } else {
            MyLog.e("发现阅读器失败，已达到最大重试次数: $MAX_DISCOVERY_RETRY_ATTEMPTS")
            MyLog.e("最终错误: ${error.errorMessage}", error)
            updateDisplayState(DisplayState.LOADING, null)
        }
    }

    /**
     * 重试连接阅读器
     */
    private fun retryConnection(reader: Reader, error: TerminalException) {
        if (connectionRetryCount < MAX_RETRY_ATTEMPTS) {
            connectionRetryCount++
            MyLog.d("Retrying connection, attempt $connectionRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                connectToReader(reader)
            }, RETRY_DELAY)
        } else {
            MyLog.e("Connection failed after $MAX_RETRY_ATTEMPTS attempts")
            updateDisplayState(DisplayState.CONNECT_READER_FAILED, null)
        }
    }

    /**
     * 重试支付
     */
    private fun retryPayment(error: TerminalException) {
        if (paymentRetryCount < MAX_RETRY_ATTEMPTS) {
            paymentRetryCount++
            MyLog.d("Retrying payment, attempt $paymentRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startPaymentCollection()
            }, RETRY_DELAY)
        } else {
            MyLog.e("Payment failed after $MAX_RETRY_ATTEMPTS attempts")
            updateDisplayState(DisplayState.ENTER_COLLECTION_METHOD_FAILED, null)
        }
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
     * 处理发现超时
     */
    private fun handleDiscoveryTimeout() {
        if (discoveryRetryCount < MAX_RETRY_ATTEMPTS) {
            discoveryRetryCount++
            MyLog.d("Discovery timeout, retrying attempt $discoveryRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            MyLog.e("Discovery failed after $MAX_RETRY_ATTEMPTS attempts due to timeout")
            updateDisplayState(DisplayState.LOADING, null)
        }
    }

    /**
     * 处理未找到阅读器
     */
    private fun handleNoReadersFound() {
        if (discoveryRetryCount < MAX_RETRY_ATTEMPTS) {
            discoveryRetryCount++
            MyLog.d("No readers found, retrying attempt $discoveryRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            MyLog.e("Discovery failed after $MAX_RETRY_ATTEMPTS attempts: no readers found")
            updateDisplayState(DisplayState.LOADING, null)
        }
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
