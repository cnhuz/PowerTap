# UI更新触发机制分析

## 完整的UI更新流程

### 1. 触发源 (数据变化)
UI更新的触发源主要有以下几种：

#### 🔄 Stripe SDK回调 (自动触发)
```kotlin
// StripeTerminalManager实现的Stripe监听器
override fun onConnectionStatusChange(status: ConnectionStatus) {
    stripeStateManager.updateConnectionStatus(status) // 触发UI更新
}

override fun onPaymentStatusChange(status: PaymentStatus) {
    stripeStateManager.updatePaymentStatus(status) // 触发UI更新
}
```

#### 🏢 业务逻辑变化 (手动触发)
```kotlin
// 各种业务场景中的手动状态更新
updateBusinessPhase(BusinessPhase.INITIALIZING)     // 初始化开始
updateBusinessPhase(BusinessPhase.CALLING_RENTAL_API) // 调用API
updateBusinessPhase(BusinessPhase.RENTAL_SUCCESS)   // 租借成功
updateBusinessPhase(BusinessPhase.PAYMENT_FAILED)   // 各种失败
```

#### ⚙️ 收集付款方式状态 (手动触发)
```kotlin
stripeStateManager.setPaymentCollectionStarted(true)  // 开始收集
stripeStateManager.setPaymentCollectionStarted(false) // 取消收集
```

### 2. 状态管理层 (StripeStateManager)

#### 状态更新方法
```kotlin
// 每个更新方法都会触发notifyStateChange()
fun updateConnectionStatus(status: ConnectionStatus) {
    if (connectionStatus != status) {
        connectionStatus = status
        notifyStateChange() // 🔥 触发UI更新
    }
}

fun updatePaymentStatus(status: PaymentStatus) {
    if (paymentStatus != status) {
        paymentStatus = status
        notifyStateChange() // 🔥 触发UI更新
    }
}

fun updateBusinessPhase(phase: BusinessPhase) {
    if (businessPhase != phase) {
        businessPhase = phase
        notifyStateChange() // 🔥 触发UI更新
    }
}
```

#### 核心通知方法
```kotlin
private fun notifyStateChange() {
    val displayState = getDisplayState() // 计算当前显示状态
    stateListener?.onDisplayStateChanged(displayState) // 通知UI层
    stateListener?.onStripeStateChanged(connectionStatus, paymentStatus, businessPhase)
}
```

#### 状态计算逻辑
```kotlin
private fun getDisplayState(): DisplayState {
    // 业务状态优先
    when (businessPhase) {
        BusinessPhase.INITIALIZING -> return DisplayState.INITIALIZING
        BusinessPhase.CALLING_RENTAL_API -> return DisplayState.PROCESSING_RENTAL
        BusinessPhase.RENTAL_SUCCESS -> return DisplayState.RENTAL_SUCCESSFUL
        BusinessPhase.RENTAL_FAILED -> return DisplayState.RENTAL_FAILED
        BusinessPhase.PAYMENT_FAILED -> return DisplayState.PAYMENT_FAILED
        BusinessPhase.NONE -> {
            // 继续检查Stripe状态
        }
    }
    
    // Stripe状态
    return when (connectionStatus) {
        ConnectionStatus.NOT_CONNECTED -> DisplayState.DISCOVERING_READERS
        ConnectionStatus.CONNECTING -> DisplayState.CONNECTING_READER
        ConnectionStatus.CONNECTED -> {
            when (paymentStatus) {
                PaymentStatus.READY -> {
                    if (paymentCollectionStarted) {
                        DisplayState.WAITING_FOR_CARD
                    } else {
                        DisplayState.READY_FOR_PAYMENT
                    }
                }
                PaymentStatus.WAITING_FOR_INPUT -> DisplayState.WAITING_FOR_CARD
                PaymentStatus.PROCESSING -> DisplayState.PROCESSING_PAYMENT
                else -> DisplayState.READER_CONNECTED
            }
        }
    }
}
```

### 3. 中间层 (StripeTerminalManager)

#### 监听器桥接
```kotlin
// 在initialize()中设置监听器桥接
stripeStateManager.setStateListener(object : StripeStateManager.StripeStateListener {
    override fun onDisplayStateChanged(displayState: DisplayState) {
        stateListener.onDisplayStateChanged(displayState) // 转发到Activity
    }
    
    override fun onStripeStateChanged(connection, payment, business) {
        Log.d(TAG, "Stripe状态变化: connection=$connection, payment=$payment, business=$business")
    }
})
```

### 4. UI层 (TerminalPaymentActivity)

#### 接收状态变化
```kotlin
class TerminalPaymentActivity : AppCompatActivity(), StripeTerminalManager.TerminalStateListener {
    
    override fun onDisplayStateChanged(displayState: DisplayState) {
        runOnUiThread {
            updateUIForDisplayState(displayState) // 🎨 更新UI
        }
    }
}
```

