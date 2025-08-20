package com.stwpower.powertap.utils

import android.util.Log
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.log4j.PatternLayout
import org.apache.log4j.RollingFileAppender
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MyLog private constructor() {

    companion object {
        private val logger: Logger = Logger.getLogger(MyLog::class.java)
        private const val TAG = "PowerTap"

        // 防止重复配置
        @Volatile private var configured = false

        init {
            configLogger()
        }

        // ===== 简化调用（默认 TAG）=====
        @JvmStatic fun e(msg: String) = log(Level.ERROR, TAG, msg, null)
        @JvmStatic fun d(msg: String) = log(Level.DEBUG, TAG, msg, null)
        @JvmStatic fun w(msg: String) = log(Level.WARN,  TAG, msg, null)
        @JvmStatic fun v(msg: String) = log(Level.TRACE, TAG, msg, null)
        @JvmStatic fun i(msg: String) = log(Level.INFO,  TAG, msg, null)

        // ===== 支持 Throwable 的 ERROR 重载（默认 TAG）=====
        @JvmStatic fun e(msg: String, tr: Throwable) = log(Level.ERROR, TAG, msg, tr)

        // ===== 指定 TAG 的重载 =====
        @JvmStatic fun e(tag: String, msg: String) = log(Level.ERROR, tag, msg, null)
        @JvmStatic fun d(tag: String, msg: String) = log(Level.DEBUG, tag, msg, null)
        @JvmStatic fun w(tag: String, msg: String) = log(Level.WARN,  tag, msg, null)
        @JvmStatic fun v(tag: String, msg: String) = log(Level.TRACE, tag, msg, null)
        @JvmStatic fun i(tag: String, msg: String) = log(Level.INFO,  tag, msg, null)

        // ===== 指定 TAG + Throwable 的 ERROR 重载 =====
        @JvmStatic fun e(tag: String, msg: String, tr: Throwable) = log(Level.ERROR, tag, msg, tr)

        // 统一出口：支持可选 Throwable
        private fun log(level: Level, tag: String, msg: String, tr: Throwable?) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = findCaller(stackTrace)

            val cleanMsg = msg.replace("[\\r\\n]".toRegex(), " ")
            val callerInfo = caller?.let { "${it.fileName}.${it.methodName}(Line:${it.lineNumber})" } ?: "UnknownCaller"
            val logMsg = "$callerInfo - $cleanMsg"
            val consoleMsg = "${getCurrentTime()} - $logMsg"

            // ---- Android Log ----
            when {
                tr != null && level.isGreaterOrEqual(Level.ERROR) -> Log.e(tag, consoleMsg, tr)
                tr != null && level.isGreaterOrEqual(Level.WARN)  -> Log.w(tag, consoleMsg, tr)
                tr != null && level.isGreaterOrEqual(Level.INFO)  -> Log.i(tag, consoleMsg, tr)
                tr != null && level.isGreaterOrEqual(Level.DEBUG) -> Log.d(tag, consoleMsg, tr)
                tr != null                                          -> Log.v(tag, consoleMsg, tr)
                else                                                -> Log.println(toAndroidLogLevel(level), tag, consoleMsg)
            }

            // ---- log4j 文件 ----
            val paddedTag = String.format("%-8.8s", tag)
            if (tr != null) {
                logger.log(level, "$paddedTag - $logMsg", tr)
            } else {
                logger.log(level, "$paddedTag - $logMsg")
            }
        }

        // 尝试跳过 MyLog/Companion 自身
        private fun findCaller(stackTrace: Array<StackTraceElement>): StackTraceElement? {
            var hit = false
            val self = MyLog::class.java.name
            val selfCompanion = "$self\$Companion"
            for (e in stackTrace) {
                if (e.className == self || e.className == selfCompanion) {
                    hit = true
                } else if (hit) {
                    return e
                }
            }
            return null
        }

        private fun getCurrentTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date())
        }

        private fun toAndroidLogLevel(level: Level): Int = when {
            level.isGreaterOrEqual(Level.ERROR) -> Log.ERROR
            level.isGreaterOrEqual(Level.WARN)  -> Log.WARN
            level.isGreaterOrEqual(Level.INFO)  -> Log.INFO
            level.isGreaterOrEqual(Level.DEBUG) -> Log.DEBUG
            else                                -> Log.VERBOSE
        }

        @Synchronized
        private fun configLogger() {
            if (configured) return
            configured = true

            val logDirectory = getLogDirectory()
            val dir = File(logDirectory)
            if (!dir.exists()) dir.mkdirs()

            val logFile = File(logDirectory, "log.txt")
            if (!logFile.exists()) runCatching { logFile.createNewFile() }.onFailure { it.printStackTrace() }

            // 防止重复添加 appender（例如多次初始化）
            val appenders = logger.allAppenders
            if (appenders != null && appenders.hasMoreElements()) return

            val fileAppender = RollingFileAppender().apply {
                file = logFile.absolutePath
                setMaxFileSize("2MB")
                maxBackupIndex = 2
                layout = PatternLayout("%d %-5p - %m%n")
                activateOptions()
            }
            logger.addAppender(fileAppender)
        }

        private fun getLogDirectory(): String = "/sdcard/Player/logs/"
    }
}
