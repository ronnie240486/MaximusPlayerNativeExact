package com.interactiveplayer.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Temporadas e episódios de uma série, portado de
 * `frontend/src/lib/xtream.ts` (`XtreamSeriesInfo.episodes`, um mapa de
 * "número da temporada" -> lista de episódios) e
 * `app/series-details.tsx`, que é a tela que consome isso.
 *
 * Sem isso, o app tratava série como um único stream — "ASSISTIR"
 * tocava a URL da série (que na prática não funciona na maioria dos
 * painéis Xtream, já que cada EPISÓDIO tem seu próprio stream_id).
 */
object XtreamEpisodesClient {

    data class Episode(
        val id: String,
        val episodeNumber: Int,
        val title: String,
        val containerExtension: String,
        val plot: String?,
    )

    data class Season(val key: String, val episodes: List<Episode>)

    /** Deve ser chamado fora da thread principal. */
    fun fetch(context: Context, item: M3uItem): List<Season> {
        val streamId = item.streamId ?: return emptyList()
        val credentials = XtreamCredentials.load(context) ?: return emptyList()

        val endpoint = "${credentials.server}/player_api.php" +
            "?username=${encode(credentials.username)}" +
            "&password=${encode(credentials.password)}" +
            "&action=get_series_info&series_id=$streamId"

        val body = readText(endpoint) ?: return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val episodesNode = root.optJSONObject("episodes") ?: return@runCatching emptyList()
            val seasonKeys = episodesNode.keys().asSequence().toList()
                .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

            seasonKeys.map { key ->
                val array = episodesNode.optJSONArray(key)
                val episodes = buildList {
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            val node = array.optJSONObject(index) ?: continue
                            val id = node.optString("id")
                            if (id.isBlank()) continue
                            val info = node.optJSONObject("info")
                            add(
                                Episode(
                                    id = id,
                                    episodeNumber = node.optInt("episode_num", index + 1),
                                    title = node.optStringOrNull("title") ?: "Episódio ${index + 1}",
                                    containerExtension = node.optStringOrNull("container_extension") ?: "mp4",
                                    plot = info?.optStringOrNull("plot"),
                                )
                            )
                        }
                    }
                }.sortedBy { it.episodeNumber }
                Season(key, episodes)
            }
        }.getOrDefault(emptyList())
    }

    /** Mesmo padrão de URL das outras telas: server/series/usuário/senha/id.ext — só que aqui o ID é do EPISÓDIO, não da série. */
    fun episodeUrl(credentials: XtreamCredentials.Credentials, episode: Episode): String =
        "${credentials.server}/series/${credentials.username}/${credentials.password}/${episode.id}.${episode.containerExtension}"

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun readText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 10000
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
