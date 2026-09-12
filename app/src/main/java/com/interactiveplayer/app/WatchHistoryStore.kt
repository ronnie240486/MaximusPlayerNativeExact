package com.interactiveplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Histórico do que foi aberto recentemente, para alimentar a faixa
 * "CONTINUE ASSISTINDO" da Home — equivalente ao
 * `frontend/src/state/watch-history.ts` do repositório Maximus.
 *
 * Segue o mesmo padrão do FavoriteStore: JSON simples em
 * SharedPreferences, sem banco nem dependência extra.
 */
object WatchHistoryStore {
    private const val PREFS = "maximus_native_watch_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 20

    data class Entry(
        val name: String,
        val group: String,
        val logo: String?,
        val url: String,
        val kind: M3uItem.Kind,
        val streamId: String? = null,
    )

    fun list(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (url.isBlank()) continue
                    add(
                        Entry(
                            name = item.optString("name").ifBlank { "Sem título" },
                            group = item.optString("group"),
                            logo = item.optString("logo").ifBlank { null },
                            url = url,
                            kind = runCatching {
                                M3uItem.Kind.valueOf(item.optString("kind"))
                            }.getOrDefault(M3uItem.Kind.MOVIE),
                            streamId = item.optStringOrNull("streamId"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Registra um item como "assistido agora". Se ele já estava no
     * histórico, sobe para o topo em vez de duplicar.
     */
    fun record(context: Context, item: M3uItem) {
        if (item.url.isBlank()) return
        val current = list(context).filterNot { it.url == item.url }
        val updated = buildList {
            add(
                Entry(
                    name = item.name,
                    group = item.group,
                    logo = item.logo,
                    url = item.url,
                    kind = item.kind,
                    streamId = item.streamId,
                )
            )
            addAll(current)
        }.take(MAX_ITEMS)
        save(context, updated)
    }

    fun clear(context: Context) = save(context, emptyList())

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("name", entry.name)
                    put("group", entry.group)
                    put("logo", entry.logo ?: "")
                    put("url", entry.url)
                    put("kind", entry.kind.name)
                    put("streamId", entry.streamId ?: "")
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, array.toString())
            .apply()
    }

    /** Converte de volta para M3uItem, que é o que a Home sabe abrir. */
    fun Entry.toItem(): M3uItem = M3uItem(name, group, logo, url, kind, streamId)
}
