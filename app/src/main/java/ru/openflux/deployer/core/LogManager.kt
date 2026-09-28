package ru.openflux.deployer.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    private val maxLogs = 1000
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val internalLogs = mutableListOf<String>()
    private val _logsFlow = MutableStateFlow<List<String>>(emptyList())
    val logsFlow: StateFlow<List<String>> = _logsFlow.asStateFlow()

    @Synchronized
    fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedMessage = "[$timestamp] $message"
        internalLogs.add(formattedMessage)
        if (internalLogs.size > maxLogs) {
            internalLogs.removeAt(0)
        }
        _logsFlow.value = ArrayList(internalLogs)
    }

    @Synchronized
    fun getLogs(): List<String> {
        return ArrayList(internalLogs)
    }

    @Synchronized
    fun getLogsText(): String {
        return internalLogs.joinToString("\n")
    }

    @Synchronized
    fun clearLogs() {
        internalLogs.clear()
        _logsFlow.value = emptyList()
    }
}
