package com.interactiveplayer.app

import org.json.JSONObject

/**
 * `JSONObject.optString(key)` do org.json tem uma pegadinha clássica:
 * quando o valor no JSON é `null` de verdade (não ausente, `null`
 * mesmo — `{"plot": null}`), ele devolve a STRING LITERAL "null" em vez
 * de string vazia ou Kotlin null. Isso passava direto por qualquer
 * `.ifBlank { null }`, porque "null" não é uma string vazia — e
 * aparecia escrito "null" na tela, palavra por palavra, sempre que o
 * painel Xtream não tinha aquele campo cadastrado.
 *
 * Esta função é o único lugar que deveria ler string de JSON vindo do
 * Xtream/TMDB daqui pra frente.
 */
fun JSONObject.optStringOrNull(key: String): String? {
    if (isNull(key) || !has(key)) return null
    val value = optString(key)
    return value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}
