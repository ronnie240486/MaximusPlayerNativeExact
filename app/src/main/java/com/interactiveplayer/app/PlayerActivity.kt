package com.interactiveplayer.app

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        if (url.isBlank()) {
            setContentView(TextView(this).apply {
                text = "URL de reprodução inválida"
                textSize = 22f
                gravity = Gravity.CENTER
            })
            return
        }
        val playerView = PlayerView(this)
        setContentView(FrameLayout(this).apply {
            addView(playerView, FrameLayout.LayoutParams(-1, -1))
        })
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }
}
