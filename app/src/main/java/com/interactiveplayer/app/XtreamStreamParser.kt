package com.interactiveplayer.app

import android.util.JsonReader
import android.util.JsonToken

/**
 * Lê o catálogo Xtream (live / filmes / séries) direto do stream, item por item,
 * SEM montar o JSON inteiro na memória. O jeito antigo baixava cada resposta
 * como uma String gigante (e depois um JSONArray com milhares de JSONObject),
 * três ao mesmo tempo -- numa TV box com pouca RAM isso estourava a memória.
 *
 * Quem separa o que é canal, filme e série aqui é o PAINEL (cada lista vem de
 * um endpoint diferente), então não há adivinhação por palavras no nome.
 */
object XtreamStreamParser {
    private fun JsonReader.str(): String = when (peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        JsonToken.NULL -> { nextNull(); "" }
        else -> { skipValue(); "" }
    }

    fun categories(reader: JsonReader): Map<String, String> {
        val map = HashMap<String, String>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            var id = ""
            var name = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "category_id" -> id = reader.str()
                    "category_name" -> name = reader.str()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (id.isNotBlank() && name.isNotBlank()) map[id] = name
        }
        reader.endArray()
        return map
    }

    // Decide "esse grupo é adulto / é Kids?" uma vez por grupo, não uma vez por item.
    private class GroupFlags(val adult: Boolean, val kids: Boolean)

    private fun flagsFor(cache: HashMap<String, GroupFlags>, group: String): GroupFlags =
        cache.getOrPut(group) {
            val adult = AdultContent.isAdultGroup(group)
            GroupFlags(adult, !adult && KidsContent.matches(group))
        }

    fun live(
        reader: JsonReader, categories: Map<String, String>, server: String, username: String, password: String,
        out: MutableList<M3uItem>, onItem: () -> Unit,
    ) {
        val flags = HashMap<String, GroupFlags>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            var name = ""; var categoryId = ""; var streamId = ""; var icon = ""; var direct = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = reader.str()
                    "category_id" -> categoryId = reader.str()
                    "stream_id" -> streamId = reader.str()
                    "stream_icon" -> icon = reader.str()
                    "direct_source" -> direct = reader.str()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            onItem()
            if (streamId.isBlank()) continue
            val title = name.ifBlank { "Canal" }
            val group = categories[categoryId] ?: "Canais"
            if (flagsFor(flags, group).adult || AdultContent.isAdultName(title)) continue
            val url = direct.ifBlank { "$server/live/$username/$password/$streamId.ts" }
            out += M3uItem(title, group, icon.ifBlank { null }, url, M3uItem.Kind.CHANNEL, streamId)
        }
        reader.endArray()
    }

    fun movies(
        reader: JsonReader, categories: Map<String, String>, server: String, username: String, password: String,
        out: MutableList<M3uItem>, onItem: () -> Unit,
    ) {
        val flags = HashMap<String, GroupFlags>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            var name = ""; var categoryId = ""; var streamId = ""; var icon = ""; var ext = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = reader.str()
                    "category_id" -> categoryId = reader.str()
                    "stream_id" -> streamId = reader.str()
                    "stream_icon" -> icon = reader.str()
                    "container_extension" -> ext = reader.str()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            onItem()
            if (streamId.isBlank()) continue
            val title = name.ifBlank { "Filme" }
            val group = categories[categoryId] ?: "Filmes"
            val f = flagsFor(flags, group)
            if (f.adult || AdultContent.isAdultName(title)) continue
            val kind = if (f.kids) M3uItem.Kind.KIDS else M3uItem.Kind.MOVIE
            out += M3uItem(title, group, icon.ifBlank { null }, "$server/movie/$username/$password/$streamId.${ext.ifBlank { "mp4" }}", kind, streamId)
        }
        reader.endArray()
    }

    fun series(
        reader: JsonReader, categories: Map<String, String>, server: String, username: String, password: String,
        out: MutableList<M3uItem>, onItem: () -> Unit,
    ) {
        val flags = HashMap<String, GroupFlags>()
        reader.beginArray()
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT) { reader.skipValue(); continue }
            var name = ""; var categoryId = ""; var seriesId = ""; var cover = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = reader.str()
                    "category_id" -> categoryId = reader.str()
                    "series_id" -> seriesId = reader.str()
                    "cover" -> cover = reader.str()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            onItem()
            if (seriesId.isBlank()) continue
            val title = name.ifBlank { "Série" }
            val group = categories[categoryId] ?: "Séries"
            val f = flagsFor(flags, group)
            if (f.adult || AdultContent.isAdultName(title)) continue
            val kind = if (f.kids) M3uItem.Kind.KIDS else M3uItem.Kind.SERIES
            out += M3uItem(title, group, cover.ifBlank { null }, "$server/series/$username/$password/$seriesId.mp4", kind, seriesId)
        }
        reader.endArray()
    }
}
