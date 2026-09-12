package com.interactiveplayer.app

import android.content.Context
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
 * Usa `item.streamId` + `XtreamCredentials`, salvos pelo
 * CatalogRepository, em vez de tentar extrair tudo de volta da URL do
 * canal — que falhava sempre que o painel preenchia `direct_source`
 * com um formato diferente do padrão live/usuário/senha/id.ext.
 */
object EpgClient {

    data class Program(
        val title: String,
        val startLabel: String,
        val endLabel: String,
        val isNow: Boolean,
    )

    // Cache simples em memória — evita rebuscar o mesmo canal toda vez
    // que a linha dele volta a ficar visível ao rolar a lista.
    private val nowTitleCache = HashMap<String, String?>()

    /** Deve ser chamado fora da thread principal. "Agora tocando" de um canal, para listas grandes (com cache). */
    fun fetchNowTitle(context: Context, item: M3uItem): String? {
        val streamId = item.streamId ?: return null
        if (nowTitleCache.containsKey(streamId)) return nowTitleCache[streamId]
        val now = fetchSchedule(context, item, limit = 2).firstOrNull { it.isNow }?.title
        nowTitleCache[streamId] = now
        return now
    }

    /** Deve ser chamado fora da thread principal. */
    fun fetchSchedule(context: Context, item: M3uItem, limit: Int = 6): List<Program> {
        val streamId = item.streamId ?: return emptyList()
        val credentials = XtreamCredentials.load(context) ?: return emptyList()

        val endpoint = "${credentials.server}/player_api.php" +
            "?username=${encode(credentials.username)}" +
            "&password=${encode(credentials.password)}" +
            "&action=get_short_epg&stream_id=$streamId&limit=$limit"

        val body = readText(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val listings = root.optJSONArray("epg_listings") ?: return@runCatching emptyList()
            val now = System.currentTimeMillis() / 1000
            buildList {
                for (index in 0 until listings.length()) {
                    val entry = listings.optJSONObject(index) ?: continue
                    val title = entry.optStringOrNull("title")
                        ?.let { decodeBase64(it) }
                        ?.takeIf { it.isNotBlank() }
                        ?: "Programação"
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

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun readText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 6000
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
