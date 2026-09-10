package com.interactiveplayer.app

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Guia de programação (EPG) de um canal, via `get_short_epg` do Xtream
 * — o "Agora" / "A seguir" que aparece no player ao vivo do original.
 *
 * Reaproveita a mesma extração de servidor/usuário/senha/stream_id da
 * URL que o XtreamInfoClient já faz. Como alguns painéis preenchem
 * `direct_source` com uma URL que não segue o padrão
 * `live/usuario/senha/id.ext`, o EPG simplesmente não aparece nesses
 * casos — sem quebrar o player, só sem a informação extra.
 */
object EpgClient {

    data class Program(
        val title: String,
        val startLabel: String,
        val endLabel: String,
        val isNow: Boolean,
    )

    /** Deve ser chamado fora da thread principal. */
    fun fetchSchedule(item: M3uItem, limit: Int = 6): List<Program> {
        val parts = parseLiveUrl(item.url) ?: return emptyList()
        val endpoint = "${parts.server}/player_api.php" +
            "?username=${encode(parts.username)}" +
            "&password=${encode(parts.password)}" +
            "&action=get_short_epg&stream_id=${parts.streamId}&limit=$limit"

        val body = readText(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val listings = root.optJSONArray("epg_listings") ?: return@runCatching emptyList()
            val now = System.currentTimeMillis() / 1000
            buildList {
                for (index in 0 until listings.length()) {
                    val entry = listings.optJSONObject(index) ?: continue
                    val title = decodeBase64(entry.optString("title")).ifBlank { "Programação" }
                    val startTs = entry.optLong("start_timestamp")
                    val endTs = entry.optLong("stop_timestamp")
                    add(
                        Program(
                            title = title,
                            startLabel = formatTime(startTs),
                            endLabel = formatTime(endTs),
                            isNow = startTs <= now && now < endTs,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeBase64(value: String): String = runCatching {
        String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrDefault(value)

    private fun formatTime(epochSeconds: Long): String {
        if (epochSeconds <= 0) return "--:--"
        return runCatching {
            SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(epochSeconds * 1000)
        }.getOrDefault("--:--")
    }

    private data class Parts(val server: String, val username: String, val password: String, val streamId: String)

    private fun parseLiveUrl(url: String): Parts? = runCatching {
        val parsed = URL(url)
        val segments = parsed.path.trim('/').split('/')
        if (segments.size < 4 || segments[0] != "live") return@runCatching null
        val streamId = segments[3].substringBeforeLast('.')
        if (streamId.isBlank() || streamId.any { !it.isDigit() }) return@runCatching null
        val port = if (parsed.port > 0) ":${parsed.port}" else ""
        Parts("${parsed.protocol}://${parsed.host}$port", segments[1], segments[2], streamId)
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun readText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 6000
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
