package ru.openflux.deployer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class OpenFluxVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_CONNECT = "ru.openflux.deployer.vpn.CONNECT"
        const val ACTION_DISCONNECT = "ru.openflux.deployer.vpn.DISCONNECT"
        private const val CHANNEL_ID = "openflux_vpn_channel"
        private const val NOTIFICATION_ID = 2001

        fun startVpn(context: Context) {
            val intent = Intent(context, OpenFluxVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, OpenFluxVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                startVpnTunnel()
            }
            ACTION_DISCONNECT -> {
                stopVpnTunnel()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpnTunnel() {
        val docUrl = ConnectionRepository.documentUrl.value
        val secret = ConnectionRepository.encryptionKey.value

        if (docUrl.isEmpty()) {
            ConnectionRepository.setConnectionState(ConnectionState.ERROR, "Ссылка на документ не указана")
            stopSelf()
            return
        }

        ConnectionRepository.setConnectionState(ConnectionState.CONNECTING)
        LogManager.addLog("Запуск OpenFlux VPN...")

        startForeground(NOTIFICATION_ID, createNotification("Подключение к OpenFlux..."))

        serviceJob?.cancel()
        serviceJob = serviceScope.launch {
            try {
                val builder = Builder()
                    .setSession("OpenFlux")
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("77.88.8.8")
                    .addDnsServer("1.1.1.1")
                    .setMtu(1400)
                    .setBlocking(true)

                val pfd = builder.establish()
                if (pfd == null) {
                    ConnectionRepository.setConnectionState(ConnectionState.ERROR, "Не удалось создать TUN интерфейс")
                    stopSelf()
                    return@launch
                }
                vpnInterface = pfd
                LogManager.addLog("TUN интерфейс поднят (10.0.0.2/32, MTU 1400)")

                val transportType = if (docUrl.contains("docs.yandex") || docUrl.contains("disk.yandex")) "vyandex" else "yandex"
                val startError = OpenFluxBridge.start(transportType, docUrl, secret)
                if (startError != null) {
                    LogManager.addLog("Предупреждение: $startError")
                    if (!OpenFluxBridge.isNativeCoreLoaded()) {
                        LogManager.addLog("Системный VPN активен. Соберите mobile.aar для полного туннелирования пакетов.")
                    }
                } else {
                    LogManager.addLog("Транспорт $transportType успешно запущен")
                }

                ConnectionRepository.setConnectionState(ConnectionState.CONNECTED)
                updateNotification("VPN активен: $transportType")

                launch {
                    val inStream = FileInputStream(pfd.fileDescriptor)
                    val buffer = ByteArray(32768)
                    try {
                        while (isActive) {
                            val length = inStream.read(buffer)
                            if (length > 0) {
                                val packet = buffer.copyOf(length)
                                OpenFluxBridge.send(packet)
                            }
                        }
                    } catch (_: IOException) {}
                }

                launch {
                    val outStream = FileOutputStream(pfd.fileDescriptor)
                    try {
                        while (isActive) {
                            val packet = OpenFluxBridge.read()
                            if (packet != null && packet.isNotEmpty()) {
                                outStream.write(packet)
                            } else {
                                delay(5)
                            }
                        }
                    } catch (_: IOException) {}
                }

                launch {
                    while (isActive) {
                        val logs = OpenFluxBridge.readLogs()
                        if (logs.isNotEmpty()) {
                            logs.lines().forEach { line ->
                                if (line.isNotBlank()) LogManager.addLog(line)
                            }
                        }
                        delay(1000)
                    }
                }

            } catch (e: Exception) {
                LogManager.addLog("Ошибка VPN: ${e.message}")
                ConnectionRepository.setConnectionState(ConnectionState.ERROR, e.message)
                stopVpnTunnel()
            }
        }
    }

    private fun stopVpnTunnel() {
        LogManager.addLog("Остановка OpenFlux VPN")
        serviceJob?.cancel()
        serviceJob = null

        OpenFluxBridge.stop()

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        ConnectionRepository.setConnectionState(ConnectionState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpnTunnel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenFlux VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомления о работе VPN туннеля"
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
            Intent(this, OpenFluxVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("OpenFlux")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", disconnectIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(statusText))
    }
}
