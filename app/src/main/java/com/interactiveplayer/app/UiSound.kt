package com.interactiveplayer.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Efeito sonoro de clique nos itens principais (sidebar, canais,
 * pôsteres) — o asset `swoosh.mp3` já existe em `original_media/` desde
 * o começo (usado hoje só na tela de boas-vindas, junto da voz), mas
 * nunca tinha sido aproveitado como som de toque no resto do app.
 *
 * Usa SoundPool (não MediaPlayer): feito pra efeitos curtos e não
 * interfere no player de vídeo/rádio, que usa ExoPlayer separado.
 */
object UiSound {
    private var pool: SoundPool? = null
    private var soundId: Int = 0
    private var loaded = false

    fun init(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val newPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
        newPool.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
        runCatching {
            context.assets.openFd("original_media/swoosh.mp3").use { descriptor ->
                soundId = newPool.load(descriptor, 1)
            }
        }
        pool = newPool
    }

    fun click() {
        if (loaded) pool?.play(soundId, 0.5f, 0.5f, 0, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
        loaded = false
    }
}
