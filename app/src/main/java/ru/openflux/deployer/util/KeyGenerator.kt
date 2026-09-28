package ru.openflux.deployer.util

import java.security.SecureRandom

object KeyGenerator {
    private val secureRandom = SecureRandom()

    fun generate256BitHex(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val sb = StringBuilder(64)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun generateChannelId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder("of-")
        repeat(6) {
            sb.append(chars[secureRandom.nextInt(chars.length)])
        }
        return sb.toString()
    }
}
