package com.stwpower.powertap.managers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.stwpower.powertap.utils.MyLog

/**
 * 系统级权限管理器
 * 用于系统应用的权限管理，跳过运行时权限请求
 */
object SystemPermissionManager {
    
    private const val TAG = "powertap"
    
    /**
     * 检查是否为系统应用
     */
    fun isSystemApp(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(context.packageName, 0)
            val flags = packageInfo.applicationInfo?.flags
            
            // 检查是否为系统应用或系统更新应用
            val isSystem = (flags?.and(android.content.pm.ApplicationInfo.FLAG_SYSTEM)) != 0
            val isSystemUpdate = (flags?.and(android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            
            MyLog.d("App flags: $flags")
            MyLog.d("Is system app: $isSystem")
            MyLog.d("Is system update: $isSystemUpdate")
            
            isSystem || isSystemUpdate
        } catch (e: Exception) {
            MyLog.e("Failed to check system app status", e)
            false
        }
    }
    
    /**
     * 使用系统权限授予所有必要权限
     */
    fun grantSystemPermissions(context: Context): Boolean {
        if (!isSystemApp(context)) {
            MyLog.w("Not a system app, cannot grant system permissions")
            return false
        }
        
        return try {
            val packageName = context.packageName
            
            // 授予位置权限
            grantPermission(packageName, "android.permission.ACCESS_FINE_LOCATION")
            grantPermission(packageName, "android.permission.ACCESS_COARSE_LOCATION")
            
            // 授予蓝牙权限
            grantPermission(packageName, "android.permission.BLUETOOTH")
            grantPermission(packageName, "android.permission.BLUETOOTH_ADMIN")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                grantPermission(packageName, "android.permission.BLUETOOTH_CONNECT")
                grantPermission(packageName, "android.permission.BLUETOOTH_SCAN")
            }
            
            // 授予存储权限
            grantPermission(packageName, "android.permission.READ_EXTERNAL_STORAGE")
            grantPermission(packageName, "android.permission.WRITE_EXTERNAL_STORAGE")
            
            MyLog.d("System permissions granted successfully")
            true
        } catch (e: Exception) {
            MyLog.e("Failed to grant system permissions", e)
            false
        }
    }
    
    /**
     * 使用pm命令授予权限
     */
    private fun grantPermission(packageName: String, permission: String): Boolean {
        return try {
            // 方法1: 直接使用pm命令（不需要su）
            val command1 = arrayOf("pm", "grant", packageName, permission)
            val process1 = Runtime.getRuntime().exec(command1)
            val exitCode1 = process1.waitFor()

            if (exitCode1 == 0) {
                MyLog.d("Granted permission via pm: $permission")
                return true
            }

            // 方法2: 使用su权限
            val command2 = "pm grant $packageName $permission"
            val process2 = Runtime.getRuntime().exec(arrayOf("su", "-c", command2))
            val exitCode2 = process2.waitFor()

            if (exitCode2 == 0) {
                MyLog.d("Granted permission via su: $permission")
                true
            } else {
                MyLog.w("Failed to grant permission: $permission (exit codes: $exitCode1, $exitCode2)")
                false
            }
        } catch (e: Exception) {
            MyLog.e("Exception granting permission: $permission", e)
            false
        }
    }
    
    /**
     * 启用GPS（系统级操作）
     */
    fun enableGPS(context: Context): Boolean {
        if (!isSystemApp(context)) {
            MyLog.w("Not a system app, cannot enable GPS")
            return false
        }
        
        return try {
            // 使用系统设置启用GPS
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
            )
            
            MyLog.d("GPS enabled successfully")
            true
        } catch (e: Exception) {
            MyLog.e("Failed to enable GPS", e)
            
            // 备用方法：使用shell命令
            try {
                val command = "settings put secure location_providers_allowed +gps,+network"
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val exitCode = process.waitFor()
                
                if (exitCode == 0) {
                    MyLog.d("GPS enabled via shell command")
                    true
                } else {
                    MyLog.w("Failed to enable GPS via shell command")
                    false
                }
            } catch (shellException: Exception) {
                MyLog.e("Failed to enable GPS via shell command", shellException)
                false
            }
        }
    }
    
    /**
     * 检查所有权限是否已授予（系统级检查）
     */
    fun checkAllPermissionsGranted(context: Context): Boolean {
        val requiredPermissions = PermissionManager.ALL_PERMISSIONS
        
        val allGranted = requiredPermissions.all { permission ->
            val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            MyLog.d("Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
            granted
        }
        
        val gpsEnabled = PermissionManager.isGpsEnabled(context)
        MyLog.d("GPS enabled: $gpsEnabled")
        
        val result = allGranted && gpsEnabled
        MyLog.d("All permissions and GPS ready: $result")
        
        return result
    }
    
    /**
     * 系统级权限初始化
     */
    fun initializeSystemPermissions(context: Context): Boolean {
        MyLog.d("Initializing system permissions...")
        
        if (!isSystemApp(context)) {
            MyLog.w("Not a system app, falling back to regular permission management")
            return false
        }
        
        // 授予所有必要权限
        val permissionsGranted = grantSystemPermissions(context)
        
        // 启用GPS
        val gpsEnabled = enableGPS(context)
        
        // 等待一下让设置生效
        Thread.sleep(1000)
        
        // 验证权限状态
        val allReady = checkAllPermissionsGranted(context)
        
        MyLog.d("System permission initialization result:")
        MyLog.d("  Permissions granted: $permissionsGranted")
        MyLog.d("  GPS enabled: $gpsEnabled")
        MyLog.d("  All ready: $allReady")
        
        return allReady
    }
}
