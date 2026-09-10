package com.interactiveplayer.app

import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Tela de reprodução em tela cheia — filmes, séries, rádio (chega
 * sempre "url" + "title" via Intent). Usa PlayerControls para os
 * controles customizados: retroceder/avançar 10s, barra de progresso,
 * modo de tela, legendas e Picture-in-Picture — que o controlador
 * padrão do Media3 não tem.
 */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var controls: PlayerControls

    private var mediaUrl: String = ""
    private var mediaTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()

        mediaUrl = intent.getStringExtra("url").orEmpty()
        mediaTitle = intent.getStringExtra("title").orEmpty()

        setContentView(buildLayout())

        if (mediaUrl.isBlank()) {
            showError("Não recebi uma URL válida pra reproduzir.")
            return
        }
        startPlayback(mediaUrl)
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        playerView = PlayerView(this).apply { useController = false }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        controls = PlayerControls(
            activity = this,
            root = root,
            playerView = playerView,
            isLive = false,
            onBack = { finish() },
        )
        root.addView(controls.build(), FrameLayout.LayoutParams(-1, -1))

        progressBar = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
        }
        root.addView(progressBar, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))

        errorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        errorText = TextView(this).apply {
            setTextColor(Theme.danger)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        errorContainer.addView(errorText)
        errorContainer.addView(
            TextView(this).apply {
                setText("Tentar de novo")
                setTextColor(Theme.black)
                background = roundRect(Theme.accentCyan, Theme.RADIUS_SM)
                setPadding(dp(24), dp(12), dp(24), dp(12))
                isFocusable = true
                isClickable = true
                gravity = Gravity.CENTER
                setOnClickListener { startPlayback(mediaUrl) }
            },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) }
        )
        root.addView(errorContainer, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))

        return root
    }

    private fun startPlayback(url: String) {
        errorContainer.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                        Player.STATE_READY -> progressBar.visibility = View.GONE
                        Player.STATE_ENDED -> finish()
                        else -> {}
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    showError("Não foi possível reproduzir esse conteúdo agora. Confere sua internet ou tenta de novo em instantes.")
                }
            })
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exo.prepare()
            exo.playWhenReady = true
            controls.bind(exo, mediaTitle.ifBlank { "Reproduzindo" })
        }
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        errorText.setText(message)
        errorContainer.visibility = View.VISIBLE
        errorContainer.requestFocus()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Sair do app (Início do D-pad, gesto de home) enquanto assiste
        // entra em Picture-in-Picture sozinho, em vez de simplesmente
        // parar o vídeo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        controls.release()
        player?.release()
        player = null
        super.onDestroy()
    }
}
