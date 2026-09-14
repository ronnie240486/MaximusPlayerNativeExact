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
    fun parse(content: String): List<M3uItem> {
        val lines = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val result = ArrayList<M3uItem>()
        var info: String? = null
        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> info = line
                line.startsWith("#") -> Unit
                info != null -> {
                    val header = info!!
                    val name = attribute(header, "tvg-name") ?: header.substringAfterLast(",", "Sem título").trim()
                    val group = attribute(header, "group-title") ?: "Sem categoria"
                    val logo = attribute(header, "tvg-logo")
                    val url = decode(line)
                    result += M3uItem(name, group, logo, url, classify(name, group), streamIdFromUrl(url))
                    info = null
                }
            }
        }
        return result
    }

    private fun attribute(line: String, key: String): String? {
        val marker = "$key=\""
        val start = line.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val from = start + marker.length
        val end = line.indexOf('"', from)
        return if (end > from) line.substring(from, end) else null
    }

    private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    /**
     * Exportação M3U do Xtream normalmente segue o padrão
     * `.../live|movie|series/usuario/senha/12345.ext` — o mesmo formato
     * que a API JSON usa. Extrai esse ID pra viabilizar EPG e sinopse
     * mesmo quando a lista chegou como M3U puro, não como catálogo
     * Xtream — que é o caminho mais comum na prática.
     */
    private fun streamIdFromUrl(url: String): String? = runCatching {
        val path = java.net.URI(url).path ?: return@runCatching null
        val segments = path.trim('/').split('/')
        if (segments.size < 4) return@runCatching null
        if (segments[segments.size - 4] !in listOf("live", "movie", "series")) return@runCatching null
        val id = segments.last().substringBeforeLast('.')
        id.takeIf { it.isNotBlank() && it.all { ch -> ch.isDigit() } }
    }.getOrNull()

    private fun classify(name: String, group: String): M3uItem.Kind {
        val value = "$name $group".lowercase()
        return when {
            // Só o nome da CATEGORIA decide Kids — um filme cujo nome
            // tenha "família" não pode arrastar uma categoria genérica
            // (tipo "STAR+" ou "TOP 10") pra dentro do Kids inteira.
            KidsContent.matches(group) && !AdultContent.isAdultGroup(group) -> M3uItem.Kind.KIDS
            listOf("série", "series", "temporada", "season").any(value::contains) -> M3uItem.Kind.SERIES
            listOf("filme", "movie", "cinema", "netflix", "amazon prime", "ação", "suspense").any(value::contains) -> M3uItem.Kind.MOVIE
            else -> M3uItem.Kind.CHANNEL
        }
    }
}
