package com.interactiveplayer.app

import android.content.Context

/**
 * Metadados de um filme/série, combinando Xtream (fonte primária) com
 * TMDB como reforço nos campos que o Xtream deixou vazios. Usado tanto
 * pelo hero da Home quanto pela tela de detalhes, pra não duplicar essa
 * lógica de combinação nos dois lugares.
 */
object ContentInfoClient {

    data class Info(
        val plot: String?,
        val genre: String?,
        val rating: String?,
        val year: String?,
        val backdrop: String?,
        val youtubeTrailer: String?,
    )

    /** Deve ser chamado fora da thread principal. */
    fun fetch(context: Context, item: M3uItem): Info? {
        val xtream = XtreamInfoClient.fetch(context, item)

        // Já veio tudo do painel — não precisa nem tentar o TMDB.
        val complete = xtream != null &&
            !xtream.plot.isNullOrBlank() &&
            !xtream.rating.isNullOrBlank() &&
            !xtream.year.isNullOrBlank()
        if (complete) {
            return Info(xtream!!.plot, xtream.genre, xtream.rating, xtream.year, xtream.backdrop, xtream.youtubeTrailer)
        }

        val tmdb = TmdbClient.search(item.name, isSeries = item.kind == M3uItem.Kind.SERIES)
        if (xtream == null && tmdb == null) return null

        return Info(
            plot = xtream?.plot?.takeIf { it.isNotBlank() } ?: tmdb?.plot,
            genre = xtream?.genre?.takeIf { it.isNotBlank() } ?: tmdb?.genre,
            rating = xtream?.rating?.takeIf { it.isNotBlank() } ?: tmdb?.rating,
            year = xtream?.year?.takeIf { it.isNotBlank() } ?: tmdb?.year,
            backdrop = xtream?.backdrop?.takeIf { it.isNotBlank() } ?: tmdb?.backdrop,
            // TMDB não devolve trailer do YouTube nessa busca simples —
            // só o Xtream tem esse campo.
            youtubeTrailer = xtream?.youtubeTrailer,
        )
    }
}
