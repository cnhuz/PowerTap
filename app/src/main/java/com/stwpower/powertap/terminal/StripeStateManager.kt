package com.stwpower.powertap.terminal

import android.content.Context
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.ReaderDisplayMessage
import com.stwpower.powertap.R

/**
 * 业务阶段枚举
 * 用于表示我们应用的业务流程状态
 */
enum class BusinessPhase {
    NONE,                    // 无业务流程
    INITIALIZING,           // 初始化中
    CALLING_RENTAL_API,     // 调用租借API
    RENTAL_SUCCESS,         // 租借成功
    RENTAL_FAILED,          // 租借失败
    PAYMENT_SUCCESS,        // 支付成功（收集付款方式成功）
    PAYMENT_FAILED,         // 支付失败
    CANCELLED               // 用户取消
}

/**
 * UI类型枚举
 */
enum class UIType {
    LOADING,        // 进度环+文字
    TAP_TO_PAY,     // 文字+图片
    MESSAGE         // 只展示白色粗体文字
}

/**
 * 显示状态
 * 用于UI显示的状态枚举
 */
enum class DisplayState(
    val stringResId: Int,
    val uiType: UIType,
    val canGoBack: Boolean = false
) {
    // 初始化和连接
    INITIALIZING(R.string.initializing_terminal, UIType.LOADING, false),
    DISCOVERING_READERS(R.string.scanning_readers, UIType.LOADING, false),
    CONNECTING_READER(R.string.connecting_reader, UIType.LOADING, false),
    READER_CONNECTED(R.string.reader_connected, UIType.LOADING, false),
    
    // 支付流程
    READY_FOR_PAYMENT(R.string.reader_connected, UIType.TAP_TO_PAY, true),
    WAITING_FOR_CARD(R.string.waiting_for_card, UIType.TAP_TO_PAY, true),
    PROCESSING_PAYMENT(R.string.processing_payment_terminal, UIType.LOADING, false),
    
    // 业务流程
    PROCESSING_RENTAL(R.string.calling_rental_api, UIType.LOADING, false),
    
    // 结果状态
    PAYMENT_SUCCESSFUL(R.string.message_payment_successful, UIType.MESSAGE, true),
    RENTAL_SUCCESSFUL(R.string.rental_successful, UIType.MESSAGE, true),
    RENTAL_FAILED(R.string.rental_failed, UIType.MESSAGE, true),
    PAYMENT_FAILED(R.string.payment_failed, UIType.MESSAGE, true),
    
    // 错误状态
    CONNECTION_FAILED(R.string.connection_failed, UIType.MESSAGE, true),
    READER_NOT_FOUND(R.string.reader_not_found, UIType.MESSAGE, true),
    INITIALIZATION_FAILED(R.string.initialization_failed, UIType.MESSAGE, true),
    CANCELLED(R.string.payment_cancelled, UIType.MESSAGE, true),
    TIMEOUT(R.string.operation_timeout, UIType.MESSAGE, true);
    
    /**
     * 检查是否为错误状态
     */
    fun isError(): Boolean {
        return when (this) {
            PAYMENT_FAILED, RENTAL_FAILED, CONNECTION_FAILED,
            READER_NOT_FOUND, INITIALIZATION_FAILED, CANCELLED, TIMEOUT -> true
            else -> false
        }
    }

    /**
     * 检查是否为成功状态
     */
    fun isSuccess(): Boolean {
        return this == PAYMENT_SUCCESSFUL || this == RENTAL_SUCCESSFUL
    }

    /**
     * 检查是否为最终状态
     */
    fun isFinal(): Boolean {
        return isSuccess() || isError()
    }

    /**
     * 检查是否为加载状态
     */
    fun isLoading(): Boolean {
        return uiType == UIType.LOADING
    }

    /**
     * 检查是否为刷卡状态
     */
    fun isTapToPay(): Boolean {
        return uiType == UIType.TAP_TO_PAY
    }

    /**
     * 检查是否为消息状态
     */
    fun isMessage(): Boolean {
        return uiType == UIType.MESSAGE
    }
    
    /**
     * 获取格式化的显示文本
     */
    fun getFormattedText(context: Context): String {
        return context.getString(stringResId)
    }
}

/**
 * Stripe状态管理器
 * 直接使用Stripe SDK的原生状态，只添加业务层状态
 */
class StripeStateManager {
    
    // Stripe原生状态
    private var connectionStatus: ConnectionStatus = ConnectionStatus.NOT_CONNECTED
    private var paymentStatus: PaymentStatus = PaymentStatus.NOT_READY
    private var readerMessage: ReaderDisplayMessage? = null
    
    // 业务状态
    private var businessPhase: BusinessPhase = BusinessPhase.NONE

    // 是否已经开始收集付款方式
    private var paymentCollectionStarted: Boolean = false

