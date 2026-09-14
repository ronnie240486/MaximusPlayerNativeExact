package com.interactiveplayer.app

/**
 * Detecta categorias de conteúdo adulto pelo nome do grupo — usado
 * pra jogar essas categorias pro fim da lista e exigir PIN antes de
 * abrir qualquer coisa nelas.
 */
object AdultContent {
    private val keywords = listOf(
        "adulto", "adultos", "+18", "18+", "xxx", "porn", "hot", "sensual", "erotic", "erótico", "erotico",
    )

    fun isAdultGroup(group: String): Boolean {
        val value = group.lowercase()
        return keywords.any { value.contains(it) }
    }
}
