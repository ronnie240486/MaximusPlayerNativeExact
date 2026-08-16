package com.interactiveplayer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

object CatalogRepository {
    @Volatile private var cached: List<M3uItem> = emptyList()
    @Volatile var lastMessage: String = ""
        private set

    suspend fun load(context: Context, force: Boolean = false): List<M3uItem> {
        if (!force && cached.isNotEmpty()) return cached
        val result = withContext(Dispatchers.IO) { fetch(context) }
        if (result.isNotEmpty()) cached = result
        return result
    }

    fun current(): List<M3uItem> = cached

    fun clear() {
        cached = emptyList()
        lastMessage = ""
    }

    private fun fetch(context: Context): List<M3uItem> {
        val session = MacSessionStore.load(context)
        if (session == null) {
            lastMessage = "Sessão MAC não encontrada."
            return emptyList()
        }
        val playlist = session.activePlaylist(context)
        if (playlist == null) {
            lastMessage = "Nenhuma playlist foi devolvida para este MAC."
            return emptyList()
        }
        val direct = fetchM3u(playlist.url)
        if (direct.isNotEmpty()) {
            lastMessage = "Playlist M3U carregada."
            return direct
        }
        val xtream = fetchXtream(playlist.url)
        if (xtream.isNotEmpty()) {
            lastMessage = "Catálogo Xtream carregado pelo painel."
            return xtream
        }
        lastMessage = "A playlist devolvida pelo painel não respondeu como M3U/Xtream."
        return emptyList()
    }

    private fun fetchM3u(rawUrl: String): List<M3uItem> = runCatching {
        val connection = open(rawUrl, 30000).apply {
            setRequestProperty("Accept", "audio/x-mpegurl, application/vnd.apple.mpegurl, text/plain, application/json, */*")
            setRequestProperty("Referer", rawUrl)
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) return@runCatching emptyList()
        if (text.contains("#EXTINF", true) || text.contains("#EXTM3U", true)) M3uParser.parse(text) else emptyList()
    }.getOrDefault(emptyList())

    private fun fetchXtream(playlistUrl: String): List<M3uItem> = runCatching {
        val parsed = URL(playlistUrl)
        val params = parsed.query.orEmpty().split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2) URLDecoder.decode(pieces[0], "UTF-8") to URLDecoder.decode(pieces[1], "UTF-8") else null
        }.toMap()
        val username = params["username"] ?: params["user"] ?: return@runCatching emptyList()
        val password = params["password"] ?: params["pass"] ?: return@runCatching emptyList()
        val server = "${parsed.protocol}://${parsed.authority}"
        val liveCategories = categories(server, username, password, "get_live_categories")
        val vodCategories = categories(server, username, password, "get_vod_categories")
        val seriesCategories = categories(server, username, password, "get_series_categories")
        val result = ArrayList<M3uItem>()
        val live = jsonArray(server, username, password, "get_live_streams")
        for (index in 0 until live.length()) {
            val item = live.optJSONObject(index) ?: continue
            val name = item.optString("name").ifBlank { "Canal" }
            val group = liveCategories[item.optString("category_id")] ?: "Canais"
            val streamId = item.optString("stream_id")
            val url = item.optString("direct_source").ifBlank { "$server/live/$username/$password/$streamId.ts" }
            if (streamId.isNotBlank()) result += M3uItem(name, group, item.optString("stream_icon").ifBlank { null }, url, M3uItem.Kind.CHANNEL)
        }
        val movies = jsonArray(server, username, password, "get_vod_streams")
        for (index in 0 until movies.length()) {
            val item = movies.optJSONObject(index) ?: continue
            val name = item.optString("name").ifBlank { "Filme" }
            val group = vodCategories[item.optString("category_id")] ?: "Filmes"
            val streamId = item.optString("stream_id")
            val ext = item.optString("container_extension").ifBlank { "mp4" }
            if (streamId.isNotBlank()) result += M3uItem(name, group, item.optString("stream_icon").ifBlank { null }, "$server/movie/$username/$password/$streamId.$ext", classifyMovie(name, group))
        }
        val series = jsonArray(server, username, password, "get_series")
        for (index in 0 until series.length()) {
            val item = series.optJSONObject(index) ?: continue
            val name = item.optString("name").ifBlank { "Série" }
            val group = seriesCategories[item.optString("category_id")] ?: "Séries"
            val seriesId = item.optString("series_id")
            if (seriesId.isNotBlank()) result += M3uItem(name, group, item.optString("cover").ifBlank { null }, "$server/series/$username/$password/$seriesId.mp4", classifySeries(name, group))
        }
        result
    }.getOrDefault(emptyList())

    private fun classifyMovie(name: String, group: String): M3uItem.Kind {
        val value = "$name $group".lowercase()
        return if (listOf("kids", "infantil", "desenho", "cartoon", "children").any(value::contains)) M3uItem.Kind.KIDS else M3uItem.Kind.MOVIE
    }

    private fun classifySeries(name: String, group: String): M3uItem.Kind {
        val value = "$name $group".lowercase()
        return if (listOf("kids", "infantil", "desenho", "cartoon", "children").any(value::contains)) M3uItem.Kind.KIDS else M3uItem.Kind.SERIES
    }

    private fun categories(server: String, username: String, password: String, action: String): Map<String, String> {
        val json = jsonArray(server, username, password, action)
        return buildMap {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val id = item.optString("category_id")
                val name = item.optString("category_name")
                if (id.isNotBlank() && name.isNotBlank()) put(id, name)
            }
        }
    }

    private fun jsonArray(server: String, username: String, password: String, action: String): JSONArray {
        val endpoint = "$server/player_api.php?username=${encode(username)}&password=${encode(password)}&action=$action"
        val connection = open(endpoint, 30000)
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299 || body.isBlank()) return JSONArray()
        return runCatching { JSONArray(body) }.getOrElse { JSONArray() }
    }

    private fun open(url: String, timeout: Int): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = timeout
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) ExoPlayerLib/2.19.1")
        setRequestProperty("Connection", "close")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
