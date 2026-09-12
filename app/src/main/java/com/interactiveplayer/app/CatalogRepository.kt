package com.interactiveplayer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

object CatalogRepository {
    private const val PREFS_NAME = "catalog_cache"
    // _v2: o cache antigo nunca guardava o streamId (bug corrigido
    // agora) — mudar a chave faz quem já tinha o app instalado buscar
    // tudo de novo uma vez, com o streamId de verdade desta vez, em vez
    // de continuar preso num cache antigo que nunca teria essa
    // informação de qualquer jeito.
    private const val CACHE_KEY = "items_json_v2"

    @Volatile private var cached: List<M3uItem> = emptyList()
    @Volatile var lastMessage: String = ""
        private set

    /**
     * Stale-while-revalidate: se já tiver algo salvo em disco de uma
     * abertura anterior, devolve isso na hora (a Home pinta instantâneo)
     * — quem chamar `load` de novo com `force = true` depois de mostrar
     * o cache é quem dispara a atualização de verdade em segundo plano.
     */
    suspend fun load(context: Context, force: Boolean = false): List<M3uItem> {
        if (!force && cached.isNotEmpty()) return cached
        if (!force) {
            val fromDisk = readDiskCache(context)
            if (fromDisk.isNotEmpty()) {
                cached = fromDisk
                return fromDisk
            }
        }
        val result = withContext(Dispatchers.IO) { fetch(context) }
        if (result.isNotEmpty()) {
            cached = result
            writeDiskCache(context, result)
        }
        return result
    }

    fun current(): List<M3uItem> = cached

    fun clear(context: Context) {
        cached = emptyList()
        lastMessage = ""
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
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
        // Precisa rodar SEMPRE, não só quando o catálogo Xtream (JSON)
        // é usado: a maioria das listas Xtream na prática chega como
        // M3U puro (get.php?...&type=m3u_plus), que tem usuário/senha
        // na própria URL mas nunca passava por fetchXtreamParallel — e
        // era só ali que as credenciais eram salvas. Sem elas, EPG e
        // sinopse nunca tinham como funcionar, mesmo com o streamId
        // certo em mãos.
        extractAndSaveCredentials(context, playlist.url)

        val direct = fetchM3u(playlist.url)
        if (direct.isNotEmpty()) {
            lastMessage = "Playlist M3U carregada."
            return direct
        }
        val xtream = fetchXtreamParallel(context, playlist.url)
        if (xtream.isNotEmpty()) {
            lastMessage = "Catálogo Xtream carregado pelo painel."
            return xtream
        }
        lastMessage = "A playlist devolvida pelo painel não respondeu como M3U/Xtream."
        return emptyList()
    }

