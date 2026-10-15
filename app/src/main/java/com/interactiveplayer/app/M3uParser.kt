package com.interactiveplayer.app

import java.net.URLDecoder

/** Modelo mínimo e testável para uma entrada IPTV da playlist M3U. */
data class M3uItem(
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
    val kind: Kind,
    // Guardado só quando vem direto do Xtream (não de playlist M3U comum).
    // Evita ter que tentar extrair o ID de volta da URL — que falha
    // sempre que o painel preenche "direct_source" com um formato
    // diferente do padrão live/movie/series/usuario/senha/id.ext.
    val streamId: String? = null,
) {
    enum class Kind { CHANNEL, MOVIE, SERIES, KIDS }
}

object M3uParser {
    fun parse(content: String): List<M3uItem> = parse(content.reader().buffered())

    /**
     * Lê a playlist linha por linha, direto do stream. Antes o app lia a
     * playlist INTEIRA numa String e ainda montava uma segunda lista com
     * TODAS as linhas (cópias aparadas) antes de começar -- em playlist
     * grande (100 mil+ itens) isso é 3x o tamanho do arquivo na memória, e
     * TV box fraca (pouca RAM) estourava a memória em "Carregando seu
     * conteúdo..." e o Android fechava o app.
     */
    fun parse(reader: java.io.BufferedReader, onProgress: ((Int) -> Unit)? = null): List<M3uItem> {
        val result = ArrayList<M3uItem>()
        var info: String? = null
        // Uma playlist tem dezenas de milhares de itens mas só algumas
        // centenas de GRUPOS. Decidir "esse grupo é Kids?" uma vez por grupo
        // (e não uma vez por item, com ~50 buscas de texto cada) é o que
        // mais tira tempo de processamento numa TV box fraca.
        class GroupInfo(val name: String, val kids: Boolean)
        val groups = HashMap<String, GroupInfo>()
        while (true) {
            val line = (reader.readLine() ?: break).trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> info = line
                line.startsWith("#") -> Unit
                info != null -> {
                    val header = info!!
                    // Minúsculas UMA vez por item: as 3 buscas de atributo abaixo
                    // viram indexOf comum (rápido). O jeito antigo usava
                    // indexOf(..., ignoreCase = true) 3 vezes por item, que é
                    // bem lento -- em lista de 200 mil itens, numa TV box
                    // fraca, isso era o que mais demorava.
                    val lower = header.lowercase().takeIf { it.length == header.length }
                    val name = attribute(header, lower, "tvg-name=\"") ?: header.substringAfterLast(",", "Sem título").trim()
                    val rawGroup = attribute(header, lower, "group-title=\"") ?: "Sem categoria"
                    val logo = attribute(header, lower, "tvg-logo=\"")
                    val url = decode(line)
                    // Um único String por grupo (em vez de uma cópia por item,
                    // 200 mil vezes) -- economiza bastante memória.
                    val info2 = groups.getOrPut(rawGroup) {
                        GroupInfo(rawGroup, KidsContent.matches(rawGroup) && !AdultContent.isAdultGroup(rawGroup))
                    }
                    result += M3uItem(name, info2.name, logo, url, classify(name, info2.name, info2.kids), streamIdFromUrl(url))
                    if (onProgress != null && result.size % 2000 == 0) onProgress(result.size)
                    info = null
                }
            }
        }
        return result
    }

    // marker já vem em minúsculas e com o `="` (ex.: tvg-name=").
    private fun attribute(line: String, lowerLine: String?, marker: String): String? {
        val start = if (lowerLine != null) lowerLine.indexOf(marker) else line.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val from = start + marker.length
        val end = line.indexOf('"', from)
        return if (end > from) line.substring(from, end) else null
    }

    // Só passa pelo URLDecoder se tiver '%' ou '+' -- na imensa maioria das
    // URLs não tem, e o resultado seria idêntico (só gastaria tempo/memória).
    private fun decode(value: String): String =
        if (value.indexOf('%') < 0 && value.indexOf('+') < 0) value
        else runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /**
     * Exportação M3U do Xtream normalmente segue o padrão
     * `.../live|movie|series/usuario/senha/12345.ext` — o mesmo formato
     * que a API JSON usa. Extrai esse ID pra viabilizar EPG e sinopse
     * mesmo quando a lista chegou como M3U puro, não como catálogo
     * Xtream — que é o caminho mais comum na prática.
     */
    private fun streamIdFromUrl(url: String): String? {
        // Pega só o caminho da URL na mão (sem java.net.URI, que é lento e
        // era chamado uma vez por item -- dezenas de milhares de vezes).
        val schemeEnd = url.indexOf("://")
        val pathStart = url.indexOf('/', if (schemeEnd >= 0) schemeEnd + 3 else 0)
        if (pathStart < 0) return null
        var pathEnd = url.length
        val query = url.indexOf('?', pathStart)
        if (query in 0 until pathEnd) pathEnd = query
        val fragment = url.indexOf('#', pathStart)
        if (fragment in 0 until pathEnd) pathEnd = fragment
        val segments = url.substring(pathStart, pathEnd).trim('/').split('/')
        if (segments.size < 4) return null
        val kind = segments[segments.size - 4]
        if (kind != "live" && kind != "movie" && kind != "series") return null
        val id = segments.last().substringBeforeLast('.')
        return id.takeIf { it.isNotBlank() && it.all { ch -> ch.isDigit() } }
    }

    private val seriesWords = arrayOf("série", "series", "temporada", "season")
    private val movieWords = arrayOf("filme", "movie", "cinema", "netflix", "amazon prime", "ação", "suspense")

    private fun classify(name: String, group: String, kidsGroup: Boolean): M3uItem.Kind {
        // Só o nome da CATEGORIA decide Kids — um filme cujo nome
        // tenha "família" não pode arrastar uma categoria genérica
        // (tipo "STAR+" ou "TOP 10") pra dentro do Kids inteira.
        if (kidsGroup) return M3uItem.Kind.KIDS
        val value = "$name $group".lowercase()
        return when {
            seriesWords.any { value.contains(it) } -> M3uItem.Kind.SERIES
            movieWords.any { value.contains(it) } -> M3uItem.Kind.MOVIE
            else -> M3uItem.Kind.CHANNEL
        }
    }
}
