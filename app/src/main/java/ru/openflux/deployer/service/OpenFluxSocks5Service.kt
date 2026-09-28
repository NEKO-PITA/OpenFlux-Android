package ru.openflux.deployer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.openflux.deployer.MainActivity
import ru.openflux.deployer.core.ConnectionRepository
import ru.openflux.deployer.core.ConnectionState
import ru.openflux.deployer.core.LogManager
import ru.openflux.deployer.core.OpenFluxBridge

class OpenFluxSocks5Service : Service() {

    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_CONNECT = "ru.openflux.deployer.socks5.CONNECT"
        const val ACTION_DISCONNECT = "ru.openflux.deployer.socks5.DISCONNECT"
        private const val CHANNEL_ID = "openflux_socks5_channel"
        private const val NOTIFICATION_ID = 2002

        fun startProxy(context: Context) {
            val intent = Intent(context, OpenFluxSocks5Service::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopProxy(context: Context) {
            val intent = Intent(context, OpenFluxSocks5Service::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startSocks5Proxy()
            ACTION_DISCONNECT -> stopSocks5Proxy()
        }
        return START_NOT_STICKY
    }

    private fun startSocks5Proxy() {
        val docUrl = ConnectionRepository.documentUrl.value
        val secret = ConnectionRepository.encryptionKey.value
        val port = ConnectionRepository.socks5Port.value

        if (docUrl.isEmpty()) {
            ConnectionRepository.setConnectionState(ConnectionState.ERROR, "Ссылка на документ не указана")
            stopSelf()
            return
        }

        ConnectionRepository.setConnectionState(ConnectionState.CONNECTING)
        LogManager.addLog("Запуск SOCKS5 прокси на 127.0.0.1:$port...")

        startForeground(NOTIFICATION_ID, createNotification("Запуск SOCKS5 на 127.0.0.1:$port..."))

        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            try {
                val transportType = if (docUrl.contains("docs.yandex") || docUrl.contains("disk.yandex")) "vyandex" else "yandex"
                val error = OpenFluxBridge.startProxy(
                    transportType = transportType,
                    documentUrl = docUrl,
                    encryptionSecret = secret,
                    codec = "batched",
                    listenAddr = "127.0.0.1:$port"
                )

                if (error != null) {
                    LogManager.addLog("Предупреждение: $error")
                } else {
                    LogManager.addLog("SOCKS5 прокси успешно запущен на 127.0.0.1:$port")
                }

                ConnectionRepository.setConnectionState(ConnectionState.CONNECTED)
                updateNotification("SOCKS5 активен: 127.0.0.1:$port")

                while (isActive) {
                    val logs = OpenFluxBridge.readLogs()
                    if (logs.isNotEmpty()) {
                        logs.lines().forEach { line ->
                            if (line.isNotBlank()) LogManager.addLog(line)
                        }
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                LogManager.addLog("Ошибка SOCKS5: ${e.message}")
                ConnectionRepository.setConnectionState(ConnectionState.ERROR, e.message)
                stopSocks5Proxy()
            }
        }
    }

    private fun stopSocks5Proxy() {
        LogManager.addLog("Остановка SOCKS5 прокси")
        serviceJob?.cancel()
        serviceJob = null

        OpenFluxBridge.stopProxy()

        ConnectionRepository.setConnectionState(ConnectionState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopSocks5Proxy()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenFlux SOCKS5 Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о работе SOCKS5 прокси"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OpenFluxSocks5Service::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("OpenFlux SOCKS5")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", disconnectIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(statusText))
    }
}
