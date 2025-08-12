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

    // 默认状态
    LOADING(R.string.loading, UIType.LOADING, false),
    // 初始化失败
    INIT_FAILED(R.string.init_failed, UIType.MESSAGE, true),
    // 扫描阅读器
    SCANNING_READER(R.string.scanning_reader, UIType.LOADING, false),
    // 连接阅读器
    CONNECTING_READER(R.string.connecting_reader, UIType.LOADING, false),
    // 连接阅读器失败
    CONNECT_READER_FAILED(R.string.connect_reader_failed, UIType.MESSAGE, true),
    // 开始升级阅读器
    START_UPGRADING_READER(R.string.start_upgrading_reader, UIType.LOADING, false),
    // 升级中
    UPGRADING(R.string.upgrading, UIType.LOADING, false),
    // 升级失败
    UPGRADE_FAILED(R.string.upgrade_failed, UIType.MESSAGE, true),
    // 进入收集方式
    ENTER_COLLECTION_METHOD(R.string.enter_collection_method, UIType.LOADING, false),
    // 进入收集方式失败
    ENTER_COLLECTION_METHOD_FAILED(R.string.enter_collection_method_failed, UIType.MESSAGE, true),
    // 收集付款方式
    COLLECTING_PAYMENT_METHOD(R.string.collecting_payment_method, UIType.TAP_TO_PAY, true),
    // 收集付款方式失败
    COLLECT_PAYMENT_METHOD_FAILED(R.string.collect_payment_method_failed, UIType.MESSAGE, true),
    // 租借中
    RENTING(R.string.renting, UIType.LOADING, true),
    // 租借成功
    RENT_SUCCESS(R.string.rent_success, UIType.MESSAGE, true),
    // 租借失败
    RENT_FAILED(R.string.rent_failed, UIType.MESSAGE, true);

    /**
     * 获取格式化的显示文本
     */
    fun getFormattedText(context: Context,vararg formatArgs: Any?): String {
        return context.getString(stringResId, formatArgs)
    }
}

/**
 * 统一状态管理器
 * 所有状态变化都通过统一入口更新DisplayState
 */
class StripeStateManager {

    // 当前的显示状态 - 这是唯一的UI状态源
    private var currentDisplayState: DisplayState = DisplayState.LOADING

    // 状态监听器
    private var stateListener: StripeStateListener? = null
    
    interface StripeStateListener {
        fun onDisplayStateChanged(displayState: DisplayState, vararg message: Any?) // 统一状态管理监听器
    }

    fun setStateListener(listener: StripeStateListener) {
        this.stateListener = listener
    }

    /**
     * 统一的状态更新入口
     * 所有状态变化都通过这个方法更新DisplayState
     */
    fun updateDisplayState(newState: DisplayState, vararg message: Any?) {
        if (currentDisplayState != newState) {
            currentDisplayState = newState
            Log.d("StripeStateManager", "DisplayState更新: $currentDisplayState")
            // 通知状态变化
            stateListener?.onDisplayStateChanged(currentDisplayState, message)
        }
    }
    
    /**
     * 获取当前显示状态
     */
    fun getCurrentDisplayState(): DisplayState {
        return currentDisplayState
    }
}
