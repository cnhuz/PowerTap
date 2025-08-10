package com.stwpower.powertap.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 权限管理器
 * 负责管理应用所需的所有权限
 */
object PermissionManager {
    
    private const val TAG = "PermissionManager"
    
    /**
     * Terminal支付所需的权限
     */
    val TERMINAL_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }
    
    /**
     * 应用基础权限
     */
    val BASIC_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    
    /**
     * 所有权限
     */
    val ALL_PERMISSIONS = TERMINAL_PERMISSIONS + BASIC_PERMISSIONS
    
    /**
     * 检查是否有所有必需的权限
     */
    fun hasAllPermissions(context: Context): Boolean {
        return ALL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查Terminal权限
     */
    fun hasTerminalPermissions(context: Context): Boolean {
        return TERMINAL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 获取缺失的权限
     */
    fun getMissingPermissions(context: Context, permissions: Array<String> = ALL_PERMISSIONS): List<String> {
        return permissions.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查GPS是否启用
     */
    fun isGpsEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        return try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check GPS status", e)
            false
        }
    }
    
    /**
     * 检查Terminal是否可用（权限+GPS）
     */
    fun isTerminalReady(context: Context): Boolean {
        val hasPermissions = hasTerminalPermissions(context)
        val hasGps = isGpsEnabled(context)
        
        Log.d(TAG, "Terminal readiness check:")
        Log.d(TAG, "  Has permissions: $hasPermissions")
        Log.d(TAG, "  GPS enabled: $hasGps")
        
        return hasPermissions && hasGps
    }
    
    /**
     * 获取权限状态报告
     */
    fun getPermissionReport(context: Context): String {
        val report = StringBuilder()
        report.appendLine("=== Permission Status Report ===")
        
        // 检查Terminal权限
        report.appendLine("Terminal Permissions:")
        TERMINAL_PERMISSIONS.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            val status = if (granted) "✓" else "✗"
            report.appendLine("  $status $permission")
        }
        
        // 检查基础权限
        report.appendLine("\nBasic Permissions:")
        BASIC_PERMISSIONS.forEach { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            val status = if (granted) "✓" else "✗"
            report.appendLine("  $status $permission")
        }
        
        // 检查GPS
        val gpsEnabled = isGpsEnabled(context)
        val gpsStatus = if (gpsEnabled) "✓" else "✗"
        report.appendLine("\nSystem Status:")
        report.appendLine("  $gpsStatus GPS Enabled")
        
        // 总结
        val terminalReady = isTerminalReady(context)
        val readyStatus = if (terminalReady) "✓ READY" else "✗ NOT READY"
        report.appendLine("\nTerminal Status: $readyStatus")
        
        return report.toString()
    }
    
    /**
     * 权限请求结果监听器
     */
    interface PermissionResultListener {
        fun onPermissionsGranted()
        fun onPermissionsDenied(deniedPermissions: List<String>)
        fun onGpsRequired()
    }
}
