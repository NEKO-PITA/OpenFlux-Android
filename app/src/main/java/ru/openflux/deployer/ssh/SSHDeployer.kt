package ru.openflux.deployer.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.openflux.deployer.data.ChannelConfig
import ru.openflux.deployer.data.DeployResult
import ru.openflux.deployer.data.ProbeResult
import ru.openflux.deployer.data.SshTarget
import ru.openflux.deployer.util.KeyGenerator
import ru.openflux.deployer.util.ShareLinkBuilder
import ru.openflux.deployer.util.UrlNormalizer
import java.io.BufferedReader
import java.io.InputStreamReader

object SSHDeployer {

    private const val PINNED_SCRIPT_URL =
        "https://raw.githubusercontent.com/p1neappleXpress/OpenFlux/38a65e5ab7c5d959dc0a4a2e4f3f7501d0e4fc81/deploy/node-install.sh"
    private const val PINNED_SHA256 =
        "48f2adb0b80701795180bed1ef33f5c916603f07957c1df41bdc917eeb630efa"

    suspend fun probe(
        target: SshTarget,
        onProgress: (String) -> Unit
    ): ProbeResult = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            onProgress("Подключение по SSH к ${target.host}:${target.port}...")
            session = openSession(target)

            onProgress("Загрузка и проверка скрипта установки node-install.sh...")
            val bootstrapCmd = buildBootstrapCmd("probe")
            val output = executeCommand(session, bootstrapCmd, target.sudoPassword)

            val json = extractJson(output)
            if (json == null || !json.optBoolean("ok", false)) {
                val err = json?.optString("error") ?: "Ошибка получения данных о сервере"
                return@withContext ProbeResult(ok = false, error = err)
            }

            val channelsArray = json.optJSONArray("channels")
            val channelList = mutableListOf<String>()
            if (channelsArray != null) {
                for (i in 0 until channelsArray.length()) {
                    channelList.add(channelsArray.getString(i))
                }
            }

