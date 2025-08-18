@echo off
setlocal EnableExtensions EnableDelayedExpansion

adb start-server >nul 2>nul

set "APK_PATH=H:\powertap\PowerTap-1.0.1-SC20NEW.apk"
adb install -r "%APK_PATH%"

set "PACKAGE_NAME=com.stwpower.powertap"

adb shell pm grant !PACKAGE_NAME! android.permission.ACCESS_FINE_LOCATION 
adb shell pm grant !PACKAGE_NAME! android.permission.ACCESS_COARSE_LOCATION

adb shell pm grant !PACKAGE_NAME! android.permission.BLUETOOTH
adb shell pm grant !PACKAGE_NAME! android.permission.BLUETOOTH_ADMIN
adb shell pm grant !PACKAGE_NAME! android.permission.BLUETOOTH_CONNECT
adb shell pm grant !PACKAGE_NAME! android.permission.BLUETOOTH_SCAN

adb shell pm grant !PACKAGE_NAME! android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant !PACKAGE_NAME! android.permission.WRITE_EXTERNAL_STORAGE

adb shell pm grant !PACKAGE_NAME! android.permission.READ_PHONE_STATE

adb shell settings put secure location_mode 3
adb shell settings put secure location_providers_allowed +gps,+network

adb shell am start -n !PACKAGE_NAME!/.MainActivity

pause
