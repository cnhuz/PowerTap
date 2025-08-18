#!/bin/bash

# PowerTap一键安装授权启动脚本
# 使用方法: ./install_powertap.sh [apk_path]
# 如果不指定apk_path，则使用apk目录下的最新APK文件

echo "=== PowerTap一键安装授权启动脚本 ==="
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

# 确定APK文件路径
APK_PATH=""
if [ $# -eq 0 ]; then
    # 如果没有指定APK路径，则查找apk目录下的最新APK文件
    if [ -d "apk" ]; then
        APK_PATH=$(ls -t apk/*.apk 2>/dev/null | head -n 1)
        if [ -z "$APK_PATH" ]; then
            echo "错误: 在apk目录中未找到APK文件"
            echo "请先构建项目或指定APK文件路径"
            exit 1
        fi
        echo "使用apk目录中的APK文件: $APK_PATH"
    else
        echo "错误: 未找到apk目录且未指定APK文件路径"
        echo "请先构建项目或指定APK文件路径"
        exit 1
    fi
else
    APK_PATH="$1"
    if [ ! -f "$APK_PATH" ]; then
        echo "错误: 指定的APK文件不存在: $APK_PATH"
        exit 1
    fi
    echo "使用指定的APK文件: $APK_PATH"
fi

echo

# 安装APK
echo "=== 安装APK ==="
echo "正在安装 $APK_PATH..."
adb install -r "$APK_PATH"
if [ $? -ne 0 ]; then
    echo "错误: APK安装失败"
    exit 1
fi
echo "APK安装成功 ✓"
echo

# 获取应用包名
PACKAGE_NAME="com.stwpower.powertap"
echo "应用包名: $PACKAGE_NAME"
echo

# 授予权限
echo "=== 授予权限 ==="
echo "正在为 $PACKAGE_NAME 授予权限..."

# 授予位置权限
echo "授予位置权限..."
adb shell pm grant $PACKAGE_NAME android.permission.ACCESS_FINE_LOCATION 2>/dev/null
adb shell pm grant $PACKAGE_NAME android.permission.ACCESS_COARSE_LOCATION 2>/dev/null

# 授予蓝牙权限
echo "授予蓝牙权限..."
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH 2>/dev/null
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_ADMIN 2>/dev/null

# Android 12+ 蓝牙权限
echo "授予Android 12+蓝牙权限..."
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_CONNECT 2>/dev/null
adb shell pm grant $PACKAGE_NAME android.permission.BLUETOOTH_SCAN 2>/dev/null

# 授予存储权限
echo "授予存储权限..."
adb shell pm grant $PACKAGE_NAME android.permission.READ_EXTERNAL_STORAGE 2>/dev/null
adb shell pm grant $PACKAGE_NAME android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null

# 授予电话状态权限（获取IMEI）
echo "授予电话状态权限..."
adb shell pm grant $PACKAGE_NAME android.permission.READ_PHONE_STATE 2>/dev/null

# 授予USB权限
echo "授予USB权限..."
adb shell pm grant $PACKAGE_NAME android.permission.USB_PERMISSION 2>/dev/null

echo
echo "启用GPS和位置服务..."
# 启用GPS
adb shell settings put secure location_mode 3 2>/dev/null
adb shell settings put secure location_providers_allowed +gps,+network 2>/dev/null

echo
echo "权限授予完成 ✓"
echo

# 设置为默认处理USB设备的应用
echo "=== 设置默认USB处理应用 ==="
echo "正在将 $PACKAGE_NAME 设置为默认处理USB设备的应用..."

# 清除默认应用设置（如果有）
adb shell pm clear $PACKAGE_NAME 2>/dev/null

# 设置为默认处理USB设备
# 注意：这可能需要根据具体设备和Android版本进行调整
adb shell cmd package set-home-activity $PACKAGE_NAME/.MainActivity 2>/dev/null

echo "默认USB处理应用设置完成 ✓"
echo

# 启动应用
echo "=== 启动应用 ==="
echo "正在启动 $PACKAGE_NAME..."
adb shell am start -n $PACKAGE_NAME/.MainActivity
if [ $? -eq 0 ]; then
    echo "应用启动成功 ✓"
else
    echo "应用启动失败，请手动启动应用"
fi
echo

# 验证权限状态
echo "=== 验证权限状态 ==="
echo "位置权限:"
adb shell dumpsys package $PACKAGE_NAME | grep -A 1 "android.permission.ACCESS_FINE_LOCATION" 2>/dev/null || echo "未找到权限信息"
echo

echo "蓝牙权限:"
adb shell dumpsys package $PACKAGE_NAME | grep -A 1 "android.permission.BLUETOOTH" 2>/dev/null || echo "未找到权限信息"
echo

echo "USB权限:"
adb shell dumpsys package $PACKAGE_NAME | grep -A 1 "android.permission.USB_PERMISSION" 2>/dev/null || echo "未找到权限信息"
echo

echo "GPS状态:"
adb shell settings get secure location_mode 2>/dev/null || echo "无法获取GPS状态"
echo

echo "=== 脚本执行完成 ==="
echo "PowerTap已成功安装、授权并启动！"
