package ru.openflux.deployer.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import ru.openflux.deployer.core.ConnectionRepository
import ru.openflux.deployer.core.LogManager
import ru.openflux.deployer.data.ChannelConfig
import ru.openflux.deployer.data.DeployResult
import ru.openflux.deployer.data.DeployState
import ru.openflux.deployer.data.SshTarget
import ru.openflux.deployer.ssh.SSHDeployer
import ru.openflux.deployer.ui.theme.*
import ru.openflux.deployer.util.KeyGenerator
import ru.openflux.deployer.util.ShareLinkBuilder
import ru.openflux.deployer.util.UrlNormalizer

@Composable
fun DeployDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var sudoPassword by remember { mutableStateOf("") }

    var channelPort by remember { mutableStateOf("8445") }
    var documentUrl by remember { mutableStateOf(ConnectionRepository.documentUrl.value) }
    var channelKey by remember { mutableStateOf(ConnectionRepository.encryptionKey.value.ifEmpty { KeyGenerator.generate256BitHex() }) }

    var deployState by remember { mutableStateOf<DeployState>(DeployState.Idle) }
    var deployResult by remember { mutableStateOf<DeployResult?>(null) }
    val deployLogs = remember { mutableStateListOf<String>() }

    fun addDeployLog(msg: String) {
        deployLogs.add(msg)
        LogManager.addLog("[SSH Deploy] $msg")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = DarkBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Деплой ноды OpenFlux",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть", color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Параметры SSH сервера",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("IP адрес / Host") },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = port,
                                    onValueChange = { port = it },
                                    label = { Text("Порт") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = user,
                                    onValueChange = { user = it },
                                    label = { Text("Пользователь") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Пароль SSH") },
                                    modifier = Modifier.weight(1f),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                            }
                            if (user != "root") {
                                OutlinedTextField(
                                    value = sudoPassword,
                                    onValueChange = { sudoPassword = it },
                                    label = { Text("Пароль Sudo (если нужен)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                            }
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Конфигурация канала",
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            OutlinedTextField(
                                value = documentUrl,
                                onValueChange = { documentUrl = UrlNormalizer.normalize(it) },
                                label = { Text("Ссылка на Яндекс Документ") },
                                placeholder = { Text("https://docs.yandex.ru/edit/d/...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = channelKey,
                                    onValueChange = { channelKey = it },
                                    label = { Text("AES-256 Ключ") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                Button(
                                    onClick = { channelKey = KeyGenerator.generate256BitHex() },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
                                ) {
                                    Text("Новый", fontSize = 12.sp)
                                }
                            }
                            OutlinedTextField(
                                value = channelPort,
                                onValueChange = { channelPort = it },
                                label = { Text("Порт прямого транспорта (Direct)") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }

                    if (deployLogs.isNotEmpty()) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = LogBackground)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Лог установки:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                deployLogs.takeLast(10).forEach { line ->
                                    Text(
                                        text = line,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    if (deployResult?.ok == true) {
                        val result = deployResult!!
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Нода успешно развернута!",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentSuccess,
                                    fontSize = 15.sp
                                )

                                val qrBitmap = remember(result.shareLink) {
                                    ShareLinkBuilder.generateQrBitmap(result.shareLink, 320)
                                }
                                if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR Code",
                                        modifier = Modifier.size(160.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        ConnectionRepository.setDocumentUrl(result.documentUrl)
                                        ConnectionRepository.setEncryptionKey(result.key)
                                        Toast.makeText(context, "Настройки применены к клиенту", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Применить настройки к клиенту")
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val hostClean = host.trim()
                            val docClean = UrlNormalizer.normalize(documentUrl)

                            if (hostClean.isEmpty()) {
                                Toast.makeText(context, "Укажите IP адрес сервера", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (!UrlNormalizer.isValid(docClean)) {
                                Toast.makeText(context, "Некорректная ссылка на документ", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val target = SshTarget(
                                host = hostClean,
                                port = port.toIntOrNull() ?: 22,
                                user = user.trim(),
                                password = password,
                                sudoPassword = sudoPassword
                            )
                            val config = ChannelConfig(
                                port = channelPort.toIntOrNull() ?: 8445,
                                documentUrl = docClean,
                                key = channelKey.trim()
                            )

                            deployState = DeployState.Running("SSH", "Начало развертывания")
                            deployLogs.clear()

                            coroutineScope.launch {
                                val res = SSHDeployer.deploy(target, config) { msg ->
                                    addDeployLog(msg)
                                }
                                deployResult = res
                                if (res.ok) {
                                    deployState = DeployState.Success(res)
                                } else {
                                    deployState = DeployState.Error(res.error ?: "Ошибка")
                                    addDeployLog("Ошибка: ${res.error}")
                                }
                            }
                        },
                        enabled = deployState !is DeployState.Running,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (deployState is DeployState.Running) {
                            CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Установка...")
                        } else {
                            Text("Запустить деплой на сервере")
                        }
                    }
                }
            }
        }
    }
}