#### UI更新执行
```kotlin
private fun updateUIForDisplayState(displayState: DisplayState) {
    Log.d("TerminalPayment", "更新UI为状态: $displayState (UIType: ${displayState.uiType})")
    
    when (displayState.uiType) {
        UIType.LOADING -> {
            showLoadingState()           // 显示加载环+文字
            loadingText.text = displayState.getFormattedText(this)
            // ... 设置按钮状态
        }
        
        UIType.TAP_TO_PAY -> {
            showCompletedState()         // 显示文字+图片
            loadingText.text = displayState.getFormattedText(this)
            // ... 设置文字颜色和按钮状态
        }
        
        UIType.MESSAGE -> {
            showMessage(displayState.getFormattedText(this)) // 显示白色粗体文字
            // ... 设置按钮状态
        }
    }
}
```

## 具体触发时机

### 🚀 应用启动流程
```
1. TerminalPaymentActivity.onCreate()
   → terminalManager.initialize()
   → updateBusinessPhase(INITIALIZING)
   → notifyStateChange()
   → onDisplayStateChanged(INITIALIZING)
   → updateUIForDisplayState() → UIType.LOADING

2. Terminal初始化完成
   → updateBusinessPhase(NONE)
   → startDiscovery()
   → notifyStateChange()
   → onDisplayStateChanged(DISCOVERING_READERS)
   → updateUIForDisplayState() → UIType.LOADING
```

### 🔌 连接流程
```
3. Stripe SDK回调
   → onConnectionStatusChange(CONNECTING)
   → updateConnectionStatus(CONNECTING)
   → notifyStateChange()
   → onDisplayStateChanged(CONNECTING_READER)
   → updateUIForDisplayState() → UIType.LOADING

4. 连接成功
   → onConnectionStatusChange(CONNECTED)
   → updateConnectionStatus(CONNECTED)
   → notifyStateChange()
   → onDisplayStateChanged(READER_CONNECTED)
   → updateUIForDisplayState() → UIType.LOADING
```

### 💳 支付流程
```
5. 支付准备就绪
   → onPaymentStatusChange(READY)
   → updatePaymentStatus(READY)
   → notifyStateChange()
   → onDisplayStateChanged(READY_FOR_PAYMENT)
   → updateUIForDisplayState() → UIType.TAP_TO_PAY

6. 开始收集付款方式
   → startPaymentCollection()
   → setPaymentCollectionStarted(true)
   → notifyStateChange()
   → onDisplayStateChanged(WAITING_FOR_CARD)
   → updateUIForDisplayState() → UIType.TAP_TO_PAY (蓝色文字)

7. 用户刷卡
   → onPaymentStatusChange(WAITING_FOR_INPUT)
   → updatePaymentStatus(WAITING_FOR_INPUT)
   → notifyStateChange()
   → onDisplayStateChanged(WAITING_FOR_CARD)
   → updateUIForDisplayState() → UIType.TAP_TO_PAY (蓝色文字)

8. 处理支付
   → onPaymentStatusChange(PROCESSING)
   → updatePaymentStatus(PROCESSING)
   → notifyStateChange()
   → onDisplayStateChanged(PROCESSING_PAYMENT)
   → updateUIForDisplayState() → UIType.LOADING
```

### 🏢 业务流程
```
9. 支付成功
   → collectPaymentMethod.onSuccess()
   → updateBusinessPhase(PAYMENT_SUCCESS)
   → notifyStateChange()
   → onDisplayStateChanged(PAYMENT_SUCCESSFUL)
   → updateUIForDisplayState() → UIType.MESSAGE

10. 调用租借API
    → callRentalApiAfterPayment()
    → updateBusinessPhase(CALLING_RENTAL_API)
    → notifyStateChange()
    → onDisplayStateChanged(PROCESSING_RENTAL)
    → updateUIForDisplayState() → UIType.LOADING

11. 租借成功
    → API响应成功
    → updateBusinessPhase(RENTAL_SUCCESS)
    → notifyStateChange()
    → onDisplayStateChanged(RENTAL_SUCCESSFUL)
    → updateUIForDisplayState() → UIType.MESSAGE
```

## 触发频率和性能

### 高频触发场景
- Stripe SDK状态变化 (连接、支付状态)
- 用户操作 (刷卡、取消)
- 网络请求结果

### 性能优化机制
```kotlin
// 状态去重，避免重复更新
if (connectionStatus != status) {
    connectionStatus = status
    notifyStateChange()
}

// UI线程保护
runOnUiThread {
    updateUIForDisplayState(displayState)
}
```

## 调试和监控

### 日志跟踪
```kotlin
Log.d("StripeTerminalManager", "Business phase changed to: $phase")
Log.d("StripeStateManager", "Stripe状态变化: connection=$connection, payment=$payment, business=$business")
Log.d("TerminalPayment", "更新UI为状态: $displayState (UIType: ${displayState.uiType})")
```

### 状态报告
```kotlin
fun getStateReport(): String {
    return """
        === Stripe状态报告 ===
        连接状态: $connectionStatus
        支付状态: $paymentStatus
        业务阶段: $businessPhase
        显示状态: ${getDisplayState()}
    """.trimIndent()
}
```

## 总结

UI更新触发机制的特点：

1. **多源触发** - Stripe SDK回调 + 业务逻辑变化
2. **统一管理** - 通过StripeStateManager集中处理
3. **状态计算** - 根据优先级计算最终显示状态
4. **监听器模式** - 通过回调通知UI层更新
5. **线程安全** - 确保UI更新在主线程执行
6. **性能优化** - 状态去重，避免无效更新

这个机制确保了UI能够及时、准确地反映当前的系统状态。
