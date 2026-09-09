package com.interactiveplayer.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca sinopse/nota/ano/backdrop de um título, como o hero da Home faz
 * em `frontend/app/home.tsx` (`xtream.vodInfo` / `xtream.seriesInfo`).
 *
 * O M3uItem do nativo não guarda credenciais nem o stream_id separados —
 * mas o CatalogRepository monta a URL no formato
 * `$server/movie/$username/$password/$streamId.$ext`, então dá pra
 * recuperar tudo de volta a partir dela, sem precisar mexer no catálogo.
 */
object XtreamInfoClient {

    data class Info(
        val plot: String?,
        val genre: String?,
        val rating: String?,
        val releaseDate: String?,
        val backdrop: String?,
    ) {
        /** Só os 4 primeiros dígitos, igual ao `.slice(0, 4)` do original. */
        val year: String? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
    }

    private val memoryCache = HashMap<String, Info>()

    /** Deve ser chamado fora da thread principal. */
    fun fetch(item: M3uItem): Info? {
        memoryCache[item.url]?.let { return it }

        val parts = parse(item.url) ?: return null
        val action = when (item.kind) {
            M3uItem.Kind.SERIES -> "get_series_info"
            else -> "get_vod_info"
        }
        val idParam = when (item.kind) {
            M3uItem.Kind.SERIES -> "series_id"
            else -> "vod_id"
        }

        val endpoint = "${parts.server}/player_api.php" +
            "?username=${encode(parts.username)}" +
            "&password=${encode(parts.password)}" +
            "&action=$action&$idParam=${parts.streamId}"

        val body = readText(endpoint) ?: return null
        val info = runCatching {
            val root = JSONObject(body)
            val node = root.optJSONObject("info") ?: return@runCatching null
            Info(
                plot = node.optString("plot").ifBlank { null },
                genre = node.optString("genre").ifBlank { null },
                rating = node.optString("rating").ifBlank { null },
                // O Xtream é inconsistente no nome desse campo, então o
                // original tenta os três — aqui é a mesma coisa.
                releaseDate = node.optString("releasedate")
                    .ifBlank { node.optString("release_date") }
                    .ifBlank { node.optString("releaseDate") }
                    .ifBlank { null },
                backdrop = node.optJSONArray("backdrop_path")
                    ?.takeIf { it.length() > 0 }
                    ?.optString(0)
                    ?.ifBlank { null },
            )
        }.getOrNull() ?: return null

        memoryCache[item.url] = info
        return info
    }

    private data class Parts(
        val server: String,
        val username: String,
        val password: String,
        val streamId: String,
    )

    /**
     * Quebra `http://host:porta/movie/usuario/senha/12345.mp4` nas suas
     * partes. Devolve null para playlists M3U comuns, que não seguem esse
     * formato — nesse caso o hero simplesmente fica sem sinopse.
     */
    private fun parse(url: String): Parts? = runCatching {
        val parsed = URL(url)
        val segments = parsed.path.trim('/').split('/')
        if (segments.size < 4) return@runCatching null

        val type = segments[0]
        if (type != "movie" && type != "series" && type != "live") return@runCatching null

        val fileName = segments[3]
        val streamId = fileName.substringBeforeLast('.')
        if (streamId.isBlank() || streamId.any { !it.isDigit() }) return@runCatching null

        val port = if (parsed.port > 0) ":${parsed.port}" else ""
        Parts(
            server = "${parsed.protocol}://${parsed.host}$port",
            username = segments[1],
            password = segments[2],
            streamId = streamId,
        )
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
