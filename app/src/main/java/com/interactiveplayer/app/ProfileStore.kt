package com.interactiveplayer.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NativeProfile(val name: String, val avatar: String, val kids: Boolean)

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_profiles", Context.MODE_PRIVATE)

    fun all(): List<NativeProfile> {
        val raw = prefs.getString("profiles", "[]") ?: "[]"
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map {
                val item = json.getJSONObject(it)
                NativeProfile(item.optString("name", "Perfil"), item.optString("avatar", "avatar-1.jpg"), item.optBoolean("kids", false))
            }
        }.getOrDefault(emptyList())
    }

    fun save(profile: NativeProfile) {
        val next = all().filterNot { it.name.equals(profile.name, ignoreCase = true) } + profile
        val json = JSONArray()
        next.forEach {
            json.put(JSONObject().apply {
                put("name", it.name)
                put("avatar", it.avatar)
                put("kids", it.kids)
            })
        }
        prefs.edit().putString("profiles", json.toString()).apply()
    }
}
