package com.stwpower.powertap.terminal

import android.content.Context
import android.util.Log
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
 * 统一状态管理器
 * 所有状态变化都通过统一入口更新DisplayState
 */
class StripeStateManager {

    // 当前的显示状态 - 这是唯一的UI状态源
    private var currentDisplayState: DisplayState = DisplayState.INITIALIZING

    // 状态监听器
    private var stateListener: StripeStateListener? = null
    
    interface StripeStateListener {
        fun onDisplayStateChanged(displayState: DisplayState)
    }

    fun setStateListener(listener: StripeStateListener) {
        this.stateListener = listener
    }

    /**
     * 统一的状态更新入口
     * 所有状态变化都通过这个方法更新DisplayState
     */
    fun updateDisplayState(newState: DisplayState) {
        if (currentDisplayState != newState) {
            currentDisplayState = newState
            Log.d("StripeStateManager", "DisplayState更新: $currentDisplayState")
            notifyStateChange()
        }
    }
    
    /**
     * 获取当前显示状态
     */
    fun getCurrentDisplayState(): DisplayState {
        return currentDisplayState
    }
    
    /**
     * 通知状态变化
     */
    private fun notifyStateChange() {
        stateListener?.onDisplayStateChanged(currentDisplayState)
    }
    
    /**
     * 重置到初始状态
     */
    fun reset() {
        updateDisplayState(DisplayState.INITIALIZING)
    }
}
