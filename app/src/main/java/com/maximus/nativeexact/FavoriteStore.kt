package com.maximus.nativeexact

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object FavoriteStore {
    private const val PREFS = "maximus_native_favorites"
    private const val KEY_ITEMS = "items"

    data class Favorite(
        val id: String,
        val name: String,
        val group: String,
        val logo: String?,
        val url: String,
        val kind: M3uItem.Kind,
    )

    fun list(context: Context): List<Favorite> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url")
                    if (url.isBlank()) continue
                    add(Favorite(
                        id = item.optString("id").ifBlank { stableId(url) },
                        name = item.optString("name").ifBlank { "Sem título" },
                        group = item.optString("group"),
                        logo = item.optString("logo").ifBlank { null },
                        url = url,
                        kind = runCatching { M3uItem.Kind.valueOf(item.optString("kind")) }.getOrDefault(M3uItem.Kind.CHANNEL),
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun contains(context: Context, item: M3uItem): Boolean = list(context).any { it.id == stableId(item.url) }

    fun toggle(context: Context, item: M3uItem): Boolean {
        val current = list(context).toMutableList()
        val id = stableId(item.url)
        val index = current.indexOfFirst { it.id == id }
        val nowFavorite = if (index >= 0) {
            current.removeAt(index)
            false
        } else {
            current.add(Favorite(id, item.name, item.group, item.logo, item.url, item.kind))
            true
        }
        persist(context, current)
        return nowFavorite
    }

    fun remove(context: Context, favorite: Favorite) {
        persist(context, list(context).filterNot { it.id == favorite.id })
    }

    private fun persist(context: Context, items: List<Favorite>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("group", item.group)
                put("logo", item.logo ?: "")
                put("url", item.url)
                put("kind", item.kind.name)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun stableId(url: String): String = url.trim().lowercase().hashCode().toString()
}
