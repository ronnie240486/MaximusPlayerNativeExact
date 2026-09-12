package com.interactiveplayer.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca sinopse/nota/ano/backdrop de um título, como o hero da Home faz
 * em `frontend/app/home.tsx` (`xtream.vodInfo` / `xtream.seriesInfo`).
 *
 * Usa `item.streamId` + `XtreamCredentials` (as duas coisas que o
 * CatalogRepository já garante de verdade) em vez de tentar extrair
 * tudo de volta da URL de cada item — que falhava sempre que o painel
 * preenchia `direct_source` com um formato diferente do esperado.
 */
object XtreamInfoClient {

    data class Info(
        val plot: String?,
        val genre: String?,
        val rating: String?,
        val releaseDate: String?,
        val backdrop: String?,
        // O painel já resolve o trailer certo do YouTube pelo lado de
        // lá — é ISSO que o TrailerActivity deveria usar antes de
        // qualquer busca. Faltava eu ler esse campo.
        val youtubeTrailer: String?,
    ) {
        /** Só os 4 primeiros dígitos, igual ao `.slice(0, 4)` do original. */
        val year: String? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
    }

    private val memoryCache = HashMap<String, Info>()

    /** Deve ser chamado fora da thread principal. */
    fun fetch(context: Context, item: M3uItem): Info? {
        memoryCache[item.url]?.let { return it }
        if (item.kind == M3uItem.Kind.CHANNEL) return null

        val streamId = item.streamId ?: parseStreamIdFromUrl(item.url) ?: return null
        val credentials = XtreamCredentials.load(context) ?: return null

        val action = if (item.kind == M3uItem.Kind.SERIES) "get_series_info" else "get_vod_info"
        val idParam = if (item.kind == M3uItem.Kind.SERIES) "series_id" else "vod_id"

        val endpoint = "${credentials.server}/player_api.php" +
            "?username=${encode(credentials.username)}" +
            "&password=${encode(credentials.password)}" +
            "&action=$action&$idParam=$streamId"

        val body = readText(endpoint) ?: return null
        val info = runCatching {
            val root = JSONObject(body)
            val node = root.optJSONObject("info") ?: return@runCatching null
            Info(
                plot = node.optStringOrNull("plot"),
                genre = node.optStringOrNull("genre"),
                rating = node.optStringOrNull("rating"),
                // O Xtream é inconsistente no nome desse campo, então o
                // original tenta os três — aqui é a mesma coisa.
                releaseDate = node.optStringOrNull("releasedate")
                    ?: node.optStringOrNull("release_date")
                    ?: node.optStringOrNull("releaseDate"),
                backdrop = node.optJSONArray("backdrop_path")
                    ?.takeIf { it.length() > 0 }
                    ?.optString(0)
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
                youtubeTrailer = node.optStringOrNull("youtube_trailer"),
            )
        }.getOrNull() ?: return null

        memoryCache[item.url] = info
        return info
    }

    /**
     * Retrocompatibilidade: itens que ainda não têm `streamId` guardado
     * (playlist M3U comum, ou cache antigo em disco) caem de volta pra
     * tentar extrair da URL — melhor que nada.
     */
    private fun parseStreamIdFromUrl(url: String): String? = runCatching {
        val parsed = URL(url)
        val segments = parsed.path.trim('/').split('/')
        if (segments.size < 4) return@runCatching null
        if (segments[0] !in listOf("movie", "series", "live")) return@runCatching null
        val streamId = segments[3].substringBeforeLast('.')
        streamId.takeIf { it.isNotBlank() && it.all { ch -> ch.isDigit() } }
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun readText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
