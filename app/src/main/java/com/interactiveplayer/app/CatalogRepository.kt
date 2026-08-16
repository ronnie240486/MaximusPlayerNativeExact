package com.interactiveplayer.app

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
        val session = MacSessionStore.load(context) ?: return emptyList()
        val playlist = session.activePlaylist(context) ?: return emptyList()
        return runCatching {
            val connection = (URL(playlist.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 15000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MaximusPlayer/1.0 Android")
                setRequestProperty("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, */*")
            }
            val text = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            connection.disconnect()
            if (text.contains("#EXTINF", true)) M3uParser.parse(text) else emptyList()
        }.getOrDefault(emptyList())
    }
}
