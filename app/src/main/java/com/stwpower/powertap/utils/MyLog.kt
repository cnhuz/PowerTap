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
        private val instance: MyLog by lazy { MyLog() }
        private val TAG = "PowerTap"

        init {
            configLogger()
        }

        fun getInstance(): MyLog = instance

        @JvmStatic
        fun e(msg: String) {
            log(Level.ERROR, TAG, msg)
        }

        @JvmStatic
        fun d(msg: String) {
            log(Level.DEBUG, TAG, msg)
        }

        @JvmStatic
        fun w(msg: String) {
            log(Level.WARN, TAG, msg)
        }

        @JvmStatic
        fun v(msg: String) {
            log(Level.TRACE, TAG, msg)
        }

        @JvmStatic
        fun i(msg: String) {
            log(Level.INFO, TAG, msg)
        }

        @JvmStatic
        fun e(tag: String, msg: String) {
            log(Level.ERROR, tag, msg)
        }

        @JvmStatic
        fun d(tag: String, msg: String) {
            log(Level.DEBUG, tag, msg)
        }

        @JvmStatic
        fun w(tag: String, msg: String) {
            log(Level.WARN, tag, msg)
        }

        @JvmStatic
        fun v(tag: String, msg: String) {
            log(Level.TRACE, tag, msg)
        }

        @JvmStatic
        fun i(tag: String, msg: String) {
            log(Level.INFO, tag, msg)
        }

        private fun log(level: Level, tag: String, msg: String) {
            val stackTrace = Thread.currentThread().stackTrace
            val caller = findCaller(stackTrace)
            if (caller != null) {
                val cleanMsg = msg.replace("[\\r\\n]".toRegex(), " ")
                val callerInfo = "${caller.fileName}.${caller.methodName}(Line:${caller.lineNumber})"
                val logMsg = "$callerInfo - $cleanMsg"
                Log.println(toAndroidLogLevel(level), tag, "${getCurrentTime()} - $logMsg")
                logger.log(level, String.format("%-8.8s", tag) + " - $logMsg")
            }
        }

        private fun findCaller(stackTrace: Array<StackTraceElement>): StackTraceElement? {
            var hitLogHelper = false
            for (element in stackTrace) {
                if (element.className == MyLog::class.java.name) {
                    hitLogHelper = true
                } else if (hitLogHelper) {
                    return element
                }
            }
            return null
        }

        private fun getCurrentTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date())
        }

        private fun toAndroidLogLevel(level: Level): Int {
            return when {
                level.isGreaterOrEqual(Level.ERROR) -> Log.ERROR
                level.isGreaterOrEqual(Level.WARN) -> Log.WARN
                level.isGreaterOrEqual(Level.INFO) -> Log.INFO
                level.isGreaterOrEqual(Level.DEBUG) -> Log.DEBUG
                else -> Log.VERBOSE
            }
        }

        private fun configLogger() {
            val logDirectory = getLogDirectory()
            val dir = File(logDirectory)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(logDirectory, "log.txt")
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val fileAppender = RollingFileAppender()
            fileAppender.file = logDirectory + "log.txt"
            fileAppender.setMaxFileSize("2MB")
            fileAppender.maxBackupIndex = 2
            fileAppender.layout = PatternLayout("%d %-5p - %m%n")
            fileAppender.activateOptions()
            logger.addAppender(fileAppender)
        }

        private fun getLogDirectory(): String {
            return "/sdcard/Player/logs/"
        }
    }
}