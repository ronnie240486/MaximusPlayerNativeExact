package com.interactiveplayer.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MacPanelClient {
    private const val PANEL_BASE = "https://renciaapp.manus.space/api/v5"
    private const val PANEL_ROOT = "https://renciaapp.manus.space"
    private const val TEST_REGISTER_FALLBACK = "https://nuvixtv.sigmab.pro/api/chatbot/Yen129WPEa/XYgD9JWr6V"

    data class CheckResult(
        val session: MacSessionStore.Session,
        val raw: String,
    )

    fun checkMac(mac: String): CheckResult {
        val endpoint = "$PANEL_BASE/check_mac.php?mac=${encode(mac)}"
        return runCatching {
            val jsonText = get(endpoint)
            val json = JSONObject(jsonText)
            CheckResult(normalize(json, mac), jsonText)
        }.getOrElse {
            CheckResult(
                MacSessionStore.Session(false, false, mac, null, null, emptyList(), null, null, null, null, null, null, "Falha de conexão."),
                "",
            )
        }
    }

    fun registerTestDevice(mac: String): Pair<Boolean, String> {
        val registerUrl = runCatching {
            val guim = get("$PANEL_ROOT/api/guim.php?mac=${encode(mac)}")
            JSONObject(guim).optString("gpcpro_server_url").ifBlank { TEST_REGISTER_FALLBACK }
        }.getOrDefault(TEST_REGISTER_FALLBACK)
        return runCatching {
            val connection = (URL("$registerUrl?mac=${encode(mac)}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            connection.outputStream.use { it.write(JSONObject().put("mac", mac).toString().toByteArray()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            (code in 200..299) to body
        }.getOrElse { false to it.message.orEmpty() }
    }

    fun fetchExtras(mac: String): Map<String, String> = runCatching {
        val json = JSONObject(get("$PANEL_ROOT/api/guim.php?mac=${encode(mac)}"))
        buildMap {
            listOf("impactPhrase", "lockTitle", "lockMessage", "lockButtonText", "lockButtonUrl", "websiteUrl", "contactInfo", "resellerEmail", "legalNotice").forEach { key ->
                json.optString(key).ifBlank { null }?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())

    private fun normalize(json: JSONObject, mac: String): MacSessionStore.Session {
        val registered = json.optBoolean("mac_registered") || json.optBoolean("registered") || json.optInt("registered") == 1 || json.optBoolean("found")
        val allowed = json.optBoolean("allowed", registered) && json.optBoolean("success", true)
        val playlists = ArrayList<MacSessionStore.Playlist>()
        val array = json.optJSONArray("playlists")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").ifBlank { item.optString("playlist_url") }
                if (url.isNotBlank()) playlists += MacSessionStore.Playlist(item.optString("name").ifBlank { item.optString("playlist_name").ifBlank { "Playlist" } }, url, item.optString("type").ifBlank { null })
            }
        }
        val single = json.optString("urlM3u8")
        if (playlists.isEmpty() && single.isNotBlank()) playlists += MacSessionStore.Playlist(json.optString("nomeServer").ifBlank { "Playlist" }, single, "m3u_plus")
        return MacSessionStore.Session(
            authorized = registered && allowed && playlists.isNotEmpty(),
            registered = registered,
            mac = json.optString("mac").ifBlank { mac },
            status = json.optString("status").ifBlank { null },
            expireDate = json.optString("dataExpiracao").ifBlank { json.optString("expire_date").ifBlank { null } },
            playlists = playlists,
            logoUrl = json.optString("logo_url").ifBlank { null },
            bgUrl = json.optString("bg_url").ifBlank { null },
            bannerUrl = json.optString("banner_url").ifBlank { null },
            appName = json.optString("app_name").ifBlank { json.optString("app").ifBlank { null } },
            whatsappUrl = json.optString("whatsapp_url").ifBlank { null },
            resellerWhatsapp = json.optString("reseller_whatsapp").ifBlank { null },
            message = json.optString("message").ifBlank { json.optString("mensagem").ifBlank { json.optString("error").ifBlank { null } } },
        )
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("HTTP $code")
        return body
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) ExoPlayerLib/2.19.1"
}
