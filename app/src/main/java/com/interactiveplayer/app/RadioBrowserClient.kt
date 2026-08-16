package com.interactiveplayer.app

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Integração pública e sem chave com a Radio Browser API. */
object RadioBrowserClient {
    private val mirrors = listOf(
        "https://all.api.radio-browser.info",
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
    )

    data class Category(
        val key: String,
        val label: String,
        val tags: List<String> = emptyList(),
        val countryCode: String? = null,
    )

    data class Station(
        val id: String,
        val name: String,
        val resolvedUrl: String,
        val favicon: String?,
        val country: String?,
        val tags: String?,
        val bitrate: Int,
        val lastCheckOk: Int,
    )

    val categories = listOf(
        Category("popular", "Populares"),
        Category("nacionais", "Nacionais", countryCode = "BR"),
        Category("rock", "Rock", listOf("rock", "classic rock", "rock nacional", "rock brasileiro")),
        Category("hardrock", "Hard Rock", listOf("hard rock", "hardrock", "heavy metal", "metal")),
        Category("pop", "Pop", listOf("pop")),
        Category("sertanejo", "Sertanejo", listOf("sertanejo")),
        Category("gospel", "Gospel", listOf("gospel", "christian", "crista", "louvor", "igreja", "evangelica"), "BR"),
        Category("esportes", "Esportes", listOf("sports", "sport", "esporte", "esportes", "futebol"), "BR"),
        Category("classicos", "Clássicos", listOf("oldies", "classic hits")),
        Category("internacionais", "Internacionais", listOf("top 40", "english")),
    )

    suspend fun fetchByCategory(category: Category, limit: Int = 100): List<Station> {
        if (category.tags.isEmpty()) {
            val country = category.countryCode?.let { "countrycode=${encode(it)}&" }.orEmpty()
            return get("/json/stations/search?${country}limit=$limit&hidebroken=true&lastcheckok=true&order=clickcount&reverse=true")
        }
        val withCountry = if (category.countryCode != null) {
            category.tags.flatMap { tag ->
                get("/json/stations/search?tag=${encode(tag)}&countrycode=${encode(category.countryCode)}&limit=$limit&hidebroken=true&lastcheckok=true&order=clickcount&reverse=true")
            }
        } else {
            category.tags.flatMap { tag ->
                get("/json/stations/bytag/${encode(tag)}?limit=$limit&hidebroken=true&lastcheckok=true&order=clickcount&reverse=true")
            }
        }
        val first = dedupe(withCountry)
        if (category.countryCode != null && first.size < 15) {
            return dedupe(first + category.tags.flatMap { tag ->
                get("/json/stations/bytag/${encode(tag)}?limit=$limit&hidebroken=true&lastcheckok=true&order=clickcount&reverse=true")
            })
        }
        return first
    }

    suspend fun search(query: String, limit: Int = 60): List<Station> =
        dedupe(get("/json/stations/search?name=${encode(query)}&limit=$limit&hidebroken=true&lastcheckok=true&order=clickcount&reverse=true"))

    private suspend fun get(path: String): List<Station> {
        for (mirror in mirrors) {
            val result = runCatching {
                val connection = (URL(mirror + path).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MaximusPlayer/1.0 Android")
                    setRequestProperty("Accept", "application/json")
                }
                val body = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
                connection.disconnect()
                parse(body)
            }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private fun parse(body: String): List<Station> {
        if (body.isBlank()) return emptyList()
        val array = JSONArray(body)
        val result = ArrayList<Station>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val resolved = item.optString("url_resolved").ifBlank { item.optString("url") }
            if (resolved.isBlank() || item.optInt("lastcheckok", 1) == 0) continue
            result += Station(
                id = item.optString("stationuuid").ifBlank { item.optString("id") },
                name = item.optString("name").trim().ifBlank { "Rádio sem nome" },
                resolvedUrl = resolved,
                favicon = item.optString("favicon").ifBlank { null },
                country = item.optString("country").ifBlank { null },
                tags = item.optString("tags").ifBlank { null },
                bitrate = item.optInt("bitrate", 0),
                lastCheckOk = item.optInt("lastcheckok", 1),
            )
        }
        return result
    }

    private fun dedupe(items: List<Station>): List<Station> {
        val seen = HashSet<String>()
        return items.filter { seen.add(it.name.trim().lowercase()) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
