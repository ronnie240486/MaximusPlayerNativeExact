package com.interactiveplayer.app

import android.content.Context

/**
 * Qual perfil está ativo agora. Antes disso não existia — a pessoa
 * escolhia um perfil na tela de perfis, mas o app nunca guardava essa
 * escolha em lugar nenhum, então "perfil infantil" nunca filtrava nada
 * de verdade.
 */
object ActiveProfileStore {
    private const val PREFS = "maximus_active_profile"
    private const val KEY_NAME = "name"
    private const val KEY_KIDS = "kids"

    fun setActive(context: Context, profile: NativeProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, profile.name)
            .putBoolean(KEY_KIDS, profile.kids)
            .apply()
    }

    fun activeName(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    /** Sem perfil escolhido ainda, assume que NÃO é infantil (comportamento de antes, sem restrição extra). */
    fun isKidsActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_KIDS, false)
}
