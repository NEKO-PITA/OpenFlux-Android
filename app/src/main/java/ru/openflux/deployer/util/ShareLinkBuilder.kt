package ru.openflux.deployer.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

object ShareLinkBuilder {

    fun buildLink(
        name: String,
        documentUrl: String,
        key: String,
        host: String,
        port: Int
    ): String {
        val root = JSONObject().apply {
            put("name", name)
            put("negotiate", true)
            put("secret", key)
            put("context", documentUrl)

            val transports = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "vyandex")
                    put("url", documentUrl)
                    put("priority", 100)
                })
                put(JSONObject().apply {
                    put("type", "direct")
                    put("dial", "$host:$port")
                    put("priority", 50)
                })
            }
            put("transports", transports)
        }

        val jsonBytes = root.toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(jsonBytes)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(jsonBytes.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        deflater.end()

        val compressed = outputStream.toByteArray()
        val base64Url = Base64.encodeToString(
            compressed,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        return "openflux://v1/$base64Url"
    }

    fun generateQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}
