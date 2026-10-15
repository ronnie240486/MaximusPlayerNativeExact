package com.interactiveplayer.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    // _v4: correcao de seguranca importante - a classificacao anterior
    // podia deixar categoria adulta aparecer dentro da aba Kids
    // (mesmo que trancada) porque a exclusao so valia pra perfil
    // infantil, nao pro modo Kids em si. Forca reclassificar tudo.
    // _v5: o cache saiu do SharedPreferences (texto gigante na memória,
    // estourava em TV box fraca) e virou arquivo gravado em streaming.
    // Quem já tinha o app instalado busca a lista de novo uma vez.
    private const val CACHE_FILE = "catalog_cache_v8.txt"

    @Volatile private var cached: List<M3uItem> = emptyList()
    @Volatile var lastMessage: String = ""
        private set

    // Texto mostrado embaixo da barra de "Carregando seu conteúdo..." -- diz em
    // que etapa o carregamento está de verdade (os 90% da barra são só uma
    // animação). Serve pra saber onde trava numa TV box lenta.
    @Volatile var progressText: String = ""

    /**
     * Stale-while-revalidate: se já tiver algo salvo em disco de uma
     * abertura anterior, devolve isso na hora (a Home pinta instantâneo)
     * — quem chamar `load` de novo com `force = true` depois de mostrar
     * o cache é quem dispara a atualização de verdade em segundo plano.
     */
    // Sem isso, o pré-carregamento disparado no login (GlobalScope) e
    // a própria Home chamando load() de novo ao abrir podiam cair AO
    // MESMO TEMPO — duas passadas pesadas de parse/classificação
    // disputando a CPU fraca do TV box no mesmo instante, chegando a
    // travar o app de verdade (ANR, "aguardar ou fechar"). Com o
    // Mutex, quem chega depois só espera a carga que já está
    // rodando terminar, em vez de começar outra do zero em paralelo.
    private val loadMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun load(context: Context, force: Boolean = false): List<M3uItem> {
        if (!force && cached.isNotEmpty()) return cached
        return loadMutex.withLock {
            // Confere de novo aqui dentro: pode ser que quem tava
            // segurando o Mutex já tenha acabado de popular o cache
            // enquanto a gente esperava a vez.
            if (!force && cached.isNotEmpty()) return@withLock cached
            withContext(Dispatchers.IO) {
                if (!force) {
                    CrashLog.mark(context, "1 lendo cache salvo no aparelho")
                    val fromDisk = readDiskCache(context)
                    if (fromDisk.isNotEmpty()) {
                        cached = fromDisk
                        CrashLog.mark(context, "ok")
                        return@withContext fromDisk
                    }
                }
                CrashLog.mark(context, "2 baixando a lista do servidor")
                progressText = "Baixando a lista do painel…"
                val result = fetch(context)
                if (result.isNotEmpty()) {
                    cached = result
                    // A Home abre AGORA. O cache em disco (só serve pra próxima
                    // abertura ser instantânea) é gravado em segundo plano, fora
                    // do caminho -- antes a tela esperava a gravação terminar,
                    // e em lista de 200 mil itens isso segurava a abertura.
                    saveCacheInBackground(context.applicationContext, result)
                }
                CrashLog.mark(context, "ok")
                result
            }
        }
    }

    private val backgroundScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private fun saveCacheInBackground(appContext: Context, items: List<M3uItem>) {
        backgroundScope.launch {
            writeDiskCache(appContext, items)
        }
    }

    fun current(): List<M3uItem> = cached

    fun clear(context: Context) {
        cached = emptyList()
        lastMessage = ""
        runCatching { cacheFile(context).delete() }
        deleteOldPrefsCache(context)
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

        // O catálogo Xtream (live / filmes / séries) vem do painel JÁ separado por
        // tipo; o M3U não: o app tinha que ADIVINHAR filme, série e canal por palavras
        // no nome ("filme", "ação"...), e misturava tudo (novelas em Filmes, filmes em
        // Canais, episódios soltos em Séries). Por isso o Xtream vai primeiro; o M3U
        // fica de reserva para lista que não tem usuário/senha ou cujo painel não
        // responde à API.
        progressText = "Baixando o catálogo do painel…"
        val xtream = fetchXtreamParallel(context, playlist.url)
        if (xtream.isNotEmpty()) {
            CrashLog.mark(context, "3 catálogo Xtream lido (${xtream.size} itens)")
            lastMessage = "Catálogo Xtream carregado pelo painel."
            return xtream
        }
        progressText = "Catálogo Xtream indisponível — lendo a lista M3U…"
        val direct = fetchM3u(playlist.url)
        if (direct.isNotEmpty()) {
            CrashLog.mark(context, "3 lista M3U lida (${direct.size} itens)")
            lastMessage = "Playlist M3U carregada."
            return direct
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
        try {
            val code = connection.responseCode
            if (code !in 200..299) return@runCatching emptyList<M3uItem>()
            // Lê e interpreta linha por linha direto do stream (nada de
            // readText() da playlist inteira): numa TV box com pouca RAM,
            // segurar a playlist toda numa String + a lista de linhas
            // estourava a memória e o app fechava em "Carregando seu
            // conteúdo...". Se a resposta não for M3U (ex.: JSON de erro),
            // não tem nenhum #EXTINF e o resultado sai vazio, caindo no
            // caminho Xtream como antes.
            // Conta os bytes que já chegaram pra mostrar na tela se o que
            // está lento é a internet (MB quase parados) ou o processador
            // da box (MB subindo, itens subindo devagar).
            var bytes = 0L
            val startedAt = System.currentTimeMillis()
            val counting = object : java.io.FilterInputStream(connection.inputStream) {
                override fun read(): Int {
                    val r = super.read()
                    if (r >= 0) bytes++
                    return r
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val r = super.read(b, off, len)
                    if (r > 0) bytes += r
                    return r
                }
            }
            counting.bufferedReader().use { reader ->
                M3uParser.parse(reader) { count ->
                    val seconds = (System.currentTimeMillis() - startedAt) / 1000
                    progressText = "Lendo a lista: $count itens (${bytes / 1048576} MB, ${seconds}s)…"
                }
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(emptyList())

    /**
     * As 6 chamadas (3 categorias + 3 listas) rodam TODAS ao mesmo tempo
     * em vez de uma atrás da outra. Cada resposta é lida em streaming, item por
     * item (XtreamStreamParser), sem guardar o JSON inteiro na memória.
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

        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        val startedAt = System.currentTimeMillis()
        val onItem = {
            val n = counter.incrementAndGet()
            if (n % 2000 == 0) {
                val seconds = (System.currentTimeMillis() - startedAt) / 1000
                progressText = "Lendo o catálogo: $n itens (${seconds}s)…"
            }
        }
        fun endpoint(action: String) =
            "$server/player_api.php?username=${encode(username)}&password=${encode(password)}&action=$action"

        kotlinx.coroutines.runBlocking {
            coroutineScope {
                val liveCats = async(Dispatchers.IO) { readJson(endpoint("get_live_categories"), 20000) { XtreamStreamParser.categories(it) } ?: emptyMap() }
                val vodCats = async(Dispatchers.IO) { readJson(endpoint("get_vod_categories"), 20000) { XtreamStreamParser.categories(it) } ?: emptyMap() }
                val seriesCats = async(Dispatchers.IO) { readJson(endpoint("get_series_categories"), 20000) { XtreamStreamParser.categories(it) } ?: emptyMap() }

                val live = async(Dispatchers.IO) {
                    val cats = liveCats.await()
                    readJson(endpoint("get_live_streams"), 30000) { reader ->
                        ArrayList<M3uItem>().also { XtreamStreamParser.live(reader, cats, server, username, password, it, onItem) }
                    } ?: emptyList<M3uItem>()
                }
                val movies = async(Dispatchers.IO) {
                    val cats = vodCats.await()
                    readJson(endpoint("get_vod_streams"), 30000) { reader ->
                        ArrayList<M3uItem>().also { XtreamStreamParser.movies(reader, cats, server, username, password, it, onItem) }
                    } ?: emptyList<M3uItem>()
                }
                val series = async(Dispatchers.IO) {
                    val cats = seriesCats.await()
                    readJson(endpoint("get_series"), 30000) { reader ->
                        ArrayList<M3uItem>().also { XtreamStreamParser.series(reader, cats, server, username, password, it, onItem) }
                    } ?: emptyList<M3uItem>()
                }

                val result = ArrayList<M3uItem>()
                result.addAll(live.await())
                result.addAll(movies.await())
                result.addAll(series.await())
                result as List<M3uItem>
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Abre o endpoint e entrega o JSON ao [block] em streaming. Tenta 2 vezes
     * (get_vod_streams é enorme e às vezes engasga só nessa chamada); devolve
     * null se as duas falharem ou se a resposta não for o JSON esperado.
     */
    private fun <T> readJson(endpoint: String, timeout: Int, block: (android.util.JsonReader) -> T): T? {
        repeat(2) {
            val result = runCatching {
                val connection = open(endpoint, timeout)
                try {
                    if (connection.responseCode !in 200..299) null
                    else android.util.JsonReader(connection.inputStream.bufferedReader()).use { block(it) }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun open(url: String, timeout: Int): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = timeout
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "VLC/3.5.5 (Linux;Android 12) LibVLC/3.5.5")
        setRequestProperty("Connection", "close")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    // --- cache em disco: pinta a Home instantâneo nas aberturas seguintes ---

    // O cache agora é um ARQUIVO gravado/lido em streaming (JsonWriter /
    // JsonReader), não mais uma String gigante no SharedPreferences. Antes
    // o app montava um JSONArray com um JSONObject por item, depois
    // chamava toString() (mais uma cópia inteira) e ainda entregava isso ao
    // SharedPreferences, que segura tudo na memória -- em lista grande, numa
    // TV box com pouca RAM, isso estourava a memória no fim do
    // "Carregando seu conteúdo..." e o Android fechava o app.
    private fun cacheFile(context: Context) = java.io.File(context.filesDir, CACHE_FILE)

    private fun readDiskCache(context: Context): List<M3uItem> =
        runCatching { readCacheFrom(cacheFile(context)) }.getOrDefault(emptyList())

    private fun writeDiskCache(context: Context, items: List<M3uItem>) {
        runCatching {
            writeCacheTo(cacheFile(context), items)
            deleteOldPrefsCache(context)
        }
    }

    // Formato: uma linha por item, campos separados por TAB (nome, grupo, logo,
    // url, tipo, streamId). Bem mais rápido e leve que JSON em lista de 200 mil
    // itens: uma única escrita por linha, sem escapar caractere por caractere.
    // TAB/quebra de linha dentro de um campo viram espaço.
    internal fun writeCacheTo(file: java.io.File, items: List<M3uItem>) {
        // Grava num arquivo temporário e só troca no fim: se o app for fechado
        // no meio da gravação, não sobra um cache pela metade.
        val tmp = java.io.File(file.parentFile, "${file.name}.tmp")
        java.io.BufferedWriter(java.io.OutputStreamWriter(java.io.FileOutputStream(tmp), Charsets.UTF_8), 64 * 1024).use { w ->
            val sb = StringBuilder(512)
            for (item in items) {
                sb.setLength(0)
                sb.append(clean(item.name)).append('\t')
                    .append(clean(item.group)).append('\t')
                    .append(clean(item.logo ?: "")).append('\t')
                    .append(clean(item.url)).append('\t')
                    .append(item.kind.name).append('\t')
                    .append(clean(item.streamId ?: "")).append('\n')
                w.write(sb.toString())
            }
            w.write("#END\n")
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun clean(value: String): String =
        if (value.indexOf('\t') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) value
        else value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    internal fun readCacheFrom(file: java.io.File): List<M3uItem> {
        if (!file.exists()) return emptyList()
        val items = ArrayList<M3uItem>()
        val groups = HashMap<String, String>()
        var complete = false
        java.io.BufferedReader(java.io.InputStreamReader(java.io.FileInputStream(file), Charsets.UTF_8), 64 * 1024).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                if (line == "#END") { complete = true; break }
                val parts = line.split('\t')
                if (parts.size < 6) continue
                val group = groups.getOrPut(parts[1]) { parts[1] }
                val kind = runCatching { M3uItem.Kind.valueOf(parts[4]) }.getOrDefault(M3uItem.Kind.CHANNEL)
                items += M3uItem(
                    parts[0], group, parts[2].ifBlank { null }, parts[3], kind, parts[5].ifBlank { null },
                )
            }
        }
        // Cache sem a marca final = gravação interrompida: ignora e baixa de novo.
        return if (complete) items else emptyList()
    }

    // Apaga o cache antigo (SharedPreferences) direto pelo arquivo, SEM
    // abrir o SharedPreferences -- abrir carregaria de uma vez aquele texto
    // gigante na memória, que é justamente o que queremos evitar.
    private fun deleteOldPrefsCache(context: Context) {
        runCatching {
            java.io.File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_NAME.xml").delete()
        }
    }
}