    // 状态监听器
    private var stateListener: StripeStateListener? = null
    
    interface StripeStateListener {
        fun onDisplayStateChanged(displayState: DisplayState)
        fun onStripeStateChanged(
            connectionStatus: ConnectionStatus,
            paymentStatus: PaymentStatus,
            businessPhase: BusinessPhase
        )
    }
    
    fun setStateListener(listener: StripeStateListener) {
        this.stateListener = listener
    }
    
    /**
     * 更新Stripe连接状态
     */
    fun updateConnectionStatus(status: ConnectionStatus) {
        if (connectionStatus != status) {
            connectionStatus = status
            notifyStateChange()
        }
    }
    
    /**
     * 更新Stripe支付状态
     */
    fun updatePaymentStatus(status: PaymentStatus) {
        if (paymentStatus != status) {
            paymentStatus = status
            notifyStateChange()
        }
    }
    
    /**
     * 更新读卡器显示消息
     */
    fun updateReaderMessage(message: ReaderDisplayMessage?) {
        if (readerMessage != message) {
            readerMessage = message
            notifyStateChange()
        }
    }
    
    /**
     * 更新业务阶段
     */
    fun updateBusinessPhase(phase: BusinessPhase) {
        if (businessPhase != phase) {
            businessPhase = phase
            notifyStateChange()
        }
    }

    /**
     * 设置收集付款方式开始状态
     */
    fun setPaymentCollectionStarted(started: Boolean) {
        if (paymentCollectionStarted != started) {
            paymentCollectionStarted = started
            notifyStateChange()
        }
    }
    
    /**
     * 获取当前显示状态
     */
    fun getCurrentDisplayState(): DisplayState {
        return getDisplayState()
    }
    
    /**
     * 获取当前Stripe状态
     */
    fun getCurrentStripeStates(): Triple<ConnectionStatus, PaymentStatus, BusinessPhase> {
        return Triple(connectionStatus, paymentStatus, businessPhase)
    }
    
    /**
     * 通知状态变化
     */
    private fun notifyStateChange() {
        val displayState = getDisplayState()
        stateListener?.onDisplayStateChanged(displayState)
        stateListener?.onStripeStateChanged(connectionStatus, paymentStatus, businessPhase)
    }
    
    /**
     * 根据当前状态计算显示状态
     */
    private fun getDisplayState(): DisplayState {
        // 业务状态优先
        when (businessPhase) {
            BusinessPhase.INITIALIZING -> return DisplayState.INITIALIZING
            BusinessPhase.CALLING_RENTAL_API -> return DisplayState.PROCESSING_RENTAL
            BusinessPhase.PAYMENT_SUCCESS -> return DisplayState.PAYMENT_SUCCESSFUL
            BusinessPhase.RENTAL_SUCCESS -> return DisplayState.RENTAL_SUCCESSFUL
            BusinessPhase.RENTAL_FAILED -> return DisplayState.RENTAL_FAILED
            BusinessPhase.PAYMENT_FAILED -> return DisplayState.PAYMENT_FAILED
            BusinessPhase.CANCELLED -> return DisplayState.CANCELLED
            BusinessPhase.NONE -> {
                // 继续检查Stripe状态
            }
        }
        
        // 然后是Stripe状态
        return when (connectionStatus) {
            ConnectionStatus.NOT_CONNECTED -> DisplayState.DISCOVERING_READERS
            ConnectionStatus.CONNECTING -> DisplayState.CONNECTING_READER
            ConnectionStatus.CONNECTED -> {
                when (paymentStatus) {
                    PaymentStatus.NOT_READY -> DisplayState.READER_CONNECTED
                    PaymentStatus.READY -> {
                        // 区分准备就绪和正在等待刷卡
                        if (paymentCollectionStarted) {
                            DisplayState.WAITING_FOR_CARD
                        } else {
                            DisplayState.READY_FOR_PAYMENT
                        }
                    }
                    PaymentStatus.WAITING_FOR_INPUT -> DisplayState.WAITING_FOR_CARD
                    PaymentStatus.PROCESSING -> DisplayState.PROCESSING_PAYMENT
                }
            }
        }
    }
    
    /**
     * 重置到初始状态
     */
    fun reset() {
        connectionStatus = ConnectionStatus.NOT_CONNECTED
        paymentStatus = PaymentStatus.NOT_READY
        readerMessage = null
        businessPhase = BusinessPhase.NONE
        paymentCollectionStarted = false
        notifyStateChange()
    }
    
    /**
     * 获取状态报告（用于调试）
     */
    fun getStateReport(): String {
        return """
            === Stripe状态报告 ===
            连接状态: $connectionStatus
            支付状态: $paymentStatus
            读卡器消息: $readerMessage
            业务阶段: $businessPhase
            显示状态: ${getDisplayState()}
        """.trimIndent()
    }
}
