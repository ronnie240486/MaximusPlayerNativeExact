package com.interactiveplayer.app

import android.content.Context
import org.json.JSONArray

/** Ordem customizada dos itens da barra lateral da Home — arrastada em Ajustes. */
object SidebarOrderStore {
    private const val PREFS = "maximus_sidebar_order"
    private const val KEY = "order"

    fun load(context: Context, defaultOrder: List<String>): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return defaultOrder
        val saved = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }
        }.getOrDefault(emptyList())
        if (saved.isEmpty()) return defaultOrder
        // Reordena os itens que a pessoa customizou, mas inclui no fim
        // qualquer item novo que tenha entrado no app depois (ex.: uma
        // atualização que adicionou uma aba nova) — sem isso, esse item
        // novo simplesmente sumiria da barra pra quem já tinha uma
        // ordem salva.
        val known = saved.filter { it in defaultOrder }
        val missing = defaultOrder.filterNot { it in known }
        return known + missing
    }

    fun save(context: Context, order: List<String>) {
        val array = JSONArray()
        order.forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
