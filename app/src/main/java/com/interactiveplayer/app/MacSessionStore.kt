package com.interactiveplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object MacSessionStore {
    private const val PREFS = "maximus_native_mac_session"
    private const val KEY_SESSION = "mac_status_v1"
    private const val KEY_ACTIVE = "active_playlist_index_v1"

    data class Playlist(val name: String, val url: String, val type: String?)

    data class Session(
        val authorized: Boolean,
        val registered: Boolean,
        val mac: String,
        val status: String?,
        val expireDate: String?,
        val playlists: List<Playlist>,
        val logoUrl: String?,
        val bgUrl: String?,
        val bannerUrl: String?,
        val appName: String?,
        val whatsappUrl: String?,
        val resellerWhatsapp: String?,
        val message: String?,
    ) {
        fun activePlaylist(context: Context): Playlist? {
            val index = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACTIVE, 0)
            return playlists.getOrNull(index) ?: playlists.firstOrNull()
        }
    }

    fun load(context: Context): Session? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SESSION, null) ?: return null
        return runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun save(context: Context, session: Session) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SESSION, encode(session).toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun setActivePlaylistIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_ACTIVE, index.coerceAtLeast(0)).apply()
    }

    fun activePlaylistIndex(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACTIVE, 0)

    private fun encode(session: Session) = JSONObject().apply {
        put("authorized", session.authorized)
        put("registered", session.registered)
        put("mac", session.mac)
        put("status", session.status ?: "")
        put("expire_date", session.expireDate ?: "")
        put("playlists", JSONArray().apply { session.playlists.forEach { put(JSONObject().apply { put("name", it.name); put("url", it.url); put("type", it.type ?: "") }) } })
        put("logo_url", session.logoUrl ?: "")
        put("bg_url", session.bgUrl ?: "")
        put("banner_url", session.bannerUrl ?: "")
        put("app_name", session.appName ?: "")
        put("whatsapp_url", session.whatsappUrl ?: "")
        put("reseller_whatsapp", session.resellerWhatsapp ?: "")
        put("message", session.message ?: "")
    }

    private fun decode(json: JSONObject): Session {
        val list = json.optJSONArray("playlists") ?: JSONArray()
        val playlists = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (url.isNotBlank()) add(Playlist(item.optString("name").ifBlank { "Playlist" }, url, item.optString("type").ifBlank { null }))
            }
        }
        return Session(
            authorized = json.optBoolean("authorized"),
            registered = json.optBoolean("registered"),
            mac = json.optString("mac"),
            status = json.optString("status").ifBlank { null },
            expireDate = json.optString("expire_date").ifBlank { null },
            playlists = playlists,
            logoUrl = json.optString("logo_url").ifBlank { null },
            bgUrl = json.optString("bg_url").ifBlank { null },
            bannerUrl = json.optString("banner_url").ifBlank { null },
            appName = json.optString("app_name").ifBlank { null },
            whatsappUrl = json.optString("whatsapp_url").ifBlank { null },
            resellerWhatsapp = json.optString("reseller_whatsapp").ifBlank { null },
            message = json.optString("message").ifBlank { null },
        )
    }
}
