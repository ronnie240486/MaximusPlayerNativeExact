package com.interactiveplayer.app

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.Gravity
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Canal ao vivo — reescrita para ser o player imersivo em tela cheia
 * desde a primeira tela, igual ao original. A versão anterior era uma
 * "página de detalhes" com cabeçalho de texto e o vídeo espremido numa
 * caixa abaixo dele, usando o controlador padrão do Media3 — por isso
 * ficava cortado e sem os controles de verdade (retroceder/avançar,
 * redimensionar, legenda, PiP), que agora vêm do PlayerControls
 * compartilhado com a PlayerActivity.
 *
 * Não tem barra de progresso numérica (é ao vivo, sem duração) — em vez
 * disso mostra o selo "AO VIVO", como no PlayerControls com isLive=true.
 */
class ChannelDetailsActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var controls: PlayerControls
    private lateinit var item: M3uItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        item = M3uItem(
            name = intent.getStringExtra("name").orEmpty().ifBlank { "Canal" },
            group = intent.getStringExtra("group").orEmpty(),
            logo = intent.getStringExtra("logo"),
            url = intent.getStringExtra("url").orEmpty(),
            kind = M3uItem.Kind.CHANNEL,
        )

        setContentView(buildLayout())
        // window.insetsController só existe depois que o DecorView é
        // criado, o que só acontece com setContentView() já chamado - fazer
        // isso antes derrubava a tela com NullPointerException em alguns
        // aparelhos.
        hideSystemBars()
        startPlayback()
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        playerView = PlayerView(this).apply { useController = false }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        controls = PlayerControls(
            activity = this,
            root = root,
            playerView = playerView,
            isLive = true,
            onBack = { finish() },
        )
        root.addView(controls.build(), FrameLayout.LayoutParams(-1, -1))

        progressBar = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
        }
        root.addView(progressBar, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))

        // O PlayerControls não sabe nada sobre favoritos (PlayerActivity
        // toca filme/série/rádio, que já favoritam em outra tela) — o
        // coração fica só aqui, flutuando abaixo da barra de ícones.
        val favorite = android.widget.TextView(this).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            background = circleDrawable(android.graphics.Color.argb(90, 30, 36, 56))
            isFocusable = true
            isClickable = true
        }
        fun refreshFavoriteIcon() {
            val active = FavoriteStore.contains(this, item)
            favorite.setText(if (active) "♥" else "♡")
            favorite.setTextColor(if (active) Theme.accentMagenta else Theme.white)
        }
        refreshFavoriteIcon()
        favorite.setOnClickListener {
            FavoriteStore.toggle(this, item)
            refreshFavoriteIcon()
        }
        root.addView(
            favorite,
            FrameLayout.LayoutParams(dp(38), dp(38), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(58)
                rightMargin = dp(12)
            }
        )

        // Guia de programação — Agora/A seguir em texto + faixa horizontal
        // com os próximos programas, cada um com um sino de lembrete.
        // Fica de fora quando o painel não expõe get_short_epg pra esse
        // canal, sem quebrar o player nesse caso.
        val epgBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val epgSummary = android.widget.TextView(this).apply {
            textSize = 12f
            setTextColor(Theme.textSecondary)
            setLineSpacing(dpF(4f), 1f)
            maxLines = 2
        }
        epgBlock.addView(epgSummary, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
        val epgStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val epgScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(epgStrip)
        }
        epgBlock.addView(epgScroll, LinearLayout.LayoutParams(-1, -2))

        root.addView(
            epgBlock,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = dp(Theme.SPACING_MD)
                rightMargin = dp(Theme.SPACING_MD)
                bottomMargin = dp(66)
            }
        )
        loadEpg(epgBlock, epgSummary, epgStrip)

        WatchHistoryStore.record(this, item)
        return root
    }

    private fun loadEpg(block: LinearLayout, summary: android.widget.TextView, strip: LinearLayout) {
        lifecycleScope.launch {
            val programs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                EpgClient.fetchSchedule(this@ChannelDetailsActivity, item)
            }
            if (programs.isEmpty() || isFinishing) return@launch

            val now = programs.firstOrNull { it.isNow }
            val next = programs.firstOrNull { !it.isNow }
            val lines = buildList {
                now?.let { add("Agora: ${it.title} (${it.startLabel}–${it.endLabel})") }
                next?.let { add("A seguir: ${it.title} (${it.startLabel})") }
            }
            if (lines.isEmpty()) return@launch
            summary.setText(lines.joinToString("\n"))

            programs.forEach { program -> strip.addView(buildEpgCard(program)) }
            block.visibility = View.VISIBLE
        }
    }

    /** Card AGORA/A SEGUIR com logo do canal, horário e sino de lembrete. */
    private fun buildEpgCard(program: EpgClient.Program): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(android.widget.TextView(this).apply {
            setText(if (program.isNow) "AGORA" else "A SEGUIR")
            textSize = 10f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setTextColor(if (program.isNow) Theme.accentCyan else Theme.textMuted)
        }, LinearLayout.LayoutParams(0, -2, 1f))

        // Sino: marca/desmarca lembrete visualmente. Ainda não dispara
        // notificação de verdade — só o toggle visual por enquanto.
        val bell = android.widget.TextView(this).apply {
            setText("🔔")
            textSize = 12f
            alpha = 0.4f
            isFocusable = true
            isClickable = true
            setOnClickListener {
                alpha = if (alpha > 0.5f) 0.4f else 1f
                android.widget.Toast.makeText(
                    this@ChannelDetailsActivity,
                    if (alpha > 0.5f) "Lembrete marcado (não notifica ainda, só visual)" else "Lembrete removido",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        header.addView(bell)
        card.addView(header, LinearLayout.LayoutParams(dp(150), -2))

        card.addView(android.widget.TextView(this).apply {
            setText(program.title)
            textSize = 12f
            setTextColor(Theme.white)
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        card.addView(android.widget.TextView(this).apply {
            setText("${program.startLabel}–${program.endLabel}")
            textSize = 10f
            setTextColor(Theme.textMuted)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })

        card.layoutParams = LinearLayout.LayoutParams(dp(150), -2).apply {
            rightMargin = dp(Theme.SPACING_SM)
        }
        return card
    }

    private fun startPlayback() {
        if (item.url.isBlank()) return
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    progressBar.visibility = if (state == Player.STATE_READY) View.GONE else View.VISIBLE
                }
                override fun onPlayerError(error: PlaybackException) {
                    progressBar.visibility = View.GONE
                }
            })
            exo.setMediaItem(MediaItem.fromUri(item.url))
            exo.prepare()
            exo.playWhenReady = true
            controls.bind(exo, item.name)
        }
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
