package com.maximus.nativeexact

import android.content.Context

/** Preferências da sessão IPTV da instalação atual. */
object AppPreferences {
    private const val PREFS = "maximus_native_preferences"
    private const val KEY_SERVER = "server"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    data class Credentials(
        val server: String,
        val username: String,
        val password: String,
    )

    fun credentials(context: Context): Credentials? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString(KEY_SERVER, null)?.trim().orEmpty()
        val username = prefs.getString(KEY_USERNAME, null)?.trim().orEmpty()
        val password = prefs.getString(KEY_PASSWORD, null).orEmpty()
        return if (server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            Credentials(normalizeServer(server), username, password)
        } else {
            null
        }
    }

    fun save(context: Context, server: String, username: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER, normalizeServer(server))
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun normalizeServer(raw: String): String {
        var value = raw.trim().removeSuffix("/")
        val marker = value.indexOf("/get.php", ignoreCase = true)
        if (marker >= 0) value = value.substring(0, marker)
        val query = value.indexOf('?')
        if (query >= 0) value = value.substring(0, query)
        return value.removeSuffix("/")
    }
}
