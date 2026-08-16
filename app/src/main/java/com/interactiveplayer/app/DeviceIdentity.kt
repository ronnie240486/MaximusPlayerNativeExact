package com.interactiveplayer.app

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object DeviceIdentity {
    private const val PREFS = "maximus_native_device"
    private const val KEY_MAC = "device_mac_id_v1"

    fun getMac(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val androidId = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
        if (!androidId.isNullOrBlank()) {
            return shaToMac(androidId).also { prefs.edit().putString(KEY_MAC, it).apply() }
        }
        val saved = prefs.getString(KEY_MAC, null)
        if (!saved.isNullOrBlank() && MAC_REGEX.matches(saved)) return saved
        return shaToMac(UUID.randomUUID().toString()).also { prefs.edit().putString(KEY_MAC, it).apply() }
    }

    private fun shaToMac(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.copyOfRange(0, 6).joinToString(":") { "%02X".format(Locale.US, it) }
    }

    private val MAC_REGEX = Regex("^[0-9A-F]{2}(:[0-9A-F]{2}){5}$")
}
