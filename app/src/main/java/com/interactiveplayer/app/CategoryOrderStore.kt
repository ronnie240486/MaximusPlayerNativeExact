package com.interactiveplayer.app

import android.content.Context
import org.json.JSONArray

/**
 * Ordem customizada das categorias DENTRO de cada aba (Canais, Filmes,
 * Séries, Kids) — diferente do SidebarOrderStore, que é só a barra
 * lateral da Home. Uma chave por seção, porque cada aba tem seu
 * próprio conjunto de categorias.
 */
object CategoryOrderStore {
    private const val PREFS = "maximus_category_order"

    fun load(context: Context, section: String, currentGroups: List<String>): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(section, null)
            ?: return currentGroups
        val saved = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }
        }.getOrDefault(emptyList())
        if (saved.isEmpty()) return currentGroups
        // Categorias que a pessoa reordenou, na ordem salva, seguidas
        // de qualquer categoria nova que o painel tenha adicionado
        // depois e ainda não tinha sido vista (senão ela some da lista).
        val known = saved.filter { it in currentGroups }
        val missing = currentGroups.filterNot { it in known }
        return known + missing
    }

    fun save(context: Context, section: String, order: List<String>) {
        val array = JSONArray()
        order.forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(section, array.toString()).apply()
    }
}
