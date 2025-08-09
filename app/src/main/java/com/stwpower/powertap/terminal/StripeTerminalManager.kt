package com.stwpower.powertap.terminal

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
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
import com.stwpower.powertap.ConfigLoader
import com.stwpower.powertap.data.api.MyApiClient
import com.stwpower.powertap.data.provider.TokenProvider
import com.stwpower.powertap.utils.PreferenceManager
import kotlinx.coroutines.*

/**
 * Stripe Terminal 管理器
 * 负责管理 Terminal 的完整生命周期和支付流程
 */
class StripeTerminalManager(
    private val context: Context,
    private var stateListener: TerminalStateListener
) : TerminalListener, DiscoveryListener, BluetoothReaderListener, UsbReaderListener {

    companion object {
        private const val TAG = "StripeTerminalManager"
        private const val DISCOVERY_TIMEOUT = 30000L // 30秒
        private const val CONNECTION_TIMEOUT = 20000L // 20秒
        private const val PAYMENT_TIMEOUT = 60000L // 60秒
        private const val MAX_RETRY_ATTEMPTS = 3 // 最大重试次数
        private const val RETRY_DELAY = 5000L // 重试延迟5秒
    }

    interface TerminalStateListener {
        fun onDisplayStateChanged(displayState: DisplayState)
        fun onPaymentSuccess(paymentIntent: PaymentIntent)
        fun onPaymentFailed(error: String, isCancelled: Boolean = false)
        fun onRentalSuccess(paymentIntent: PaymentIntent, message: String)
        fun onRentalFailed(paymentIntent: PaymentIntent, error: String)
    }

    // 使用新的状态管理器
    private val stripeStateManager = StripeStateManager()
    private var discoveryJob: Job? = null
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

    // 状态保持相关
    private var isReaderConnected = false
    private var shouldMaintainConnection = true

    /**
     * 初始化 Terminal
     */
    fun initialize() {
        // 设置状态管理器监听器
        stripeStateManager.setStateListener(object : StripeStateManager.StripeStateListener {
            override fun onDisplayStateChanged(displayState: DisplayState) {
                stateListener.onDisplayStateChanged(displayState)
            }

            override fun onStripeStateChanged(
                connectionStatus: ConnectionStatus,
                paymentStatus: PaymentStatus,
                businessPhase: BusinessPhase
            ) {
                Log.d(TAG, "Stripe状态变化: connection=$connectionStatus, payment=$paymentStatus, business=$businessPhase")
            }
        })

        updateBusinessPhase(BusinessPhase.INITIALIZING)
        resetRetryCounters() // 重置所有重试计数器

        if (Terminal.isInitialized()) {
            Log.d(TAG, "Terminal already initialized")

            // 检查是否已有连接的阅读器
            if (checkExistingConnection()) {
                Log.d(TAG, "Found existing reader connection, skipping discovery")
                return
            }

            startDiscovery()
            return
        }

        // 检查权限
        if (!hasRequiredPermissions()) {
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            // 权限检查失败，通知调用者需要请求权限
            stateListener.onPaymentFailed("Required permissions not granted")
            return
        }

        // 检查GPS
        if (!isGpsEnabled()) {
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("GPS must be enabled for Terminal to work")
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
            Log.d(TAG, "Terminal initialized successfully")
            startDiscovery()
        } catch (e: TerminalException) {
            Log.e(TAG, "Failed to initialize Terminal", e)
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Failed to initialize terminal: ${e.message}")
        }
    }

    /**
     * 开始发现阅读器
     */
    private fun startDiscovery() {
        // 发现读卡器是Stripe的技术状态，会通过ConnectionStatus自动更新

        // 检查SNO
        val sno = getDeviceQrCode()
        if (sno.isEmpty()) {
            Log.e(TAG, "SNO（二维码编号）为空，10s后重试")
            handler.postDelayed({
                startDiscovery()
            }, 10000)
            return
        }

        // 获取位置ID
        val locationId = getLocationId()
        if (locationId.isNullOrEmpty()) {
            Log.e(TAG, "该设备的locationId为空，10s重试")
            handler.postDelayed({
                startDiscovery()
            }, 10000)
            return
        }

        Log.d(TAG, "开始扫描阅读器，位置id：$locationId")

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
                    Log.d(TAG, "Discovery completed successfully")
                }

                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Discovery failed", e)
                    retryDiscovery(e)
                }
            }
        )

        // 设置发现超时
        handler.postDelayed({
            // 检查是否还在发现阶段（连接状态为NOT_CONNECTED）
            if (Terminal.getInstance().connectionStatus == ConnectionStatus.NOT_CONNECTED) {
                discoveryCancelable?.cancel(object : Callback {
                    override fun onSuccess() {
                        handleDiscoveryTimeout()
                    }
                    override fun onFailure(e: TerminalException) {
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
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Location ID not configured")
            return
        }

        Log.d(TAG, "连接到阅读器: ${reader.serialNumber}")

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
        Log.d(TAG, "开始支付收集流程")

        scope.launch(Dispatchers.IO) {
            try {
                // 创建支付意图是支付流程的一部分，保持当前状态

                // 创建支付意图
                val sno = getDeviceQrCode()
                val paymentIntentResult = MyApiClient.createPaymentIntent(sno)

                withContext(Dispatchers.Main) {
                    if (paymentIntentResult?.get("clientSecret") != null) {
                        Log.d(TAG, "服务端创建PaymentIntent成功，取回PaymentIntent")

                        // 获取支付意图
                        Terminal.getInstance().retrievePaymentIntent(
                            paymentIntentResult["clientSecret"]!!,
                            createPaymentIntentCallback()
                        )
                    } else {
                        updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
                        stateListener.onPaymentFailed("Failed to create payment intent", false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start payment collection", e)
                withContext(Dispatchers.Main) {
                    updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Failed to start payment: ${e.message}", false)
                }
            }
        }
    }

    /**
     * 安全地取消当前操作
     */
    fun cancel() {
        // 安全地取消发现操作
        discoveryCancelable?.let { cancelable ->
            try {
                if (!cancelable.isCompleted) {
                    cancelable.cancel(object : Callback {
                        override fun onSuccess() {
                            Log.d(TAG, "Discovery cancelled successfully")
                        }
                        override fun onFailure(e: TerminalException) {
                            Log.w(TAG, "Failed to cancel discovery: ${e.message}")
                        }
                    })
                } else {
                    Log.d(TAG, "Discovery operation already completed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception while cancelling discovery: ${e.message}")
            }
            discoveryCancelable = null
        }

        // 安全地取消支付收集操作
        collectCancelable?.let { cancelable ->
            try {
                if (!cancelable.isCompleted) {
                    cancelable.cancel(object : Callback {
                        override fun onSuccess() {
                            Log.d(TAG, "Payment collection cancelled successfully")
                        }
                        override fun onFailure(e: TerminalException) {
                            Log.w(TAG, "Failed to cancel payment collection: ${e.message}")
                        }
                    })
                } else {
                    Log.d(TAG, "Payment collection operation already completed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception while cancelling payment collection: ${e.message}")
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

            Log.d(TAG, "检查现有连接状态:")
            Log.d(TAG, "  连接状态: $connectionStatus")
            Log.d(TAG, "  支付状态: $paymentStatus")
            Log.d(TAG, "  连接的阅读器: ${connectedReader?.serialNumber}")

            if (connectionStatus == ConnectionStatus.CONNECTED && connectedReader != null) {
                this.connectedReader = connectedReader
                isReaderConnected = true

                when (paymentStatus) {
                    PaymentStatus.READY -> {
                        Log.d(TAG, "阅读器已连接且支付状态为READY，需要重新开始支付收集流程")
                        // 支付状态会通过PaymentStatus自动更新UI

                        // 延迟一下再开始支付收集，确保UI状态正确
                        handler.postDelayed({
                            Log.d(TAG, "开始重新收集支付方式")
                            startPaymentCollection()
                        }, 1000)

                        return true
                    }
                    PaymentStatus.NOT_READY -> {
                        Log.d(TAG, "阅读器已连接但支付状态为NOT_READY，等待状态变化")
                        // 支付状态会通过PaymentStatus自动更新UI
                        return true
                    }
                    PaymentStatus.WAITING_FOR_INPUT -> {
                        Log.d(TAG, "阅读器已连接且正在等待输入，直接进入等待刷卡状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                        return true
                    }
                    else -> {
                        Log.d(TAG, "阅读器已连接，支付状态: $paymentStatus，显示连接状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                        return true
                    }
                }
            }

            Log.d(TAG, "没有找到有效的阅读器连接")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查现有连接时发生异常", e)
            false
        }
    }

    /**
     * 暂停支付收集（保持连接）
     */
    fun pausePaymentCollection() {
        Log.d(TAG, "暂停支付收集，保持阅读器连接")

        try {
            // 安全地取消当前的支付收集
            collectCancelable?.let { cancelable ->
                try {
                    if (!cancelable.isCompleted) {
                        cancelable.cancel(object : Callback {
                            override fun onSuccess() {
                                Log.d(TAG, "支付收集已取消")
                            }
                            override fun onFailure(e: TerminalException) {
                                Log.w(TAG, "取消支付收集失败: ${e.message}")
                            }
                        })
                    } else {
                        Log.d(TAG, "支付收集操作已完成，无需取消")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "取消支付收集时发生异常: ${e.message}")
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

            Log.d(TAG, "支付收集已暂停，阅读器连接保持")

        } catch (e: Exception) {
            Log.e(TAG, "暂停支付收集时发生异常", e)
        }
    }

    /**
     * 恢复支付收集
     */
    fun resumePaymentCollection() {
        Log.d(TAG, "恢复支付收集")

        try {
            val connectionStatus = Terminal.getInstance().connectionStatus
            val paymentStatus = Terminal.getInstance().paymentStatus

            Log.d(TAG, "当前状态 - 连接: $connectionStatus, 支付: $paymentStatus")

            if (connectionStatus == ConnectionStatus.CONNECTED) {
                when (paymentStatus) {
                    PaymentStatus.READY -> {
                        Log.d(TAG, "支付状态为READY，开始支付收集流程")
                        // 支付状态会通过PaymentStatus自动更新UI

                        // 延迟一下再开始支付收集
                        handler.postDelayed({
                            startPaymentCollection()
                        }, 1000)
                    }
                    PaymentStatus.NOT_READY -> {
                        Log.d(TAG, "支付状态为NOT_READY，等待状态变化")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    PaymentStatus.WAITING_FOR_INPUT -> {
                        Log.d(TAG, "支付状态为WAITING_FOR_INPUT，直接进入等待刷卡状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                    else -> {
                        Log.d(TAG, "当前支付状态: $paymentStatus，显示连接状态")
                        // 支付状态会通过PaymentStatus自动更新UI
                    }
                }
            } else {
                Log.w(TAG, "阅读器未连接，重新开始发现流程")
                startDiscovery()
            }

        } catch (e: Exception) {
            Log.e(TAG, "恢复支付收集时发生异常", e)
            startDiscovery()
        }
    }

    /**
     * 清理资源（保持连接）
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up StripeTerminalManager (maintaining connection)")

        // 暂停支付收集但保持连接
        pausePaymentCollection()

        Log.d(TAG, "StripeTerminalManager cleanup completed (connection maintained)")
    }

    /**
     * 完全断开连接（仅在应用退出时调用）
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting StripeTerminalManager completely")
        shouldMaintainConnection = false

        cancel()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)

        if (Terminal.isInitialized() && connectedReader != null) {
            Terminal.getInstance().disconnectReader(object : Callback {
                override fun onSuccess() {
                    Log.d(TAG, "Reader disconnected successfully")
                }
                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Failed to disconnect reader", e)
                }
            })
        }

        // 重置状态
        connectedReader = null
        isReaderConnected = false

        Log.d(TAG, "StripeTerminalManager disconnected completely")
    }

    // TerminalListener 实现
    override fun onUnexpectedReaderDisconnect(reader: Reader) {
        Log.w(TAG, "阅读器意外断开连接: ${reader.deviceType}")
        currentReader = null

        // 根据状态管理逻辑：阅读器断开连接需要重新扫描连接
        Log.d(TAG, "阅读器断开连接，开始重新扫描连接")
        // 断开连接会通过ConnectionStatus自动更新UI

        // 重新开始扫描和连接流程
        startDiscovery()
    }

    override fun onConnectionStatusChange(status: ConnectionStatus) {
        Log.d(TAG, "Connection status changed: $status")
        stripeStateManager.updateConnectionStatus(status)

        when (status) {
            ConnectionStatus.CONNECTED -> {
                Log.d(TAG, "阅读器连接成功")
                // 连接成功后，等待支付状态变为READY
                // 不需要手动开始支付，等待onPaymentStatusChange回调
            }
            ConnectionStatus.CONNECTING -> {
                Log.d(TAG, "正在连接阅读器")
            }
            ConnectionStatus.NOT_CONNECTED -> {
                Log.w(TAG, "阅读器断开连接")
                // 可以根据需要设置错误的业务阶段
                // updateBusinessPhase(BusinessPhase.CONNECTION_ERROR)
            }
        }
    }

    override fun onPaymentStatusChange(status: PaymentStatus) {
        Log.d(TAG, "Payment status changed: $status")
        stripeStateManager.updatePaymentStatus(status)

        when (status) {
            PaymentStatus.NOT_READY -> {
                // 如果处于NOT_READY，30s后还是处于NOT_READY，进行重连
                handler.postDelayed({
                    if (Terminal.getInstance().paymentStatus == PaymentStatus.NOT_READY) {
                        Log.d(TAG, "支付状态超过30s处于NOT_READY，重新扫描")
                        startDiscovery()
                    }
                }, 30000)
            }
            PaymentStatus.READY -> {
                Log.d(TAG, "支付状态为READY，准备接收支付")

                // 检查当前业务状态，如果不是租借完成，直接进入收集付款方式
                val (_, _, businessPhase) = stripeStateManager.getCurrentStripeStates()
                if (businessPhase != BusinessPhase.RENTAL_SUCCESS) {
                    Log.d(TAG, "当前业务状态: $businessPhase，直接进入收集付款方式")
                    startPaymentCollection()
                } else {
                    Log.d(TAG, "租借已完成，保持当前状态")
                }
            }
            PaymentStatus.WAITING_FOR_INPUT -> {
                Log.d(TAG, "支付状态为WAITING_FOR_INPUT，等待用户输入")
            }
            PaymentStatus.PROCESSING -> {
                Log.d(TAG, "支付状态为PROCESSING，正在处理支付")
            }
        }
    }

    override fun onStartInstallingUpdate(update: ReaderSoftwareUpdate, cancelable: Cancelable?) {
        Log.d(TAG, "Starting reader update")
        // 更新过程可以考虑添加到BusinessPhase，暂时保持现状
    }

    override fun onReportReaderSoftwareUpdateProgress(progress: Float) {
        Log.d(TAG, "Update progress: ${(progress * 100).toInt()}%")
        // 更新进度可以考虑添加到BusinessPhase，暂时保持现状
    }

    override fun onFinishInstallingUpdate(update: ReaderSoftwareUpdate?, e: TerminalException?) {
        Log.d(TAG, "Update finished")
        if (e != null) {
            Log.e(TAG, "Update failed", e)
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Reader update failed: ${e.message}")
        } else {
            // 更新成功，连接状态会通过ConnectionStatus自动更新
            // 连接成功后等待支付状态变化，不需要手动调用
        }
    }

    // DiscoveryListener 实现
    override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
        Log.d(TAG, "Discovered ${readers.size} readers")
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
                Log.d(TAG, "Successfully connected to reader: ${reader.serialNumber}")
                connectedReader = reader
                connectionRetryCount = 0 // 重置连接重试计数器
                // 连接成功会通过ConnectionStatus自动更新UI
            }

            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Failed to connect to reader", e)
                currentReader?.let { reader ->
                    retryConnection(reader, e)
                } ?: run {
                    updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Failed to connect to reader: ${e.message}")
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
                Log.d(TAG, "取回PaymentIntent成功，paymentIntent: $paymentIntent，开始收集付款方式")
                currentPaymentIntent = paymentIntent
                collectPaymentMethod(paymentIntent)
            }

            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Failed to retrieve payment intent", e)
                updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
                stateListener.onPaymentFailed("Failed to retrieve payment intent: ${e.message}", false)
            }
        }
    }

    /**
     * 创建重连监听器
     */
    private fun createReaderReconnectionListener(): ReaderReconnectionListener {
        return object : ReaderReconnectionListener {
            override fun onReaderReconnectFailed(reader: Reader) {
                Log.w(TAG, "Reader reconnect failed: ${reader.serialNumber}")
                handler.postDelayed({
                    startDiscovery()
                }, 10000)
            }

            override fun onReaderReconnectStarted(reader: Reader, cancelReconnect: Cancelable) {
                Log.d(TAG, "Reader reconnect started: ${reader.serialNumber}")
            }

            override fun onReaderReconnectSucceeded(reader: Reader) {
                Log.d(TAG, "Reader reconnect succeeded: ${reader.serialNumber}")
                connectedReader = reader
                // 重连成功会通过ConnectionStatus自动更新UI
            }
        }
    }

    // BluetoothReaderListener 实现
    override fun onReportReaderEvent(event: ReaderEvent) {
        Log.d(TAG, "Reader event: $event")
        // 处理阅读器事件，如卡片插入、移除等
    }

    override fun onRequestReaderInput(options: ReaderInputOptions) {
        Log.d(TAG, "Reader input requested: $options")
        // 处理阅读器输入请求
    }

    override fun onRequestReaderDisplayMessage(message: ReaderDisplayMessage) {
        Log.d(TAG, "Reader display message: $message")
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
                    Log.d(TAG, "Payment method collected")
                    updateBusinessPhase(BusinessPhase.PAYMENT_SUCCESS)
                    confirmPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Failed to collect payment method", e)

                    // 检查是否是用户取消操作
                    val isCancelled = e.errorCode == TerminalException.TerminalErrorCode.CANCELED ||
                                     e.message?.contains("cancel", ignoreCase = true) == true ||
                                     e.message?.contains("abort", ignoreCase = true) == true

                    updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Failed to collect payment: ${e.message}", isCancelled)
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
                    Log.d(TAG, "Payment processed successfully")
                    // 支付成功后调用租借接口
                    callRentalApiAfterPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Failed to process payment", e)
                    updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Payment processing failed: ${e.message}", false)
                }
            }
        )
    }

    /**
     * 更新状态监听器
     */
    fun updateStateListener(newListener: TerminalStateListener) {
        Log.d(TAG, "更新状态监听器")
        stateListener = newListener
        // 立即通知当前显示状态
        stateListener.onDisplayStateChanged(stripeStateManager.getCurrentDisplayState())
    }

    /**
     * 支付成功后调用租借接口
     */
    private fun callRentalApiAfterPayment(paymentIntent: PaymentIntent) {
        Log.d(TAG, "=== 开始调用租借接口 ===")

        updateBusinessPhase(BusinessPhase.CALLING_RENTAL_API)

        scope.launch {
            try {
                // 获取设备信息
                val qrCode = PreferenceManager.getQrCode()

                if (qrCode.isNullOrEmpty()) {
                    Log.e(TAG, "设备序列号为空")
                    updateBusinessPhase(BusinessPhase.RENTAL_FAILED)
                    stateListener.onRentalFailed(paymentIntent, "设备序列号未配置")
                    return@launch
                }

                Log.d(TAG, "qrCode: $qrCode")
                Log.d(TAG, "PaymentIntent ID: ${paymentIntent.id}")
                Log.d(TAG, "appSecretKey: ${ConfigLoader.secretKey}")

                // 检查PaymentIntent的必要字段
                if (paymentIntent.id.isNullOrEmpty()) {
                    Log.e(TAG, "PaymentIntent ID为空")
                    updateBusinessPhase(BusinessPhase.RENTAL_FAILED)
                    stateListener.onRentalFailed(paymentIntent, "PaymentIntent ID无效")
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

                Log.d(TAG, "租借接口调用完成")
                Log.d(TAG, "响应结果: success=${response.success}, code=${response.code}, message=${response.message}")
                Log.d(TAG, "响应数据: ${response.data}")

                // 直接使用响应中的成功状态和消息
                val success = response.success
                val message = response.message

                if (success) {
                    Log.d(TAG, "租借接口调用成功: $message")
                    updateBusinessPhase(BusinessPhase.RENTAL_SUCCESS)
                    stateListener.onRentalSuccess(paymentIntent, message)
                } else {
                    Log.e(TAG, "租借接口调用失败: $message")
                    updateBusinessPhase(BusinessPhase.RENTAL_FAILED)
                    stateListener.onRentalFailed(paymentIntent, message)
                }

            } catch (e: Exception) {
                Log.e(TAG, "调用租借接口时发生异常", e)
                updateBusinessPhase(BusinessPhase.RENTAL_FAILED)
                stateListener.onRentalFailed(paymentIntent, "网络请求失败: ${e.message}")
            }
        }
    }

    /**
     * 更新业务阶段
     */
    private fun updateBusinessPhase(phase: BusinessPhase) {
        Log.d(TAG, "Business phase changed to: $phase")
        stripeStateManager.updateBusinessPhase(phase)
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
            Log.e(TAG, "Failed to get location ID", e)
            null
        }
    }

    private fun getDeviceQrCode(): String {
        return try {
            PreferenceManager.getDeviceSno() ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device QR code", e)
            ""
        }
    }

    /**
     * 重试发现阅读器
     */
    private fun retryDiscovery(error: TerminalException) {
        if (discoveryRetryCount < MAX_RETRY_ATTEMPTS) {
            discoveryRetryCount++
            Log.d(TAG, "Retrying discovery, attempt $discoveryRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Discovery failed after $MAX_RETRY_ATTEMPTS attempts")
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Failed to discover readers after $MAX_RETRY_ATTEMPTS attempts: ${error.message}")
        }
    }

    /**
     * 重试连接阅读器
     */
    private fun retryConnection(reader: Reader, error: TerminalException) {
        if (connectionRetryCount < MAX_RETRY_ATTEMPTS) {
            connectionRetryCount++
            Log.d(TAG, "Retrying connection, attempt $connectionRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                connectToReader(reader)
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Connection failed after $MAX_RETRY_ATTEMPTS attempts")
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Failed to connect to reader after $MAX_RETRY_ATTEMPTS attempts: ${error.message}")
        }
    }

    /**
     * 重试支付
     */
    private fun retryPayment(error: TerminalException) {
        if (paymentRetryCount < MAX_RETRY_ATTEMPTS) {
            paymentRetryCount++
            Log.d(TAG, "Retrying payment, attempt $paymentRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startPaymentCollection()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Payment failed after $MAX_RETRY_ATTEMPTS attempts")
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Payment failed after $MAX_RETRY_ATTEMPTS attempts: ${error.message}")
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
            Log.d(TAG, "Discovery timeout, retrying attempt $discoveryRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Discovery failed after $MAX_RETRY_ATTEMPTS attempts due to timeout")
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Failed to discover readers after $MAX_RETRY_ATTEMPTS attempts: timeout")
        }
    }

    /**
     * 处理未找到阅读器
     */
    private fun handleNoReadersFound() {
        if (discoveryRetryCount < MAX_RETRY_ATTEMPTS) {
            discoveryRetryCount++
            Log.d(TAG, "No readers found, retrying attempt $discoveryRetryCount/$MAX_RETRY_ATTEMPTS")
            // 重试过程保持当前状态

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Discovery failed after $MAX_RETRY_ATTEMPTS attempts: no readers found")
            updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)
            stateListener.onPaymentFailed("Failed to discover readers after $MAX_RETRY_ATTEMPTS attempts: no readers found")
        }
    }

    /**
     * 获取当前显示状态
     */
    fun getCurrentDisplayState(): DisplayState {
        return stripeStateManager.getCurrentDisplayState()
    }

    /**
     * 检查是否有支付正在进行中
     */
    fun isPaymentInProgress(): Boolean {
        val (connectionStatus, paymentStatus, businessPhase) = stripeStateManager.getCurrentStripeStates()

        // 检查业务阶段
        when (businessPhase) {
            BusinessPhase.CALLING_RENTAL_API,
            BusinessPhase.PAYMENT_SUCCESS -> return true
            BusinessPhase.RENTAL_SUCCESS,
            BusinessPhase.RENTAL_FAILED,
            BusinessPhase.PAYMENT_FAILED,
            BusinessPhase.CANCELLED -> return false
            else -> {
                // 检查Stripe状态
                return connectionStatus == ConnectionStatus.CONNECTED &&
                       (paymentStatus == PaymentStatus.WAITING_FOR_INPUT ||
                        paymentStatus == PaymentStatus.PROCESSING)
            }
        }
    }
}
