package com.interactiveplayer.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Busca no TMDB como reforço de metadados — muitos painéis Xtream têm
 * `plot`/`rating` vazios em `get_vod_info`/`get_series_info` (comum em
 * catálogos de revenda com pouco cadastro). Quando isso acontece, o
 * app busca o mesmo título no TMDB, que quase sempre tem sinopse, nota,
 * ano e capa reais.
 *
 * Sem chave (BuildConfig.TMDB_API_KEY vazia — secret TMDB_API_KEY não
 * configurado no Actions), essa classe não faz chamada nenhuma e a
 * tela cai de volta no texto padrão, sem quebrar nada.
 */
object TmdbClient {

    data class Info(
        val plot: String?,
        val genre: String?,
        val rating: String?,
        val year: String?,
        val backdrop: String?,
    )

    private val memoryCache = HashMap<String, Info?>()

    /** Deve ser chamado fora da thread principal. */
    fun search(rawTitle: String, isSeries: Boolean): Info? {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) return null

        val title = cleanTitle(rawTitle)
        if (title.isBlank()) return null

        val cacheKey = "$isSeries:$title"
        if (memoryCache.containsKey(cacheKey)) return memoryCache[cacheKey]

        val kind = if (isSeries) "tv" else "movie"
        val searchUrl = "https://api.themoviedb.org/3/search/$kind" +
            "?api_key=$apiKey&language=pt-BR&query=${URLEncoder.encode(title, "UTF-8")}"

        val info = runCatching {
            val results = JSONObject(readText(searchUrl) ?: return@runCatching null)
                .optJSONArray("results") ?: return@runCatching null
            if (results.length() == 0) return@runCatching null
            val first = results.optJSONObject(0) ?: return@runCatching null
            val id = first.optInt("id")

            // A busca já traz sinopse e nota; só falta o gênero, que só
            // vem no endpoint de detalhes.
            val detailUrl = "https://api.themoviedb.org/3/$kind/$id?api_key=$apiKey&language=pt-BR"
            val detail = runCatching { JSONObject(readText(detailUrl) ?: "") }.getOrNull()

            val releaseField = if (isSeries) "first_air_date" else "release_date"
            val genres = detail?.optJSONArray("genres")
            val genreName = genres?.optJSONObject(0)?.optString("name")

            Info(
                plot = first.optString("overview").ifBlank { null },
                genre = genreName,
                rating = first.optDouble("vote_average").takeIf { !it.isNaN() && it > 0 }
                    ?.let { "%.1f".format(it) },
                year = first.optString(releaseField).takeIf { it.length >= 4 }?.substring(0, 4),
                backdrop = first.optString("backdrop_path").ifBlank { null }
                    ?.let { "https://image.tmdb.org/t/p/w780$it" },
            )
        }.getOrNull()

        memoryCache[cacheKey] = info
        return info
    }

    /** Tira "(2026)", "[LEG]", "- ReelShorts" etc. pra melhorar a busca. */
    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("""\(\d{4}\)"""), "")
        .replace(Regex("""\[[^\]]*]"""), "")
        .replace(Regex("""[★✦•]"""), "")
        .replace(Regex("""\s*-\s*(ReelShorts?|Kiralik|LEG|DUB)\s*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun readText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 6000
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
