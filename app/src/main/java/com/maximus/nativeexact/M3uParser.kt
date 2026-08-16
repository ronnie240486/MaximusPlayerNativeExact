package com.maximus.nativeexact

import java.net.URLDecoder

/** Modelo mínimo e testável para uma entrada IPTV da playlist M3U. */
data class M3uItem(
    val name: String,
    val group: String,
    val logo: String?,
    val url: String,
    val kind: Kind,
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
                    result += M3uItem(name, group, logo, decode(line), classify(name, group))
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

    private fun classify(name: String, group: String): M3uItem.Kind {
        val value = "$name $group".lowercase()
        return when {
            listOf("kids", "infantil", "desenho", "cartoon", "children").any(value::contains) -> M3uItem.Kind.KIDS
            listOf("série", "series", "temporada", "season").any(value::contains) -> M3uItem.Kind.SERIES
            listOf("filme", "movie", "cinema", "netflix", "amazon prime", "ação", "suspense").any(value::contains) -> M3uItem.Kind.MOVIE
            else -> M3uItem.Kind.CHANNEL
        }
    }
}
