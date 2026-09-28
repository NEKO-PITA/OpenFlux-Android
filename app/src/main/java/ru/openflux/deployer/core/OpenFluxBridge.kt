package ru.openflux.deployer.core

import java.lang.reflect.Method

object OpenFluxBridge {
    private var mobileClass: Class<*>? = null
    private var startMethod: Method? = null
    private var stopMethod: Method? = null
    private var isConnectedMethod: Method? = null
    private var sendMethod: Method? = null
    private var readMethod: Method? = null
    private var readLogsMethod: Method? = null
    private var startProxyMethod: Method? = null
    private var stopProxyMethod: Method? = null
    private var proxyIsConnectedMethod: Method? = null
    private var proxyIsRunningMethod: Method? = null

    init {
        try {
            val loadedClass = Class.forName("mobile.Mobile")
            mobileClass = loadedClass
            startMethod = loadedClass.getMethod("start", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
            stopMethod = loadedClass.getMethod("stop")
            isConnectedMethod = loadedClass.getMethod("isConnected")
            sendMethod = loadedClass.getMethod("send", ByteArray::class.java)
            readMethod = loadedClass.getMethod("read")
            readLogsMethod = loadedClass.getMethod("readLogs")
            startProxyMethod = loadedClass.getMethod("startProxy", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
            stopProxyMethod = loadedClass.getMethod("stopProxy")
            proxyIsConnectedMethod = loadedClass.getMethod("proxyIsConnected")
            proxyIsRunningMethod = loadedClass.getMethod("proxyIsRunning")
        } catch (_: Throwable) {
            mobileClass = null
        }
    }

    fun isNativeCoreLoaded(): Boolean {
        return mobileClass != null
    }

    fun start(
        transportType: String,
        documentUrl: String,
        encryptionSecret: String,
        codec: String = "batched",
        maxToken: String = "",
        maxUid: String = ""
    ): String? {
        val method = startMethod ?: return "Go-модуль не подключен. Соберите mobile.aar."
        return try {
            val result = method.invoke(null, transportType, documentUrl, encryptionSecret, codec, maxToken, maxUid) as? String
            if (result.isNullOrEmpty()) null else result
        } catch (e: Throwable) {
            e.cause?.message ?: e.message
        }
    }

    fun stop() {
        try {
            stopMethod?.invoke(null)
        } catch (_: Throwable) {}
    }

    fun isConnected(): Boolean {
        val method = isConnectedMethod ?: return false
        return try {
            method.invoke(null) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun send(packet: ByteArray): String? {
        val method = sendMethod ?: return "Ядро не запущено"
        return try {
            val result = method.invoke(null, packet) as? String
            if (result.isNullOrEmpty()) null else result
        } catch (e: Throwable) {
            e.cause?.message ?: e.message
        }
    }

    fun read(): ByteArray? {
        val method = readMethod ?: return null
        return try {
            method.invoke(null) as? ByteArray
        } catch (_: Throwable) {
            null
        }
    }

    fun readLogs(): String {
        val method = readLogsMethod ?: return ""
        return try {
            (method.invoke(null) as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    fun startProxy(
        transportType: String,
        documentUrl: String,
        encryptionSecret: String,
        codec: String = "batched",
        maxToken: String = "",
        maxUid: String = "",
        listenAddr: String = "127.0.0.1:1080",
        username: String = "",
        password: String = "",
        bypassDomains: String = ""
    ): String? {
        val method = startProxyMethod ?: return "Go-модуль не подключен. Соберите mobile.aar."
        return try {
            val result = method.invoke(
                null,
                transportType,
                documentUrl,
                encryptionSecret,
                codec,
                maxToken,
                maxUid,
                listenAddr,
                username,
                password,
                bypassDomains
            ) as? String
            if (result.isNullOrEmpty()) null else result
        } catch (e: Throwable) {
            e.cause?.message ?: e.message
        }
    }

    fun stopProxy() {
        try {
            stopProxyMethod?.invoke(null)
        } catch (_: Throwable) {}
    }

    fun isProxyConnected(): Boolean {
        val method = proxyIsConnectedMethod ?: return false
        return try {
            method.invoke(null) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun isProxyRunning(): Boolean {
        val method = proxyIsRunningMethod ?: return false
        return try {
            method.invoke(null) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
