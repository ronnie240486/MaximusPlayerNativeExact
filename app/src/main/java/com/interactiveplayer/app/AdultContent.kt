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

    // Palavras mais restritas para o NOME do título/canal (o grupo já é checado
    // acima com a lista maior): evita esconder um filme normal só porque o nome
    // tem "hot" ou "sensual", mas pega "[+18]", "XXX", "Adulto" etc.
    private val nameKeywords = listOf("xxx", "porn", "+18", "18+", "adulto", "erotic", "erótic")

    fun isAdultName(name: String): Boolean {
        val value = name.lowercase()
        return nameKeywords.any { value.contains(it) }
    }

    /** Conteúdo adulto pelo grupo OU pelo nome: o app inteiro esconde isso. */
    fun isAdult(name: String, group: String): Boolean = isAdultGroup(group) || isAdultName(name)
}
