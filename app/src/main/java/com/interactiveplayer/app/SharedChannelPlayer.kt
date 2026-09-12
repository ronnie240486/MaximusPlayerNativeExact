package com.interactiveplayer.app

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Um ExoPlayer só para canal ao vivo, compartilhado entre
 * `ChannelDetailsActivity` (caixa pequena) e `PlayerActivity` (tela
 * cheia) — pedido explícito: "um player só, só w abre grande e
 * pequeno".
 *
 * Sem isso, cada tela criava o próprio ExoPlayer: ir da caixinha pra
 * tela cheia (ou voltar) recriava o player do zero, com re-buffer e
 * reconexão ao vivo toda vez. Agora as duas telas só ANEXAM/DESANEXAM
 * a `PlayerView` a este player único — ele continua rodando por trás
 * enquanto a pessoa alterna entre as duas.
 *
 * Só serve para canal ao vivo. Filme/série/rádio continuam com o
 * ExoPlayer próprio de cada tela — não há conceito de "mini" pra eles.
 */
object SharedChannelPlayer {
    var player: ExoPlayer? = null
        private set
    var currentUrl: String? = null
        private set

    /** Devolve o player existente se já for o mesmo canal; senão troca. */
    fun playerFor(context: Context, url: String): ExoPlayer {
        val existing = player
        if (existing != null && currentUrl == url) return existing

        existing?.release()
        val created = ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
        player = created
        currentUrl = url
        return created
    }

    /** Chamado quando a pessoa sai do canal de vez (não ao trocar de mini pra tela cheia). */
    fun release() {
        player?.release()
        player = null
        currentUrl = null
    }
}
