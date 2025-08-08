#!/bin/bash

# PowerTap权限授予脚本
# 使用方法: ./grant_permissions.sh

echo "=== PowerTap权限授予脚本 ==="
echo "正在为 com.stwpower.powertap 授予权限..."
echo

# 检查ADB连接
if ! adb devices | grep -q "device$"; then
    echo "错误: 没有检测到ADB设备连接"
    echo "请确保:"
    echo "1. 设备已连接并启用USB调试"
    echo "2. 已授权ADB调试"
    exit 1
fi

echo "检测到ADB设备连接 ✓"
echo

# 授予位置权限
echo "授予位置权限..."
adb shell pm grant com.stwpower.powertap android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.stwpower.powertap android.permission.ACCESS_COARSE_LOCATION

# 授予蓝牙权限
echo "授予蓝牙权限..."
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_ADMIN

# Android 12+ 蓝牙权限
echo "授予Android 12+蓝牙权限..."
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_CONNECT 2>/dev/null || echo "BLUETOOTH_CONNECT权限不适用于此设备"
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_SCAN 2>/dev/null || echo "BLUETOOTH_SCAN权限不适用于此设备"

# 授予存储权限
echo "授予存储权限..."
adb shell pm grant com.stwpower.powertap android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.stwpower.powertap android.permission.WRITE_EXTERNAL_STORAGE

# 授予电话状态权限（获取IMEI）
echo "授予电话状态权限..."
adb shell pm grant com.stwpower.powertap android.permission.READ_PHONE_STATE

echo
echo "启用GPS和位置服务..."
# 启用GPS
adb shell settings put secure location_mode 3
adb shell settings put secure location_providers_allowed +gps,+network

echo
echo "=== 权限授予完成 ==="
echo

# 验证权限状态
echo "验证权限状态..."
echo "位置权限:"
adb shell dumpsys package com.stwpower.powertap | grep -A 1 "android.permission.ACCESS_FINE_LOCATION"
echo

echo "蓝牙权限:"
adb shell dumpsys package com.stwpower.powertap | grep -A 1 "android.permission.BLUETOOTH"
echo

echo "GPS状态:"
adb shell settings get secure location_mode
echo

echo "=== 脚本执行完成 ==="
echo "请重新启动PowerTap应用以验证权限状态"
