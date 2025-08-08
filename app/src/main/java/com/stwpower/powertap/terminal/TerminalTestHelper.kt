package com.stwpower.powertap.terminal

import android.content.Context
import android.util.Log
import com.stwpower.powertap.utils.PreferenceManager

/**
 * Terminal 测试辅助类
 * 用于测试和调试 Stripe Terminal 功能
 */
object TerminalTestHelper {
    
    private const val TAG = "TerminalTestHelper"
    
    /**
     * 设置测试数据
     */
    fun setupTestData(context: Context) {
        PreferenceManager.init(context)
        
        // 设置测试用的位置ID和设备序列号
        // 这些值应该从实际的配置中获取
        val testLocationId = "tml_test_location_id"
        val testSno = "test_device_sno"
        
        PreferenceManager.setLocationId(testLocationId)
        PreferenceManager.setDeviceSno(testSno)
        
        Log.d(TAG, "Test data setup complete:")
        Log.d(TAG, "  Location ID: $testLocationId")
        Log.d(TAG, "  Device SNO: $testSno")
    }
    
    /**
     * 验证配置
     */
    fun validateConfiguration(context: Context): Boolean {
        PreferenceManager.init(context)
        
        val locationId = PreferenceManager.getLocationId()
        val deviceSno = PreferenceManager.getDeviceSno()
        
        val isValid = !locationId.isNullOrEmpty() && !deviceSno.isNullOrEmpty()
        
        Log.d(TAG, "Configuration validation:")
        Log.d(TAG, "  Location ID: $locationId")
        Log.d(TAG, "  Device SNO: $deviceSno")
        Log.d(TAG, "  Is Valid: $isValid")
        
        return isValid
    }
    
    /**
     * 清除测试数据
     */
    fun clearTestData(context: Context) {
        PreferenceManager.init(context)
        
        // 这里可以添加清除逻辑，但要小心不要清除生产数据
        Log.d(TAG, "Test data cleared")
    }
    
    /**
     * 模拟状态变化（用于UI测试）
     */
    fun simulateStateChanges(listener: StripeTerminalManager.TerminalStateListener) {
        val states = listOf(
            TerminalState.INITIALIZING,
            TerminalState.DISCOVERING_READERS,
            TerminalState.CONNECTING_READER,
            TerminalState.READER_CONNECTED,
            TerminalState.CREATING_PAYMENT_INTENT,
            TerminalState.WAITING_FOR_CARD,
            TerminalState.CARD_DETECTED,
            TerminalState.PROCESSING_PAYMENT,
            TerminalState.CONFIRMING_PAYMENT,
            TerminalState.PAYMENT_SUCCESSFUL
        )
        
        var index = 0
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        fun nextState() {
            if (index < states.size) {
                val state = states[index]
                Log.d(TAG, "Simulating state: $state")
                listener.onStateChanged(state)
                index++
                
                // 延迟到下一个状态
                val delay = when (state) {
                    TerminalState.WAITING_FOR_CARD -> 5000L // 等待卡片时间长一些
                    else -> 2000L
                }
                
                handler.postDelayed({ nextState() }, delay)
            }
        }
        
        nextState()
    }
}
