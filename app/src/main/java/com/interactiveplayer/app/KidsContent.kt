package com.interactiveplayer.app

/**
 * Detecta conteúdo infantil pelo nome (canal, filme, série ou grupo).
 * Antes a lista de palavras-chave era bem curta (kids/infantil/
 * desenho/cartoon/children) e deixava passar batido um monte de
 * categoria de desenho/família que os painéis nomeiam de outros
 * jeitos — por isso o Kids aparecia com muito pouco conteúdo.
 */
object KidsContent {
    private val keywords = listOf(
        "kids", "infantil", "infantis", "desenho", "desenhos", "cartoon", "cartoons",
        "children", "criança", "crianças", "baby", "bebê", "bebe", "junior", "júnior",
        "animação", "animacao", "animações", "animacoes", "animado", "animados", "família", "familia", "toon",
        "gloob", "discovery kids", "nick jr", "nickelodeon", "cartoon network",
        "disney junior", "boomerang", "tooncast", "anime", "animes", "crunchyroll",
        "anos 90", "natal",
    )

    fun matches(vararg parts: String): Boolean {
        val value = parts.joinToString(" ").lowercase()
        return keywords.any(value::contains)
    }
}
