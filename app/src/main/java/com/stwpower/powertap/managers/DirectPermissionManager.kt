package com.stwpower.powertap.managers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

/**
 * 直接权限管理器
 * 使用ADB命令直接授予权限，跳过运行时请求
 */
object DirectPermissionManager {
    
    private const val TAG = "DirectPermissionManager"
    
    /**
     * 直接授予所有必要权限
     */
    fun grantAllPermissions(context: Context): Boolean {
        Log.d(TAG, "Starting direct permission grant...")
        
        val packageName = context.packageName
        val permissions = arrayOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        
        var successCount = 0
        
        permissions.forEach { permission ->
            if (grantPermission(packageName, permission)) {
                successCount++
            }
        }
        
        Log.d(TAG, "Granted $successCount/${permissions.size} permissions")
        
        // 启用GPS
        val gpsEnabled = enableGPS()
        Log.d(TAG, "GPS enabled: $gpsEnabled")
        
        return successCount == permissions.size && gpsEnabled
    }
    
    /**
     * 授予单个权限
     */
    private fun grantPermission(packageName: String, permission: String): Boolean {
        return try {
            Log.d(TAG, "Granting permission: $permission")
            
            // 方法1: 直接pm命令
            val command = arrayOf("pm", "grant", packageName, permission)
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Permission $permission - Exit code: $exitCode, Output: $output")
            
            if (exitCode == 0) {
                Log.d(TAG, "Successfully granted: $permission")
                true
            } else {
                Log.w(TAG, "Failed to grant: $permission")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception granting permission: $permission", e)
            false
        }
    }
    
    /**
     * 启用GPS
     */
    private fun enableGPS(): Boolean {
        return try {
            Log.d(TAG, "Enabling GPS...")
            
            // 启用位置服务
            val commands = arrayOf(
                arrayOf("settings", "put", "secure", "location_mode", "3"),
                arrayOf("settings", "put", "secure", "location_providers_allowed", "+gps,+network")
            )
            
            var allSuccess = true
            
            commands.forEach { command ->
                val process = ProcessBuilder(*command)
                    .redirectErrorStream(true)
                    .start()
                
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                Log.d(TAG, "GPS command ${command.joinToString(" ")} - Exit code: $exitCode, Output: $output")
                
                if (exitCode != 0) {
                    allSuccess = false
                }
            }
            
            if (allSuccess) {
                Log.d(TAG, "GPS enabled successfully")
            } else {
                Log.w(TAG, "Some GPS commands failed")
            }
            
            allSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Exception enabling GPS", e)
            false
        }
    }
    
    /**
     * 异步授予权限
     */
    suspend fun grantAllPermissionsAsync(context: Context): Boolean = withContext(Dispatchers.IO) {
        grantAllPermissions(context)
    }
    
    /**
     * 检查权限状态
     */
    fun checkPermissionStatus(context: Context) {
        Log.d(TAG, "=== Direct Permission Status Check ===")
        Log.d(TAG, PermissionManager.getPermissionReport(context))
    }
    
    /**
     * 创建权限授予脚本
     */
    fun createPermissionScript(context: Context): String {
        val packageName = context.packageName
        val permissions = arrayOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )
        
        val script = StringBuilder()
        script.appendLine("#!/bin/bash")
        script.appendLine("# PowerTap权限授予脚本")
        script.appendLine("echo 'Granting permissions for $packageName'")
        script.appendLine()
        
        permissions.forEach { permission ->
            script.appendLine("adb shell pm grant $packageName $permission")
        }
        
        script.appendLine()
        script.appendLine("# 启用GPS")
        script.appendLine("adb shell settings put secure location_mode 3")
        script.appendLine("adb shell settings put secure location_providers_allowed +gps,+network")
        script.appendLine()
        script.appendLine("echo 'Permission grant completed'")
        
        return script.toString()
    }
}
