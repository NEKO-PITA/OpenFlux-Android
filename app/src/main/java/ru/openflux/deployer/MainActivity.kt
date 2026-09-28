package ru.openflux.deployer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import ru.openflux.deployer.core.ConnectionMode
import ru.openflux.deployer.core.ConnectionRepository
import ru.openflux.deployer.service.OpenFluxSocks5Service
import ru.openflux.deployer.service.OpenFluxVpnService
import ru.openflux.deployer.ui.MainScreen
import ru.openflux.deployer.ui.theme.OpenFluxTheme

class MainActivity : ComponentActivity() {

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "Требуется разрешение для запуска VPN", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermission()

        setContent {
            OpenFluxTheme {
                MainScreen(
                    onConnectRequested = { handleConnect() },
                    onDisconnectRequested = { handleDisconnect() }
                )
            }
        }
    }

    private fun handleConnect() {
        val mode = ConnectionRepository.connectionMode.value
        when (mode) {
            ConnectionMode.VPN -> {
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent != null) {
                    vpnConsentLauncher.launch(prepareIntent)
                } else {
                    startVpnService()
                }
            }
            ConnectionMode.SOCKS5 -> {
                OpenFluxSocks5Service.startProxy(this)
            }
        }
    }

    private fun handleDisconnect() {
        OpenFluxVpnService.stopVpn(this)
        OpenFluxSocks5Service.stopProxy(this)
    }

    private fun startVpnService() {
        OpenFluxVpnService.startVpn(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
