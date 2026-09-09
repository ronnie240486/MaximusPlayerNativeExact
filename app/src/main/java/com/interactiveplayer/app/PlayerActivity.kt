package com.interactiveplayer.app

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageButton
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
 * Tela de reprodução — usada tanto pra canal ao vivo quanto pra rádio,
 * filme, série (recebe sempre "url" + "title" via Intent, igual as outras
 * telas já chamam). Pensada pra funcionar bem tanto no toque quanto no
 * D-pad de uma TV Box: o botão de voltar tem foco preferencial, e o botão
 * físico BACK/voltar do controle sempre fecha a tela sem precisar navegar
 * até um botão na tela primeiro.
 */
class PlayerActivity : ComponentActivity() {
    private val cyan = Color.rgb(53, 222, 231)
    private val white = Color.rgb(242, 244, 248)
    private val warning = Color.rgb(240, 169, 76)

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorContainer: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var titleText: TextView
    private lateinit var backButton: ImageButton
    private lateinit var playPauseButton: ImageButton

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
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            useController = false // controles próprios (topo), não os padrão do Media3
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        progressBar = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
        )

        // Barra superior — botão voltar + título, sempre visível, com fundo
        // semi-transparente pra ficar legível por cima de qualquer vídeo.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(140, 8, 16, 30))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        backButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_previous)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(white)
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = "Voltar"
            setOnClickListener { finish() }
        }
        topBar.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        titleText = TextView(this).apply {
            text = mediaTitle.ifBlank { "Reproduzindo" }
            setTextColor(white)
            textSize = 18f
            setPadding(dp(12), 0, 0, 0)
        }
        topBar.addView(
            titleText,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        playPauseButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_media_pause)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(white)
            isFocusable = true
            contentDescription = "Pausar ou continuar"
            setOnClickListener { togglePlayPause() }
        }
        topBar.addView(playPauseButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        root.addView(
            topBar,
            FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        )

        // Estado de erro — escondido até dar problema de verdade.
        errorContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(32), dp(32), dp(32), dp(32))
        }
        errorText = TextView(this).apply {
            setTextColor(warning)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        errorContainer.addView(errorText)
        val retryButton = TextView(this).apply {
            text = "Tentar de novo"
            setTextColor(Color.BLACK)
            setBackgroundColor(cyan)
            setPadding(dp(24), dp(12), dp(24), dp(12))
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            setOnClickListener { startPlayback(mediaUrl) }
        }
        errorContainer.addView(
            retryButton,
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
            playerView.player = exo
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                        Player.STATE_READY -> progressBar.visibility = View.GONE
                        Player.STATE_ENDED -> finish()
                        else -> {}
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playPauseButton.setImageResource(
                        if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    showError("Não foi possível reproduzir esse conteúdo agora. Confere sua internet ou tenta de novo em instantes.")
                }
            })
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun togglePlayPause() {
        val exo = player ?: return
        exo.playWhenReady = !exo.playWhenReady
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        errorText.text = message
        errorContainer.visibility = View.VISIBLE
        errorContainer.requestFocus()
    }

    /** Some com a barra de status/navegação, pra ficar tela cheia de verdade. */
    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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

    /**
     * Botão físico de VOLTAR do controle remoto (ou botão de voltar do
     * Android) sempre fecha o player direto, sem precisar navegar até um
     * botão na tela primeiro — importante pra experiência em TV Box.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (errorContainer.visibility != View.VISIBLE && !backButton.isFocused && !playPauseButton.isFocused) {
                togglePlayPause()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
