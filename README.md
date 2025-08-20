# 触屏版app

## 版本说明

| 版本号   | 日期         | 备注                    |
|-------|------------|-----------------------|
| 1.0.0 | 2025-08-18 | 初始版本                  |
| 1.0.1 | 2025-08-18 | 修复terminal页弹窗问题       |
| 1.0.2 | 2025-08-20 | 优化日志功能，优化terminal连接流程 |


## TODO List

- [X] 进入terminal会是否使用该app的弹窗，而不是在main页弹出，是不是也可以不要这个东西
- [X] 当terminal连接不稳定时，或者反复拔插测试的时候，会出现状态混乱，且出现错误流程的情况，需优化
- [ ] 减少包大小，是否能开启混淆
- [X] 统一tag，输出关键日志到本地
- [ ] 增加升级功能，监听广播进行升级（目前不是设置为系统应用，但是有系统证书，不知能否支持无需用户交互的升级模式）
- [ ] 授权目前只能通过adb，由于kiosk模式，无法进入系统页面正常授权
- [ ] 国际化配置可以从本地文件中加载
- [ ] 优化网络请求，有些数据，只需要请求一次就可以了，后续直接使用缓存


## adb授权指令

```shell
# 位置权限
adb shell pm grant com.stwpower.powertap android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.stwpower.powertap android.permission.ACCESS_COARSE_LOCATION

# 蓝牙权限
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH
adb shell pm grant com.stwpower.powertap android.permission.BLUETOOTH_ADMIN

# 存储权限
adb shell pm grant com.stwpower.powertap android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.stwpower.powertap android.permission.WRITE_EXTERNAL_STORAGE

# 获取电话权限
adb shell pm grant com.stwpower.powertap android.permission.READ_PHONE_STATE

# 启用GPS和位置服务
adb shell settings put secure location_mode 3
adb shell settings put secure location_providers_allowed +gps,+network

```