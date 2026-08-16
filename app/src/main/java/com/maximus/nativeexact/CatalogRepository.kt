package com.maximus.nativeexact

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object CatalogRepository {
    @Volatile private var cached: List<M3uItem> = emptyList()

    suspend fun load(context: Context, force: Boolean = false): List<M3uItem> {
        if (!force && cached.isNotEmpty()) return cached
        val result = withContext(Dispatchers.IO) { fetch(context) }
        if (result.isNotEmpty()) cached = result
        return result
    }

    fun current(): List<M3uItem> = cached

    fun clear() {
        cached = emptyList()
    }

    private fun fetch(context: Context): List<M3uItem> {
        val credentials = AppPreferences.credentials(context) ?: return emptyList()
        return runCatching {
            val user = java.net.URLEncoder.encode(credentials.username, "UTF-8")
            val pass = java.net.URLEncoder.encode(credentials.password, "UTF-8")
            val endpoint = "${credentials.server}/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MaximusPlayer/1.0 Android")
            }
            val text = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            connection.disconnect()
            if (text.contains("#EXTINF", true)) M3uParser.parse(text) else emptyList()
        }.getOrDefault(emptyList())
    }
}
