package com.stwpower.powertap.terminal

import android.content.Context
import com.stwpower.powertap.R

/**
 * Terminal 状态枚举
 * 定义了 Stripe Terminal 在支付流程中的各种状态
 */
enum class TerminalState(
    val stringResId: Int,
    val isLoading: Boolean = true,
    val canGoBack: Boolean = false
) {
    // 初始化阶段
    INITIALIZING(R.string.initializing_terminal, true, false),
    REQUESTING_PERMISSIONS(R.string.requesting_permissions, true, false),

    // 扫描和连接阶段
    DISCOVERING_READERS(R.string.scanning_readers, true, false),
    CONNECTING_READER(R.string.connecting_reader, true, false),
    READER_CONNECTED(R.string.reader_connected, true, false),

    // 更新阶段
    UPDATING_READER(R.string.updating_reader, true, false),
    UPDATE_PROGRESS(R.string.updating_reader, true, false), // 特殊处理进度

    // 支付准备阶段
    CREATING_PAYMENT_INTENT(R.string.preparing_payment, true, false),
    STARTING_PAYMENT_COLLECTION(R.string.starting_payment, true, false),

    // 等待用户操作阶段
    WAITING_FOR_CARD(R.string.waiting_for_card, false, true),
    CARD_DETECTED(R.string.card_detected, true, false),

    // 支付处理阶段
    PROCESSING_PAYMENT(R.string.processing_payment_terminal, true, false),
    CONFIRMING_PAYMENT(R.string.confirming_payment, true, false),

    // 完成状态
    PAYMENT_SUCCESSFUL(R.string.payment_successful, false, true),
    PAYMENT_FAILED(R.string.payment_failed, false, true),

    // 错误状态
    INITIALIZATION_FAILED(R.string.initialization_failed, false, true),
    READER_NOT_FOUND(R.string.reader_not_found, false, true),
    CONNECTION_FAILED(R.string.connection_failed, false, true),
    PAYMENT_CANCELLED(R.string.payment_cancelled, false, true),
    TIMEOUT(R.string.operation_timeout, false, true),

    // 重试状态
    RETRYING(R.string.retrying, true, false);
    
    /**
     * 检查是否为错误状态
     */
    fun isError(): Boolean {
        return when (this) {
            PAYMENT_FAILED, INITIALIZATION_FAILED, READER_NOT_FOUND, 
            CONNECTION_FAILED, PAYMENT_CANCELLED, TIMEOUT -> true
            else -> false
        }
    }
    
    /**
     * 检查是否为成功状态
     */
    fun isSuccess(): Boolean {
        return this == PAYMENT_SUCCESSFUL
    }
    
    /**
     * 检查是否为最终状态（成功或失败）
     */
    fun isFinal(): Boolean {
        return isSuccess() || isError()
    }
    
    /**
     * 获取格式化的显示文本（支持进度百分比）
     */
    fun getFormattedText(context: Context, progress: Int = 0): String {
        return if (this == UPDATE_PROGRESS) {
            "${context.getString(stringResId)}: $progress%"
        } else {
            context.getString(stringResId)
        }
    }
}
