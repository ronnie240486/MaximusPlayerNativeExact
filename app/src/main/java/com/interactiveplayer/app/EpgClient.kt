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

    // Motivo da última falha, mostrado na tela quando não há programação -- numa
    // TV box sem cabo/logcat é a única forma de saber se foi rede, painel ou dado.
    @Volatile var lastError: String = ""
        private set

    /** Deve ser chamado fora da thread principal. "Agora tocando" de um canal, para listas grandes (com cache). */
    fun fetchNowTitle(context: Context, item: M3uItem): String? {
        val streamId = item.streamId ?: return null
        if (nowTitleCache.containsKey(streamId)) return nowTitleCache[streamId]
        // Só guarda no cache quando o painel REALMENTE respondeu. Antes, uma falha de
        // rede (timeout) também era guardada como "sem programação" e nunca mais
        // tentava de novo naquele canal até fechar o app.
        val schedule = fetchScheduleOrNull(context, item, limit = 2) ?: return null
        val now = schedule.firstOrNull { it.isNow }?.title
        nowTitleCache[streamId] = now
        return now
    }

    /** Deve ser chamado fora da thread principal. */
    fun fetchSchedule(context: Context, item: M3uItem, limit: Int = 6): List<Program> =
        fetchScheduleOrNull(context, item, limit) ?: emptyList()

    /** null = falhou (rede/painel); lista vazia = o painel respondeu mas o canal não tem guia. */
    private fun fetchScheduleOrNull(context: Context, item: M3uItem, limit: Int): List<Program>? {
        val streamId = item.streamId
        if (streamId == null) { lastError = "canal sem ID de stream"; return null }
        val credentials = XtreamCredentials.load(context)
        if (credentials == null) { lastError = "sem usuário/senha salvos do painel"; return null }

        val endpoint = "${credentials.server}/player_api.php" +
            "?username=${encode(credentials.username)}" +
            "&password=${encode(credentials.password)}" +
            "&action=get_short_epg&stream_id=$streamId&limit=$limit"

        val body = readText(endpoint) ?: return null
        return runCatching {
            val root = JSONObject(body)
            val listings = root.optJSONArray("epg_listings") ?: return@runCatching emptyList<Program>()
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
        }.getOrElse { lastError = "resposta do painel ilegível (${it.javaClass.simpleName})"; null }
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

    // Antes: 5s para conectar e 6s para ler, sem nova tentativa. Numa TV box lenta (CPU
    // ocupada com a lista e as imagens) isso estourava e a tela dizia "sem
    // programação". Agora espera mais e tenta 2 vezes.
    private fun readText(url: String): String? {
        repeat(2) {
            val result = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 10000
                    connection.readTimeout = 15000
                    connection.setRequestProperty("User-Agent", "VLC/3.5.5 (Linux;Android 12) LibVLC/3.5.5")
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        lastError = "painel respondeu HTTP $code"
                        null
                    } else {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure { lastError = "falha de rede (${it.javaClass.simpleName})" }.getOrNull()
            if (result != null) return result
        }
        return null
    }
}
