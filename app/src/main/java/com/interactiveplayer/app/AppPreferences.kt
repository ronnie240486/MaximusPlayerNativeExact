package com.interactiveplayer.app

import android.content.Context

/**
 * Preferências simples do app, equivalentes ao que `settings.tsx`
 * guarda em AsyncStorage: próximo episódio automático, áudio de
 * boas-vindas e o PIN do controle parental.
 */
object AppPreferences {
    private const val PREFS = "maximus_native_preferences"
    private const val KEY_AUTOPLAY = "autoplay_next"
    private const val KEY_WELCOME_AUDIO = "welcome_audio"
    private const val KEY_PARENTAL_PIN = "parental_pin"

    fun autoplayNext(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOPLAY, true)

    fun setAutoplayNext(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTOPLAY, value).apply()
    }

    fun welcomeAudio(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WELCOME_AUDIO, true)

    fun setWelcomeAudio(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WELCOME_AUDIO, value).apply()
    }

    /** Null enquanto o controle parental estiver desativado. */
    fun parentalPin(context: Context): String? = prefs(context).getString(KEY_PARENTAL_PIN, null)

    fun isParentalLockOn(context: Context): Boolean = parentalPin(context) != null

    fun setParentalPin(context: Context, pin: String?) {
        prefs(context).edit().putString(KEY_PARENTAL_PIN, pin).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
