package com.interactiveplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favoritos de rádio, equivalente à categoria `FAVORITES_KEY` de
 * `frontend/app/radios.tsx`. Separado do FavoriteStore de filmes/séries
 * porque a chave de identidade é diferente (station.id da RadioBrowser).
 */
object RadioFavoriteStore {
    private const val PREFS = "maximus_native_radio_favorites"
    private const val KEY = "stations"

    fun list(context: Context): List<RadioBrowserClient.Station> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        RadioBrowserClient.Station(
                            id = item.optString("id"),
                            name = item.optString("name"),
                            resolvedUrl = item.optString("url"),
                            favicon = item.optString("favicon").ifBlank { null },
                            country = item.optString("country").ifBlank { null },
                            tags = item.optString("tags").ifBlank { null },
                            bitrate = item.optInt("bitrate"),
                            lastCheckOk = item.optInt("lastCheckOk", 1),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun contains(context: Context, station: RadioBrowserClient.Station): Boolean =
        list(context).any { it.id == station.id }

    /** Devolve true se ficou favoritada, false se foi removida. */
    fun toggle(context: Context, station: RadioBrowserClient.Station): Boolean {
        val current = list(context).toMutableList()
        val index = current.indexOfFirst { it.id == station.id }
        val nowFavorite = if (index >= 0) {
            current.removeAt(index)
            false
        } else {
            current.add(station)
            true
        }
        val array = JSONArray()
        current.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("url", item.resolvedUrl)
                    put("favicon", item.favicon ?: "")
                    put("country", item.country ?: "")
                    put("tags", item.tags ?: "")
                    put("bitrate", item.bitrate)
                    put("lastCheckOk", item.lastCheckOk)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
        return nowFavorite
    }
}