            ProbeResult(
                ok = true,
                arch = json.optString("arch"),
                os = json.optString("os"),
                systemd = json.optBoolean("systemd"),
                sudo = json.optString("sudo"),
                firewall = json.optString("firewall"),
                channels = channelList
            )
        } catch (e: Exception) {
            ProbeResult(ok = false, error = e.localizedMessage ?: "Сбой подключения к серверу")
        } finally {
            session?.disconnect()
        }
    }

    suspend fun deploy(
        target: SshTarget,
        config: ChannelConfig,
        onProgress: (String) -> Unit
    ): DeployResult = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            val cleanUrl = UrlNormalizer.normalize(config.documentUrl)
            if (!UrlNormalizer.isValid(cleanUrl)) {
                return@withContext DeployResult(
                    ok = false,
                    error = "Некорректный адрес документа. Ожидается: https://docs.yandex.ru/edit/d/<ID>"
                )
            }

            val channelId = if (config.channel.isNotBlank()) config.channel else KeyGenerator.generateChannelId()
            val encryptionKey = if (config.key.isNotBlank()) config.key else KeyGenerator.generate256BitHex()

            onProgress("Подключение по SSH к ${target.host}...")
            session = openSession(target)

            onProgress("Проверка окружения сервера (systemd, архитектрура)...")
            val probeCmd = buildBootstrapCmd("probe")
            val probeOut = executeCommand(session, probeCmd, target.sudoPassword)
            val probeJson = extractJson(probeOut)
            if (probeJson == null || !probeJson.optBoolean("ok", false)) {
                return@withContext DeployResult(
                    ok = false,
                    error = probeJson?.optString("error") ?: "Сервер не прошел предварительную проверку"
                )
            }

            onProgress("Установка ядра OpenFlux, регистрация службы systemd и открытие порта ${config.port}...")
            val applyCmd = buildApplyCmd(channelId, cleanUrl, encryptionKey, config.port, target.user, target.sudoPassword)
            val applyOut = executeCommand(session, applyCmd, target.sudoPassword)
            val applyJson = extractJson(applyOut)

            if (applyJson == null || !applyJson.optBoolean("ok", false)) {
                val err = applyJson?.optString("error") ?: "Сбой на этапе применения конфигурации"
                return@withContext DeployResult(ok = false, error = err)
            }

            onProgress("Генерация ссылки подключения openflux://...")
            val shareLink = ShareLinkBuilder.buildLink(
                name = config.name.ifBlank { "MyOpenFluxNode" },
                documentUrl = cleanUrl,
                key = encryptionKey,
                host = target.host,
                port = config.port
            )

            onProgress("Развертывание ноды успешно завершено!")

            DeployResult(
                ok = true,
                channel = channelId,
                host = target.host,
                port = config.port,
                key = encryptionKey,
                documentUrl = cleanUrl,
                shareLink = shareLink
            )
        } catch (e: Exception) {
            DeployResult(ok = false, error = e.localizedMessage ?: "Ошибка выполнения развертывания")
        } finally {
            session?.disconnect()
        }
    }

    suspend fun remove(
        target: SshTarget,
        channel: String,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            onProgress("Подключение к ${target.host}...")
            session = openSession(target)

            onProgress("Удаление канала $channel...")
            val removeCmd = buildRemoveCmd(channel, target.user, target.sudoPassword)
            val out = executeCommand(session, removeCmd, target.sudoPassword)
            val json = extractJson(out)
            json?.optBoolean("ok", false) == true
        } catch (_: Exception) {
            false
        } finally {
            session?.disconnect()
        }
    }

    private fun openSession(target: SshTarget): Session {
        val jsch = JSch()
        if (target.privateKey.isNotBlank()) {
            jsch.addIdentity("key", target.privateKey.toByteArray(Charsets.UTF_8), null, null)
        }

        val session = jsch.getSession(target.user, target.host, target.port)
        if (target.password.isNotBlank()) {
            session.setPassword(target.password)
        }

        session.setConfig("StrictHostKeyChecking", "no")
        session.connect(15000)
        return session
    }

    private fun executeCommand(session: Session, command: String, sudoPass: String = ""): String {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        channel.setInputStream(null)

        val reader = BufferedReader(InputStreamReader(channel.inputStream, Charsets.UTF_8))
        channel.connect(20000)

        val output = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        channel.disconnect()
        return output.toString().trim()
    }

    private fun buildBootstrapCmd(action: String): String {
        return """
            f=${'$'}(mktemp /tmp/openflux-node-install.XXXXXX)
            u='$PINNED_SCRIPT_URL'
            h='$PINNED_SHA256'
            if command -v curl >/dev/null 2>&1; then
                curl -fsSL --retry 3 --connect-timeout 20 -o "${'$'}f" "${'$'}u"
            elif command -v wget >/dev/null 2>&1; then
                wget -q -T 20 -t 3 -O "${'$'}f" "${'$'}u"
            else
                echo '{"ok":false,"step":"fetch","error":"на сервере нет curl или wget"}'
                exit 1
            fi
            chmod 0700 "${'$'}f"
            "${'$'}f" $action
            rm -f "${'$'}f"
        """.trimIndent().replace("\n", " ; ")
    }

    private fun buildApplyCmd(
        channel: String,
        url: String,
        key: String,
        port: Int,
        user: String,
        sudoPass: String
    ): String {
        val sudoPrefix = when {
            user == "root" -> ""
            sudoPass.isNotBlank() -> "echo '$sudoPass' | sudo -S "
            else -> "sudo "
        }
        return """
            f=${'$'}(mktemp /tmp/openflux-node-install.XXXXXX)
            cfg=${'$'}(mktemp /tmp/openflux-cfg.XXXXXX)
            chmod 0600 "${'$'}cfg"
            printf 'channel=%s\nurl=%s\nkey=%s\nport=%d\n' '$channel' '$url' '$key' $port > "${'$'}cfg"
            u='$PINNED_SCRIPT_URL'
            if command -v curl >/dev/null 2>&1; then
                curl -fsSL --retry 3 --connect-timeout 20 -o "${'$'}f" "${'$'}u"
            else
                wget -q -T 20 -t 3 -O "${'$'}f" "${'$'}u"
            fi
            chmod 0700 "${'$'}f"
            $sudoPrefix "${'$'}f" apply "${'$'}cfg"
            rm -f "${'$'}f" "${'$'}cfg"
        """.trimIndent().replace("\n", " ; ")
    }

    private fun buildRemoveCmd(channel: String, user: String, sudoPass: String): String {
        val sudoPrefix = when {
            user == "root" -> ""
            sudoPass.isNotBlank() -> "echo '$sudoPass' | sudo -S "
            else -> "sudo "
        }
        return """
            f=${'$'}(mktemp /tmp/openflux-node-install.XXXXXX)
            cfg=${'$'}(mktemp /tmp/openflux-cfg.XXXXXX)
            chmod 0600 "${'$'}cfg"
            printf 'channel=%s\n' '$channel' > "${'$'}cfg"
            u='$PINNED_SCRIPT_URL'
            if command -v curl >/dev/null 2>&1; then
                curl -fsSL --retry 3 --connect-timeout 20 -o "${'$'}f" "${'$'}u"
            else
                wget -q -T 20 -t 3 -O "${'$'}f" "${'$'}u"
            fi
            chmod 0700 "${'$'}f"
            $sudoPrefix "${'$'}f" remove "${'$'}cfg"
            rm -f "${'$'}f" "${'$'}cfg"
        """.trimIndent().replace("\n", " ; ")
    }

    private fun extractJson(output: String): JSONObject? {
        val trimmed = output.trim()
        val start = trimmed.lastIndexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end != -1 && end > start) {
            val jsonSub = trimmed.substring(start, end + 1)
            return try {
                JSONObject(jsonSub)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}
