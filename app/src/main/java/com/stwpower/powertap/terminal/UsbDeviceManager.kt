package com.stwpower.powertap.terminal

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.stwpower.powertap.utils.MyLog

/**
 * USB设备管理器
 * 统一管理USB设备的连接、权限和状态监听
 */
class UsbDeviceManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UsbDeviceManager"
        private const val ACTION_USB_PERMISSION = "com.stwpower.powertap.USB_PERMISSION"
    }
    
    private var usbManager: UsbManager? = null
    private var usbReceiver: BroadcastReceiver? = null
    private var isUsbReceiverRegistered = false
    private var deviceListener: UsbDeviceListener? = null
    
    interface UsbDeviceListener {
        fun onDeviceAttached(device: UsbDevice)
        fun onDeviceDetached(device: UsbDevice)
        fun onPermissionGranted(device: UsbDevice)
        fun onPermissionDenied(device: UsbDevice)
    }
    
    fun setDeviceListener(listener: UsbDeviceListener) {
        this.deviceListener = listener
    }
    
    /**
     * 初始化USB设备管理器
     */
    fun initialize() {
        try {
            if (isUsbReceiverRegistered) {
                MyLog.d("USB设备管理器已初始化，跳过重复初始化")
                return
            }
            
            usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            MyLog.d("USB管理器初始化成功")
            
            // 创建USB设备连接状态变化的广播接收器
            usbReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            MyLog.d("USB设备已连接: ${device?.deviceName}, VendorId: ${device?.vendorId}, ProductId: ${device?.productId}")
                            
                            device?.let { deviceListener?.onDeviceAttached(it) }
                        }
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            MyLog.d("USB设备已断开: ${device?.deviceName}, VendorId: ${device?.vendorId}, ProductId: ${device?.productId}")
                            
                            device?.let { deviceListener?.onDeviceDetached(it) }
                        }
                        ACTION_USB_PERMISSION -> {
                            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                            }
                            val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            
                            if (permissionGranted) {
                                MyLog.d("USB设备权限已授予: ${device?.deviceName}")
                                device?.let { deviceListener?.onPermissionGranted(it) }
                            } else {
                                MyLog.w("USB设备权限被拒绝: ${device?.deviceName}")
                                device?.let { deviceListener?.onPermissionDenied(it) }
                            }
                        }
                    }
                }
            }
            
            // 注册广播接收器
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(ACTION_USB_PERMISSION)
            }
            
            context.registerReceiver(usbReceiver, filter)
            isUsbReceiverRegistered = true
            MyLog.d("USB设备监听器注册成功")
            
            // 检查当前连接的USB设备
            val deviceList = usbManager?.deviceList
            if (deviceList != null && deviceList.isNotEmpty()) {
                MyLog.d("当前连接的USB设备数量: ${deviceList.size}")
                for ((name, device) in deviceList) {
                    MyLog.d("USB设备: $name, VendorId: ${device.vendorId}, ProductId: ${device.productId}")
                }
            } else {
                MyLog.d("当前没有连接USB设备")
            }
        } catch (e: Exception) {
            MyLog.e("USB管理器初始化失败", e)
        }
    }
    
    /**
     * 检查并请求USB设备权限
     */
    fun checkAndRequestUsbPermission(device: UsbDevice) {
        try {
            usbManager?.let { manager ->
                if (manager.hasPermission(device)) {
                    MyLog.d("已拥有USB设备权限: ${device.deviceName}")
                    deviceListener?.onPermissionGranted(device)
                } else {
                    MyLog.d("请求USB设备权限: ${device.deviceName}")
                    
                    // 创建权限请求Intent
                    val permissionIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_MUTABLE
                        } else {
                            0
                        }
                    )
                    
                    // 请求权限
                    manager.requestPermission(device, permissionIntent)
                }
            }
        } catch (e: Exception) {
            MyLog.e("检查USB设备权限时出错", e)
        }
    }
    
    /**
     * 检查是否是Stripe阅读器设备
     */
    fun isStripeReaderDevice(device: UsbDevice): Boolean {
        // Stripe阅读器的VendorId通常是5538 (0x15A2)
        return device.vendorId == 5538 || device.vendorId == 0x15A2
    }
    
    /**
     * 注销USB设备管理器
     */
    fun destroy() {
        if (isUsbReceiverRegistered && usbReceiver != null) {
            try {
                context.unregisterReceiver(usbReceiver)
                isUsbReceiverRegistered = false
                usbReceiver = null
                MyLog.d("USB设备监听器注销成功")
            } catch (e: Exception) {
                MyLog.e("注销USB设备监听器时出错", e)
            }
        } else {
            MyLog.d("USB设备监听器未注册或已注销")
        }
    }
}