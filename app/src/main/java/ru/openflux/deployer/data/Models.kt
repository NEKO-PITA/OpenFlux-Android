package ru.openflux.deployer.data

data class SshTarget(
    val host: String = "",
    val port: Int = 22,
    val user: String = "root",
    val password: String = "",
    val sudoPassword: String = "",
    val privateKey: String = ""
)

data class ChannelConfig(
    val channel: String = "",
    val port: Int = 8445,
    val documentUrl: String = "",
    val key: String = "",
    val name: String = "MyOpenFluxNode"
)

data class ProbeResult(
    val ok: Boolean = false,
    val arch: String = "",
    val os: String = "",
    val systemd: Boolean = false,
    val sudo: String = "",
    val firewall: String = "",
    val channels: List<String> = emptyList(),
    val error: String? = null
)

data class DeployResult(
    val ok: Boolean = false,
    val channel: String = "",
    val host: String = "",
    val port: Int = 8445,
    val key: String = "",
    val documentUrl: String = "",
    val shareLink: String = "",
    val error: String? = null
)

sealed class DeployState {
    object Idle : DeployState()
    data class Running(val step: String, val message: String) : DeployState()
    data class Success(val result: DeployResult) : DeployState()
    data class Error(val message: String) : DeployState()
}
