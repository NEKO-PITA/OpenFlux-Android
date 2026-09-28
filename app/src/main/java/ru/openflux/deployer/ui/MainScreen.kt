package ru.openflux.deployer.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.openflux.deployer.core.ConnectionMode
import ru.openflux.deployer.core.ConnectionRepository
import ru.openflux.deployer.core.ConnectionState
import ru.openflux.deployer.ui.theme.*
import ru.openflux.deployer.util.KeyGenerator
import ru.openflux.deployer.util.UrlNormalizer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onConnectRequested: () -> Unit,
    onDisconnectRequested: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val connectionState by ConnectionRepository.connectionState.collectAsState()
    val connectionMode by ConnectionRepository.connectionMode.collectAsState()
    val documentUrl by ConnectionRepository.documentUrl.collectAsState()
    val encryptionKey by ConnectionRepository.encryptionKey.collectAsState()
    val socksPort by ConnectionRepository.socks5Port.collectAsState()
    val errorMessage by ConnectionRepository.errorMessage.collectAsState()

    var showDeployDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    if (showDeployDialog) {
        DeployDialog(onDismiss = { showDeployDialog = false })
    }

    if (showLogsDialog) {
        LogsDialog(onDismiss = { showLogsDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OpenFlux",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showDeployDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkCard,
                            contentColor = PrimaryBlue
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "Деплой",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Деплой", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    FilledTonalButton(
                        onClick = { showLogsDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = DarkCard,
                            contentColor = TextPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Логи",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Логи", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Статус подключения",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (connectionState) {
                                ConnectionState.CONNECTED -> "Подключено"
                                ConnectionState.CONNECTING -> "Подключение..."
                                ConnectionState.ERROR -> "Ошибка подключения"
                                ConnectionState.DISCONNECTED -> "Отключено"
                            },
                            color = when (connectionState) {
                                ConnectionState.CONNECTED -> AccentSuccess
                                ConnectionState.CONNECTING -> PrimaryBlue
                                ConnectionState.ERROR -> AccentDanger
                                ConnectionState.DISCONNECTED -> TextHint
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> AccentSuccess
                                    ConnectionState.CONNECTING -> PrimaryBlue
                                    ConnectionState.ERROR -> AccentDanger
                                    ConnectionState.DISCONNECTED -> DarkCardBorder
                                }
                            )
                    )
                }
            }

            if (errorMessage != null && connectionState == ConnectionState.ERROR) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AccentDanger)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = AccentDanger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Выбор режима работы",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (connectionMode == ConnectionMode.VPN) PrimaryBlue else DarkSurface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (connectionState == ConnectionState.DISCONNECTED) {
                                        ConnectionRepository.setConnectionMode(ConnectionMode.VPN)
                                    } else {
                                        Toast.makeText(context, "Сначала отключите туннель", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "VPN",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (connectionMode == ConnectionMode.VPN) Color.White else TextPrimary
                                )
                                Text(
                                    text = "Вся система",
                                    fontSize = 11.sp,
                                    color = if (connectionMode == ConnectionMode.VPN) Color.White.copy(alpha = 0.85f) else TextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (connectionMode == ConnectionMode.SOCKS5) PrimaryBlue else DarkSurface,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (connectionState == ConnectionState.DISCONNECTED) {
                                        ConnectionRepository.setConnectionMode(ConnectionMode.SOCKS5)
                                    } else {
                                        Toast.makeText(context, "Сначала отключите туннель", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "SOCKS5",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (connectionMode == ConnectionMode.SOCKS5) Color.White else TextPrimary
                                )
                                Text(
                                    text = "127.0.0.1:$socksPort",
                                    fontSize = 11.sp,
                                    color = if (connectionMode == ConnectionMode.SOCKS5) Color.White.copy(alpha = 0.85f) else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Ссылка на Яндекс.Документ",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = documentUrl,
                        onValueChange = {
                            ConnectionRepository.setDocumentUrl(it)
                        },
                        placeholder = { Text("https://docs.yandex.ru/edit/d/...", color = TextHint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            Row {
                                if (documentUrl.isNotEmpty()) {
                                    IconButton(onClick = { ConnectionRepository.setDocumentUrl("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = TextSecondary)
                                    }
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val item = clipboard.primaryClip?.getItemAt(0)
                                    val text = item?.text?.toString() ?: ""
                                    if (text.isNotBlank()) {
                                        val normalized = UrlNormalizer.normalize(text)
                                        ConnectionRepository.setDocumentUrl(normalized)
                                        Toast.makeText(context, "Ссылка вставлена", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Вставить", tint = PrimaryBlue)
                                }
                            }
                        }
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSettings = !showSettings },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Параметры безопасности",
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                        Text(
                            text = if (showSettings) "Скрыть" else "Показать",
                            color = PrimaryBlue,
                            fontSize = 12.sp
                        )
                    }

                    AnimatedVisibility(visible = showSettings) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = encryptionKey,
                                    onValueChange = { ConnectionRepository.setEncryptionKey(it) },
                                    label = { Text("AES-256 Ключ") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        val newKey = KeyGenerator.generate256BitHex()
                                        ConnectionRepository.setEncryptionKey(newKey)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                                ) {
                                    Text("Новый", fontSize = 12.sp)
                                }
                            }

                            if (connectionMode == ConnectionMode.SOCKS5) {
                                OutlinedTextField(
                                    value = socksPort.toString(),
                                    onValueChange = {
                                        val p = it.toIntOrNull() ?: 1080
                                        ConnectionRepository.setSocks5Port(p)
                                    },
                                    label = { Text("Порт SOCKS5") },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val isConnected = connectionState == ConnectionState.CONNECTED
            val isConnecting = connectionState == ConnectionState.CONNECTING

            Button(
                onClick = {
                    if (isConnected || isConnecting) {
                        onDisconnectRequested()
                    } else {
                        val cleanUrl = UrlNormalizer.normalize(documentUrl)
                        if (cleanUrl.isEmpty()) {
                            Toast.makeText(context, "Вставьте ссылку на Яндекс.Документ", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        ConnectionRepository.setDocumentUrl(cleanUrl)
                        onConnectRequested()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) AccentDanger else PrimaryBlue
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Подключение...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else if (isConnected) {
                    Text(
                        text = "Отключить",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                } else {
                    Text(
                        text = if (connectionMode == ConnectionMode.VPN) "Подключить VPN" else "Запустить SOCKS5",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
