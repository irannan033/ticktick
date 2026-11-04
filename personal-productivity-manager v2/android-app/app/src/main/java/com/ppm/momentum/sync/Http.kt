package com.ppm.momentum.sync

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpStatusException(val code: Int, message: String) : IOException(message)

/** Minimal JSON-over-HTTPS client (no extra dependency). Call from a background dispatcher. */
object Http {
    fun requireHttps(baseUrl: String) {
        if (!baseUrl.startsWith("https://")) throw IOException("Server URL must start with https://")
    }

    fun request(method: String, url: String, body: JSONObject?, token: String? = null): JSONObject {
        requireHttps(url)
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpStatusException(code, "Server answered $code: ${text.take(200)}")
            return JSONObject(text)
        } finally {
            c.disconnect()
        }
    }
}
