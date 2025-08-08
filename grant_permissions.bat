@echo off
chcp 65001 >nul

echo === PowerTap权限授予脚本 ===
echo 正在为 com.stwpower.powertap 授予权限...
echo.

REM 检查ADB连接
adb devices | findstr "device" >nul
if errorlevel 1 (
    echo 错误: 没有检测到ADB设备连接
    echo 请确保:
    echo 1. 设备已连接并启用USB调试
    echo 2. 已授权ADB调试
    pause
    exit /b 1
)

echo 检测到ADB设备连接 ✓
echo.

REM 授予位置权限
echo 授予位置权限...
adb shell pm grant com.stwpower.powertap android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.stwpower.powertap android.permission.ACCESS_COARSE_LOCATION

REM 授予蓝牙权限
echo 授予蓝牙权限...
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_ADMIN

REM Android 12+ 蓝牙权限
echo 授予Android 12+蓝牙权限...
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_CONNECT 2>nul || echo BLUETOOTH_CONNECT权限不适用于此设备
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_SCAN 2>nul || echo BLUETOOTH_SCAN权限不适用于此设备

REM 授予存储权限
echo 授予存储权限...
adb shell pm grant com.stwpower.powertap android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.stwpower.powertap android.permission.WRITE_EXTERNAL_STORAGE

REM 授予电话状态权限（获取IMEI）
echo 授予电话状态权限...
adb shell pm grant com.stwpower.powertap android.permission.READ_PHONE_STATE

echo.
echo 启用GPS和位置服务...
REM 启用GPS
adb shell settings put secure location_mode 3
adb shell settings put secure location_providers_allowed +gps,+network

echo.
echo === 权限授予完成 ===
echo.

REM 验证权限状态
echo 验证权限状态...
echo 位置权限:
adb shell dumpsys package com.stwpower.powertap | findstr "android.permission.ACCESS_FINE_LOCATION"
echo.

echo 蓝牙权限:
adb shell dumpsys package com.stwpower.powertap | findstr "android.permission.BLUETOOTH"
echo.

echo GPS状态:
adb shell settings get secure location_mode
echo.

echo === 脚本执行完成 ===
echo 请重新启动PowerTap应用以验证权限状态
pause
