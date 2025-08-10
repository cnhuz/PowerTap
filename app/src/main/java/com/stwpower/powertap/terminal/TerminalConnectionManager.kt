package com.stwpower.powertap.terminal

import android.content.Context
import android.util.Log
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.Terminal

/**
 * Terminal连接管理器单例
 * 用于在不同Activity间共享Terminal连接状态
 */
object TerminalConnectionManager {
    
    private const val TAG = "powertap"
    
    private var terminalManager: StripeTerminalManager? = null
    private var isInitialized = false
    
    /**
     * 获取或创建TerminalManager实例
     */
    fun getTerminalManager(context: Context, stateListener: StripeTerminalManager.TerminalStateListener): StripeTerminalManager {
        if (terminalManager == null) {
            Log.d(TAG, "创建新的TerminalManager实例")
            terminalManager = StripeTerminalManager(context, stateListener)
            isInitialized = false
        } else {
            Log.d(TAG, "复用现有的TerminalManager实例")
            // 更新状态监听器
            terminalManager!!.updateStateListener(stateListener)
        }
        
        return terminalManager!!
    }
    
    /**
     * 初始化Terminal（如果尚未初始化）
     */
    fun initializeIfNeeded(context: Context, stateListener: StripeTerminalManager.TerminalStateListener) {
        val manager = getTerminalManager(context, stateListener)
        
        if (!isInitialized) {
            Log.d(TAG, "首次初始化Terminal")
            manager.initialize()
            isInitialized = true
        } else {
            Log.d(TAG, "Terminal已初始化，检查连接状态")
            // 如果已经初始化，检查连接状态并恢复
            manager.resumePaymentCollection()
        }
    }
    
    /**
     * 暂停支付收集（保持连接）
     */
    fun pausePaymentCollection() {
        Log.d(TAG, "暂停支付收集")
        terminalManager?.pausePaymentCollection()
    }
    
    /**
     * 恢复支付收集
     */
    fun resumePaymentCollection() {
        Log.d(TAG, "恢复支付收集")
        terminalManager?.resumePaymentCollection()
    }
    
    /**
     * 完全断开连接（应用退出时）
     */
    fun disconnect() {
        Log.d(TAG, "完全断开Terminal连接")
        terminalManager?.disconnect()
        terminalManager = null
        isInitialized = false
    }
    
    /**
     * 获取当前连接状态
     */
    fun getConnectionStatus(): ConnectionStatus? {
        return try {
            if (Terminal.isInitialized()) {
                Terminal.getInstance().connectionStatus
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取连接状态失败", e)
            null
        }
    }
    
    /**
     * 获取当前支付状态
     */
    fun getPaymentStatus(): PaymentStatus? {
        return try {
            if (Terminal.isInitialized()) {
                Terminal.getInstance().paymentStatus
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取支付状态失败", e)
            null
        }
    }
    
    /**
     * 检查是否有活跃的连接
     */
    fun hasActiveConnection(): Boolean {
        val connectionStatus = getConnectionStatus()
        val paymentStatus = getPaymentStatus()
        
        Log.d(TAG, "检查活跃连接: 连接状态=$connectionStatus, 支付状态=$paymentStatus")
        
        return connectionStatus == ConnectionStatus.CONNECTED
    }
    
    /**
     * 获取连接状态报告
     */
    fun getStatusReport(): String {
        return try {
            val connectionStatus = getConnectionStatus()
            val paymentStatus = getPaymentStatus()
            val connectedReader = if (Terminal.isInitialized()) {
                Terminal.getInstance().connectedReader?.serialNumber
            } else {
                null
            }
            
            """
            === Terminal连接状态报告 ===
            初始化状态: $isInitialized
            连接状态: $connectionStatus
            支付状态: $paymentStatus
            连接的阅读器: $connectedReader
            TerminalManager实例: ${if (terminalManager != null) "存在" else "不存在"}
            """.trimIndent()
        } catch (e: Exception) {
            "获取状态报告失败: ${e.message}"
        }
    }
}
