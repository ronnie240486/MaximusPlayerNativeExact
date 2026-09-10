package com.interactiveplayer.app

import android.content.Context

/**
 * Server/usuário/senha do Xtream, guardados pelo `CatalogRepository`
 * assim que ele consegue extrair da URL da playlist ativa — que é a
 * fonte confiável dessas credenciais.
 *
 * Antes, cada cliente (XtreamInfoClient, EpgClient) tentava adivinhar
 * essas três coisas de volta a partir da URL de CADA item individual —
 * o que falhava sempre que o painel preenchia `direct_source` com uma
 * URL num formato diferente (comum: CDN direta, sem usuário/senha no
 * caminho). Com as credenciais salvas uma vez só, esses clientes só
 * precisam do `streamId`, que agora vem guardado direto no M3uItem.
 */
object XtreamCredentials {
    private const val PREFS = "maximus_native_xtream_credentials"

    data class Credentials(val server: String, val username: String, val password: String)

    fun save(context: Context, server: String, username: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("server", server)
            .putString("username", username)
            .putString("password", password)
            .apply()
    }

    fun load(context: Context): Credentials? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = prefs.getString("server", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        return Credentials(server, username, password)
    }
}
