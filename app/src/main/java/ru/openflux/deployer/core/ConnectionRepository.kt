package ru.openflux.deployer.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectionRepository {
    private const val PREFS_NAME = "openflux_prefs"
    private const val KEY_DOC_URL = "document_url"
    private const val KEY_ENCRYPTION_KEY = "encryption_key"
    private const val KEY_MODE = "connection_mode"
    private const val KEY_SOCKS_PORT = "socks_port"

    private var preferences: SharedPreferences? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionMode = MutableStateFlow(ConnectionMode.VPN)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    private val _documentUrl = MutableStateFlow("")
    val documentUrl: StateFlow<String> = _documentUrl.asStateFlow()

    private val _encryptionKey = MutableStateFlow("")
    val encryptionKey: StateFlow<String> = _encryptionKey.asStateFlow()

    private val _socks5Port = MutableStateFlow(1080)
    val socks5Port: StateFlow<Int> = _socks5Port.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun initialize(context: Context) {
        if (preferences == null) {
            preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedUrl = preferences?.getString(KEY_DOC_URL, "") ?: ""
            val savedKey = preferences?.getString(KEY_ENCRYPTION_KEY, "") ?: ""
            val savedMode = preferences?.getString(KEY_MODE, ConnectionMode.VPN.name) ?: ConnectionMode.VPN.name
            val savedPort = preferences?.getInt(KEY_SOCKS_PORT, 1080) ?: 1080

            _documentUrl.value = savedUrl
            _encryptionKey.value = savedKey
            _connectionMode.value = runCatching { ConnectionMode.valueOf(savedMode) }.getOrDefault(ConnectionMode.VPN)
            _socks5Port.value = savedPort
        }
    }

    fun setDocumentUrl(url: String) {
        _documentUrl.value = url
        preferences?.edit()?.putString(KEY_DOC_URL, url)?.apply()
    }

    fun setEncryptionKey(key: String) {
        _encryptionKey.value = key
        preferences?.edit()?.putString(KEY_ENCRYPTION_KEY, key)?.apply()
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _connectionMode.value = mode
        preferences?.edit()?.putString(KEY_MODE, mode.name)?.apply()
    }

    fun setSocks5Port(port: Int) {
        _socks5Port.value = port
        preferences?.edit()?.putInt(KEY_SOCKS_PORT, port)?.apply()
    }

    fun setConnectionState(state: ConnectionState, error: String? = null) {
        _connectionState.value = state
        _errorMessage.value = error
        if (error != null) {
            LogManager.addLog("Ошибка: $error")
        }
    }
}
