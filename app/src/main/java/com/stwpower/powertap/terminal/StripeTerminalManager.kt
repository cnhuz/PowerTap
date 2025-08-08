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
    private val stateListener: TerminalStateListener
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
        fun onStateChanged(state: TerminalState, progress: Int = 0)
        fun onPaymentSuccess(paymentIntent: PaymentIntent)
        fun onPaymentFailed(error: String)
    }

    private var currentState = TerminalState.INITIALIZING
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

    /**
     * 初始化 Terminal
     */
    fun initialize() {
        updateState(TerminalState.INITIALIZING)
        resetRetryCounters() // 重置所有重试计数器

        if (Terminal.isInitialized()) {
            Log.d(TAG, "Terminal already initialized")
            startDiscovery()
            return
        }

        // 检查权限
        if (!hasRequiredPermissions()) {
            updateState(TerminalState.REQUESTING_PERMISSIONS)
            // 权限检查失败，通知调用者需要请求权限
            stateListener.onPaymentFailed("Required permissions not granted")
            return
        }

        // 检查GPS
        if (!isGpsEnabled()) {
            updateState(TerminalState.INITIALIZATION_FAILED)
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
            updateState(TerminalState.INITIALIZATION_FAILED)
            stateListener.onPaymentFailed("Failed to initialize terminal: ${e.message}")
        }
    }

    /**
     * 开始发现阅读器
     */
    private fun startDiscovery() {
        updateState(TerminalState.DISCOVERING_READERS)
        
        // 获取位置ID
        val locationId = getLocationId()
        if (locationId.isNullOrEmpty()) {
            updateState(TerminalState.INITIALIZATION_FAILED)
            stateListener.onPaymentFailed("Location ID not configured")
            return
        }

        // 尝试USB发现，如果失败则尝试蓝牙
        val config = DiscoveryConfiguration(
            timeout = 0,
            discoveryMethod = DiscoveryMethod.USB,
            isSimulated = ConfigLoader.enableDebug, // 调试模式下使用模拟器
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
            if (currentState == TerminalState.DISCOVERING_READERS) {
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
        updateState(TerminalState.CONNECTING_READER)
        currentReader = reader // 保存当前尝试连接的阅读器

        val locationId = getLocationId()
        if (locationId.isNullOrEmpty()) {
            updateState(TerminalState.CONNECTION_FAILED)
            stateListener.onPaymentFailed("Location ID not configured")
            return
        }

        // 根据阅读器类型选择连接方法
        when (reader.deviceType) {
            DeviceType.CHIPPER_2X, DeviceType.CHIPPER_1X, DeviceType.WISECUBE -> {
                // USB 连接
                val config = ConnectionConfiguration.UsbConnectionConfiguration(
                    locationId,
                    autoReconnectOnUnexpectedDisconnect = true,
                    null // ReaderReconnectionListener
                )
                Terminal.getInstance().connectUsbReader(reader, config, this, createReaderCallback())
            }
            else -> {
                // 蓝牙连接
                val config = ConnectionConfiguration.BluetoothConnectionConfiguration(
                    locationId,
                    autoReconnectOnUnexpectedDisconnect = true,
                    null // ReaderReconnectionListener
                )
                Terminal.getInstance().connectBluetoothReader(reader, config, this, createReaderCallback())
            }
        }
    }

    /**
     * 开始支付流程
     */
    fun startPayment() {
        if (currentState != TerminalState.WAITING_FOR_CARD) {
            Log.w(TAG, "Cannot start payment in current state: $currentState")
            return
        }

        paymentJob = scope.launch {
            try {
                updateState(TerminalState.CREATING_PAYMENT_INTENT)
                
                // 创建支付意图
                val paymentIntentResult = withContext(Dispatchers.IO) {
                    MyApiClient.createPaymentIntent(getDeviceQrCode())
                }
                
                if (paymentIntentResult?.get("clientSecret") == null) {
                    updateState(TerminalState.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Failed to create payment intent")
                    return@launch
                }

                // 获取支付意图
                Terminal.getInstance().retrievePaymentIntent(
                    paymentIntentResult["clientSecret"]!!,
                    createPaymentIntentCallback()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start payment", e)
                updateState(TerminalState.PAYMENT_FAILED)
                stateListener.onPaymentFailed("Failed to start payment: ${e.message}")
            }
        }
    }

    /**
     * 取消当前操作
     */
    fun cancel() {
        discoveryCancelable?.cancel(object : Callback {
            override fun onSuccess() {
                Log.d(TAG, "Discovery cancelled")
            }
            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Failed to cancel discovery", e)
            }
        })

        collectCancelable?.cancel(object : Callback {
            override fun onSuccess() {
                Log.d(TAG, "Payment collection cancelled")
                updateState(TerminalState.PAYMENT_CANCELLED)
            }
            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Failed to cancel payment collection", e)
            }
        })

        paymentJob?.cancel()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        cancel()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        
        if (Terminal.isInitialized() && connectedReader != null) {
            Terminal.getInstance().disconnectReader(object : Callback {
                override fun onSuccess() {
                    Log.d(TAG, "Reader disconnected")
                }
                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Failed to disconnect reader", e)
                }
            })
        }
    }

    // TerminalListener 实现
    override fun onUnexpectedReaderDisconnect(reader: Reader) {
        Log.w(TAG, "Reader unexpectedly disconnected")
        updateState(TerminalState.CONNECTION_FAILED)
        stateListener.onPaymentFailed("Card reader disconnected")
    }

    override fun onConnectionStatusChange(status: ConnectionStatus) {
        Log.d(TAG, "Connection status changed: $status")
        when (status) {
            ConnectionStatus.CONNECTED -> {
                updateState(TerminalState.READER_CONNECTED)
                // 延迟一下再开始支付，让用户看到连接成功的状态
                handler.postDelayed({
                    startPayment()
                }, 1000)
            }
            ConnectionStatus.CONNECTING -> {
                updateState(TerminalState.CONNECTING_READER)
            }
            ConnectionStatus.NOT_CONNECTED -> {
                if (currentState != TerminalState.DISCOVERING_READERS) {
                    updateState(TerminalState.CONNECTION_FAILED)
                    stateListener.onPaymentFailed("Connection lost")
                }
            }
        }
    }

    override fun onPaymentStatusChange(status: PaymentStatus) {
        Log.d(TAG, "Payment status changed: $status")
        when (status) {
            PaymentStatus.READY -> {
                updateState(TerminalState.WAITING_FOR_CARD)
            }
            PaymentStatus.WAITING_FOR_INPUT -> {
                updateState(TerminalState.WAITING_FOR_CARD)
            }
            PaymentStatus.PROCESSING -> {
                updateState(TerminalState.PROCESSING_PAYMENT)
            }
            PaymentStatus.NOT_READY -> {
                // 保持当前状态或根据情况更新
            }
        }
    }

    override fun onStartInstallingUpdate(update: ReaderSoftwareUpdate, cancelable: Cancelable?) {
        Log.d(TAG, "Starting reader update")
        updateState(TerminalState.UPDATING_READER)
    }

    override fun onReportReaderSoftwareUpdateProgress(progress: Float) {
        Log.d(TAG, "Update progress: ${(progress * 100).toInt()}%")
        updateState(TerminalState.UPDATE_PROGRESS, (progress * 100).toInt())
    }

    override fun onFinishInstallingUpdate(update: ReaderSoftwareUpdate?, e: TerminalException?) {
        Log.d(TAG, "Update finished")
        if (e != null) {
            Log.e(TAG, "Update failed", e)
            updateState(TerminalState.CONNECTION_FAILED)
            stateListener.onPaymentFailed("Reader update failed: ${e.message}")
        } else {
            updateState(TerminalState.READER_CONNECTED)
            handler.postDelayed({
                startPayment()
            }, 1000)
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
                updateState(TerminalState.READER_CONNECTED)
            }

            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Failed to connect to reader", e)
                currentReader?.let { reader ->
                    retryConnection(reader, e)
                } ?: run {
                    updateState(TerminalState.CONNECTION_FAILED)
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
                Log.d(TAG, "Retrieved payment intent: ${paymentIntent.id}")
                currentPaymentIntent = paymentIntent
                collectPaymentMethod(paymentIntent)
            }

            override fun onFailure(e: TerminalException) {
                Log.e(TAG, "Failed to retrieve payment intent", e)
                updateState(TerminalState.PAYMENT_FAILED)
                stateListener.onPaymentFailed("Failed to retrieve payment intent: ${e.message}")
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
        updateState(TerminalState.STARTING_PAYMENT_COLLECTION)

        val collectConfig = CollectConfiguration.Builder()
            .skipTipping(false)
            .build()

        collectCancelable = Terminal.getInstance().collectPaymentMethod(
            paymentIntent,
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "Payment method collected")
                    updateState(TerminalState.CARD_DETECTED)
                    confirmPayment(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Failed to collect payment method", e)
                    updateState(TerminalState.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Failed to collect payment: ${e.message}")
                }
            },
            collectConfig
        )

        updateState(TerminalState.WAITING_FOR_CARD)
    }

    private fun confirmPayment(paymentIntent: PaymentIntent) {
        updateState(TerminalState.CONFIRMING_PAYMENT)

        Terminal.getInstance().processPayment(
            paymentIntent,
            object : PaymentIntentCallback {
                override fun onSuccess(paymentIntent: PaymentIntent) {
                    Log.d(TAG, "Payment processed successfully")
                    updateState(TerminalState.PAYMENT_SUCCESSFUL)
                    stateListener.onPaymentSuccess(paymentIntent)
                }

                override fun onFailure(e: TerminalException) {
                    Log.e(TAG, "Failed to process payment", e)
                    updateState(TerminalState.PAYMENT_FAILED)
                    stateListener.onPaymentFailed("Payment processing failed: ${e.message}")
                }
            }
        )
    }

    private fun updateState(newState: TerminalState, progress: Int = 0) {
        currentState = newState
        Log.d(TAG, "State changed to: $newState")
        stateListener.onStateChanged(newState, progress)
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
            updateState(TerminalState.RETRYING)

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Discovery failed after $MAX_RETRY_ATTEMPTS attempts")
            updateState(TerminalState.READER_NOT_FOUND)
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
            updateState(TerminalState.RETRYING)

            handler.postDelayed({
                connectToReader(reader)
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Connection failed after $MAX_RETRY_ATTEMPTS attempts")
            updateState(TerminalState.CONNECTION_FAILED)
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
            updateState(TerminalState.RETRYING)

            handler.postDelayed({
                startPayment()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Payment failed after $MAX_RETRY_ATTEMPTS attempts")
            updateState(TerminalState.PAYMENT_FAILED)
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
            updateState(TerminalState.RETRYING)

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Discovery failed after $MAX_RETRY_ATTEMPTS attempts due to timeout")
            updateState(TerminalState.READER_NOT_FOUND)
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
            updateState(TerminalState.RETRYING)

            handler.postDelayed({
                startDiscovery()
            }, RETRY_DELAY)
        } else {
            Log.e(TAG, "Discovery failed after $MAX_RETRY_ATTEMPTS attempts: no readers found")
            updateState(TerminalState.READER_NOT_FOUND)
            stateListener.onPaymentFailed("Failed to discover readers after $MAX_RETRY_ATTEMPTS attempts: no readers found")
        }
    }
}
