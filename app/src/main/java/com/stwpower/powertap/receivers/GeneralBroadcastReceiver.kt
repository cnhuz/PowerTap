package com.stwpower.powertap.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.stwpower.powertap.R
import com.stwpower.powertap.config.ConfigLoader
import com.stwpower.powertap.data.api.MyApiClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通用广播接收器
 * 用于实现应用开机自启和接收自定义广播通知功能
 */
class GeneralBroadcastReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "powertap"
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_RECEIVE_DATA = "com.stwpower.player.ACTION_RECEIVE_DATA"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "接收到广播: $action")
        
        when (action) {
            // 开机完成广播处理
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            HTC_QUICKBOOT_POWERON -> {
                Log.d(TAG, "设备开机完成，启动应用...")
                
                // 延迟启动应用，确保系统完全启动
                Handler(Looper.getMainLooper()).postDelayed({
                    startMainActivity(context)
                }, 3000) // 延迟3秒启动
            }
            
            // 自定义数据接收广播处理
            ACTION_RECEIVE_DATA -> {
                Log.d(TAG, "接收到自定义数据广播")
                handleCustomDataBroadcast(context, intent)
            }
            
            else -> {
                Log.w(TAG, "未知广播动作: $action")
            }
        }
    }
    
    /**
     * 处理自定义数据广播
     */
    private fun handleCustomDataBroadcast(context: Context, intent: Intent) {
        try {
            // 获取广播中的数据
            val listData = intent.getCharSequenceArrayExtra("listData")
            
            Log.d(TAG, "接收到自定义数据:")
            if (listData != null) {
                Log.d(TAG, "  listData长度: ${listData.size}")
                for (i in listData.indices) {
                    Log.d(TAG, "  listData[$i]: ${listData[i]}")
                }
                
                // 处理电源银行数据
                processPowerBankData(context, listData)
            } else {
                Log.d(TAG, "  listData为空")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理自定义数据广播时出错", e)
        }
    }
    
    /**
     * 显示数据弹窗
     */
    private fun showDataPopup(context: Context, data: Array<CharSequence>) {
        try {
            Log.d(TAG, "显示数据弹窗")
            
            // 创建启动弹窗Activity的意图
            val intent = Intent(context, com.stwpower.powertap.ui.DataPopupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(com.stwpower.powertap.ui.DataPopupActivity.EXTRA_DATA_LIST, data)
            }
            
            // 启动弹窗Activity
            context.startActivity(intent)
            Log.d(TAG, "数据弹窗启动成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示数据弹窗时出错", e)
        }
    }
    
    /**
     * 处理电源银行数据
     */
    private fun processPowerBankData(context: Context, data: Array<CharSequence>) {
        try {
            Log.d(TAG, "处理电源银行数据，共${data.size}条记录")
            
            // 启动协程进行网络请求
            GlobalScope.launch(Dispatchers.Main) {
                try {
                    // 创建一个新的列表来存储处理后的数据
                    val processedDataList = mutableListOf<CharSequence>()
                    
                    // 为每个电源银行数据项执行网络请求
                    for (item in data) {
                        Log.d(TAG, "处理电源银行项: $item")
                        
                        // 假设item是充电宝ID，需要根据实际数据格式进行调整
                        val powerBankId = item.toString()
                        
                        // 在IO线程中执行网络请求
                        val response = withContext(Dispatchers.IO) {
                            MyApiClient.getOrderInfoByPowerBankId(powerBankId)
                        }
                        
                        // 处理响应结果
                        if (response != null && response.code == 200) {
                            // 解析数据
                            val dataMap = response.data as? Map<*, *>
                            val usedTime = dataMap?.get("usedTime")
                            val amount = dataMap?.get("amount") as? Number
                            
                            // 格式化数据
                            val usedTimeStr = if (usedTime is Number) {
                                usedTime.toInt().toString()
                            } else {
                                "N/A"
                            }
                            val amountStr = amount?.let { String.format("%.2f", it.toDouble()) } ?: "N/A"
                            
                            // 添加原始数据和归还成功信息到列表
                            processedDataList.add(context.getString(R.string.return_success, usedTimeStr, amountStr, ConfigLoader.currency))
                            showDataPopup(context, processedDataList.toTypedArray())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理电源银行数据时出错", e)
                    // 出现异常时不显示弹窗
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理电源银行数据时出错", e)
        }
    }
    
    /**
     * 启动主Activity
     */
    private fun startMainActivity(context: Context) {
        try {
            Log.d(TAG, "启动主Activity...")
            val intent = Intent(context, com.stwpower.powertap.MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            context.startActivity(intent)
            Log.d(TAG, "主Activity启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "启动主Activity失败", e)
        }
    }
}