    private fun extractAndSaveCredentials(context: Context, playlistUrl: String) {
        runCatching {
            val parsed = URL(playlistUrl)
            val params = parsed.query.orEmpty().split('&').mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) URLDecoder.decode(pieces[0], "UTF-8") to URLDecoder.decode(pieces[1], "UTF-8") else null
            }.toMap()
            val username = params["username"] ?: params["user"] ?: return
            val password = params["password"] ?: params["pass"] ?: return
            val server = "${parsed.protocol}://${parsed.authority}"
            XtreamCredentials.save(context, server, username, password)
        }
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

    /**
     * As 6 chamadas (3 categorias + 3 listas) rodam TODAS ao mesmo tempo
     * em vez de uma atrás da outra — antes isso somava até 6x o tempo de
     * espera de cada chamada individual, e era a causa real da Home
     * demorar tanto pra aparecer.
     */
    private fun fetchXtreamParallel(context: Context, playlistUrl: String): List<M3uItem> = runCatching {
        val parsed = URL(playlistUrl)
        val params = parsed.query.orEmpty().split('&').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size == 2) URLDecoder.decode(pieces[0], "UTF-8") to URLDecoder.decode(pieces[1], "UTF-8") else null
        }.toMap()
        val username = params["username"] ?: params["user"] ?: return@runCatching emptyList()
        val password = params["password"] ?: params["pass"] ?: return@runCatching emptyList()
        val server = "${parsed.protocol}://${parsed.authority}"
        // Salva aqui — é o único lugar que já faz esse parsing com
        // sucesso. XtreamInfoClient/EpgClient usam depois, sem precisar
        // adivinhar de volta a partir da URL de cada item.
        XtreamCredentials.save(context, server, username, password)

        kotlinx.coroutines.runBlocking {
            coroutineScope {
                val liveCategoriesDeferred = async(Dispatchers.IO) { categories(server, username, password, "get_live_categories") }
                val vodCategoriesDeferred = async(Dispatchers.IO) { categories(server, username, password, "get_vod_categories") }
                val seriesCategoriesDeferred = async(Dispatchers.IO) { categories(server, username, password, "get_series_categories") }
                val liveDeferred = async(Dispatchers.IO) { jsonArray(server, username, password, "get_live_streams") }
                val moviesDeferred = async(Dispatchers.IO) { jsonArray(server, username, password, "get_vod_streams") }
                val seriesDeferred = async(Dispatchers.IO) { jsonArray(server, username, password, "get_series") }

                val liveCategories = liveCategoriesDeferred.await()
                val vodCategories = vodCategoriesDeferred.await()
                val seriesCategories = seriesCategoriesDeferred.await()
                val live = liveDeferred.await()
                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()

                val result = ArrayList<M3uItem>()
                for (index in 0 until live.length()) {
                    val item = live.optJSONObject(index) ?: continue
                    val name = item.optString("name").ifBlank { "Canal" }
                    val group = liveCategories[item.optString("category_id")] ?: "Canais"
                    val streamId = item.optString("stream_id")
                    val url = item.optString("direct_source").ifBlank { "$server/live/$username/$password/$streamId.ts" }
                    if (streamId.isNotBlank()) result += M3uItem(name, group, item.optString("stream_icon").ifBlank { null }, url, M3uItem.Kind.CHANNEL, streamId)
                }
                for (index in 0 until movies.length()) {
                    val item = movies.optJSONObject(index) ?: continue
                    val name = item.optString("name").ifBlank { "Filme" }
                    val group = vodCategories[item.optString("category_id")] ?: "Filmes"
                    val streamId = item.optString("stream_id")
                    val ext = item.optString("container_extension").ifBlank { "mp4" }
                    if (streamId.isNotBlank()) result += M3uItem(name, group, item.optString("stream_icon").ifBlank { null }, "$server/movie/$username/$password/$streamId.$ext", classifyMovie(name, group), streamId)
                }
                for (index in 0 until series.length()) {
                    val item = series.optJSONObject(index) ?: continue
                    val name = item.optString("name").ifBlank { "Série" }
                    val group = seriesCategories[item.optString("category_id")] ?: "Séries"
                    val seriesId = item.optString("series_id")
                    if (seriesId.isNotBlank()) result += M3uItem(name, group, item.optString("cover").ifBlank { null }, "$server/series/$username/$password/$seriesId.mp4", classifySeries(name, group), seriesId)
                }
                result
            }
        }
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

    // --- cache em disco: pinta a Home instantâneo nas aberturas seguintes ---

    private fun readDiskCache(context: Context): List<M3uItem> = runCatching {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(CACHE_KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val kind = runCatching { M3uItem.Kind.valueOf(obj.optString("kind")) }.getOrDefault(M3uItem.Kind.CHANNEL)
            M3uItem(
                name = obj.optString("name"),
                group = obj.optString("group"),
                logo = obj.optString("logo").ifBlank { null },
                url = obj.optString("url"),
                kind = kind,
                streamId = obj.optStringOrNull("streamId"),
            )
        }
    }.getOrDefault(emptyList())

    private fun writeDiskCache(context: Context, items: List<M3uItem>) {
        runCatching {
            val array = JSONArray()
            items.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("name", item.name)
                        put("group", item.group)
                        put("logo", item.logo ?: "")
                        put("url", item.url)
                        put("kind", item.kind.name)
                        put("streamId", item.streamId ?: "")
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHE_KEY, array.toString())
                .apply()
        }
    }
}
