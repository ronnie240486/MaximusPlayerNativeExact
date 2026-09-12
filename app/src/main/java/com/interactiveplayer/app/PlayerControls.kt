package com.interactiveplayer.app

import android.app.PictureInPictureParams
import android.graphics.Typeface
import android.os.Build
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Controles de player customizados, compartilhados entre `PlayerActivity`
 * (filmes/séries/rádio) e `ChannelDetailsActivity` (canal ao vivo).
 *
 * O ExoPlayer padrão (`useController = true`) usa o esqueleto genérico
 * do Media3 — sem retroceder/avançar 10s, sem alternar modo de tela,
 * sem seletor de legenda, sem Picture-in-Picture. Esta classe monta a
 * mesma barra de controles nas duas telas: voltar, título, ícones de
 * redimensionar/legenda/PiP no topo; retroceder 10s / play-pause /
 * avançar 10s no centro; barra de progresso embaixo (ou selo "AO VIVO"
 * quando não há duração, como num canal).
 */
class PlayerControls(
    private val activity: ComponentActivity,
    private val root: FrameLayout,
    private val playerView: PlayerView,
    private val isLive: Boolean,
    private val onBack: () -> Unit,
    /** Só preenchido em canal ao vivo — abre a lista de canais sem sair da tela cheia. */
    private val onChannelGridRequested: (() -> Unit)? = null,
) {
    private var player: ExoPlayer? = null
    private var hideJob: Job? = null
    private var progressJob: Job? = null
    private var currentUrl: String? = null

    private lateinit var overlay: FrameLayout
    private lateinit var titleText: TextView
    private lateinit var playPauseButton: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var liveBadge: TextView
    private var draggingSeek = false

    /** Chamado depois que o ExoPlayer é criado, pra ligar os controles nele. */
    fun bind(exo: ExoPlayer, title: String, url: String? = null, logo: android.graphics.Bitmap? = null) {
        player = exo
        currentUrl = url
        titleText.setText(title)
        playerView.player = exo
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playPauseButton.setText(if (isPlaying) "❚❚" else "▶")
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) updateSeekBounds()
            }
        })
        updateSeekBounds()
        startProgressLoop()
        scheduleAutoHide()
    }

    /** Monta o overlay e devolve pra quem chamou adicionar na hierarquia. */
    fun build(): View {
        overlay = FrameLayout(activity)

        // Toca em qualquer lugar do vídeo pra mostrar/esconder os controles.
        overlay.setOnClickListener { toggleVisibility() }

        overlay.addView(buildTopBar(), FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        // Título solto sobre o vídeo (não mais espremido na barrinha de
        // cima) — fica acima da barra inferior, como na referência.
        overlay.addView(
            buildTitleOverlay(),
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                leftMargin = dp(Theme.SPACING_MD)
                bottomMargin = dp(96)
            }
        )
        // largura WRAP_CONTENT (-2), não MATCH_PARENT: com -1 o bloco
        // ocupava a tela toda e "gravity = CENTER_VERTICAL" só
        // centralizava verticalmente — os botões ficavam colados à
        // esquerda em vez de no centro de verdade da tela.
        overlay.addView(buildCenterControls(), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        overlay.addView(buildBottomBar(), FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        return overlay
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.argb(140, 11, 15, 26))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        bar.addView(iconButton("‹", 22f) { onBack() }, LinearLayout.LayoutParams(dp(40), dp(40)))
        bar.addView(View(activity), LinearLayout.LayoutParams(0, -2, 1f))

        // Lista de canais, só em canal ao vivo.
        onChannelGridRequested?.let { onRequest ->
            bar.addView(
                iconButton("⊞", 18f) { onRequest() },
                LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(6) }
            )
        }

        // Dois botões separados — FIT (mostra tudo, pode sobrar borda) e
        // ZOOM (preenche a tela cortando as bordas) — em vez de um só
        // alternando, pra bater com os dois ícones de "expandir" da
        // referência.
        bar.addView(
            iconButton("⛶", 15f) { setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT) },
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(6) }
        )
        bar.addView(
            iconButton("⛶", 20f) { setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) },
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(6) }
        )
        // "Abrir externamente" — sem SDK de Chromecast aqui, então em
        // vez de um cast de verdade isso manda o link pra outro app
        // (VLC, navegador etc.) que consiga tocar.
        bar.addView(
            iconButton("⇱", 16f) { openExternally() },
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(6) }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            bar.addView(
                iconButton("▭", 16f) { enterPip() },
                LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(6) }
            )
        }
        bar.addView(iconButton("CC", 12f) { showSubtitlePicker() }, LinearLayout.LayoutParams(dp(38), dp(38)))
        return bar
    }

    /** Título solto sobre o vídeo, como na referência — não espremido na barra fina de cima. */
    private fun buildTitleOverlay(): View {
        titleText = TextView(activity).apply {
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        }
        return titleText
    }

    private fun buildCenterControls(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            iconButton("⟲10", 15f) { seekBy(-10_000) },
            LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(Theme.SPACING_LG) }
        )
        playPauseButton = iconButton("▶", 26f) { togglePlayPause() }.apply {
            background = circleDrawable(android.graphics.Color.argb(90, 76, 232, 240))
        }
        row.addView(playPauseButton, LinearLayout.LayoutParams(dp(72), dp(72)))
        row.addView(
            iconButton("10⟳", 15f) { seekBy(10_000) },
            LinearLayout.LayoutParams(dp(56), dp(56)).apply { leftMargin = dp(Theme.SPACING_LG) }
        )
        return row
    }

    private fun buildBottomBar(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.argb(140, 11, 15, 26))
            setPadding(dp(Theme.SPACING_MD), dp(Theme.SPACING_SM), dp(Theme.SPACING_MD), dp(Theme.SPACING_SM))
        }

        liveBadge = TextView(activity).apply {
            setText("AO VIVO")
            textSize = 11f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Theme.black)
            gravity = Gravity.CENTER
            background = activity.roundRect(Theme.accentMagenta, 4)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            visibility = if (isLive) View.VISIBLE else View.GONE
        }
        bar.addView(liveBadge, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) })

        val seekRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (isLive) View.GONE else View.VISIBLE
        }
        currentTimeText = TextView(activity).apply {
            setText("00:00")
            textSize = 11f
            setTextColor(Theme.textSecondary)
        }
        seekRow.addView(currentTimeText, LinearLayout.LayoutParams(-2, -2))

        seekBar = SeekBar(activity).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) currentTimeText.setText(formatTime(value.toLong()))
                }
                override fun onStartTrackingTouch(seek: SeekBar?) { draggingSeek = true }
                override fun onStopTrackingTouch(seek: SeekBar?) {
                    draggingSeek = false
                    player?.seekTo(seek?.progress?.toLong() ?: 0)
                }
            })
        }
        seekRow.addView(seekBar, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(Theme.SPACING_SM)
            rightMargin = dp(Theme.SPACING_SM)
        })

        totalTimeText = TextView(activity).apply {
            setText("00:00")
            textSize = 11f
            setTextColor(Theme.textSecondary)
        }
        seekRow.addView(totalTimeText, LinearLayout.LayoutParams(-2, -2))
        bar.addView(seekRow, LinearLayout.LayoutParams(-1, -2))
        return bar
    }

    private fun iconButton(label: String, sizeSp: Float, onClick: () -> Unit): TextView =
        TextView(activity).apply {
            setText(label)
            textSize = sizeSp
            setTextColor(Theme.white)
            gravity = Gravity.CENTER
            background = circleDrawable(android.graphics.Color.argb(90, 30, 36, 56))
            isFocusable = true
            isClickable = true
            setOnClickListener {
                onClick()
                scheduleAutoHide()
            }
        }

    private fun togglePlayPause() {
        val exo = player ?: return
        exo.playWhenReady = !exo.playWhenReady
    }

    private fun seekBy(deltaMs: Long) {
        val exo = player ?: return
        val target = (exo.currentPosition + deltaMs).coerceIn(0, exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        exo.seekTo(target)
    }

    private fun setResizeMode(mode: Int) {
        playerView.resizeMode = mode
        val label = when (mode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Preencher tela (cortando bordas)"
            else -> "Ajustar (mantém proporção original)"
        }
        Toast.makeText(activity, label, Toast.LENGTH_SHORT).show()
    }

    /** Sem SDK de Chromecast aqui — abre com outro app instalado que toque o link. */
    private fun openExternally() {
        val url = currentUrl ?: return
        runCatching {
            activity.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            )
        }.onFailure {
            Toast.makeText(activity, "Nenhum app encontrado pra abrir esse link.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Lista as faixas de legenda disponíveis no stream e deixa escolher. */
    private fun showSubtitlePicker() {
        val exo = player ?: return
        val textGroups = exo.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (textGroups.isEmpty()) {
            Toast.makeText(activity, "Esse conteúdo não tem legendas disponíveis.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = mutableListOf("Nenhuma")
        val entries = mutableListOf<Pair<androidx.media3.common.TrackGroup, Int>?>(null)
        textGroups.forEach { group ->
            for (index in 0 until group.length) {
                val format = group.getTrackFormat(index)
                labels += format.language?.uppercase(Locale.getDefault()) ?: "Legenda ${entries.size}"
                entries += group.mediaTrackGroup to index
            }
        }
        android.app.AlertDialog.Builder(activity)
            .setTitle("Legendas")
            .setItems(labels.toTypedArray()) { _, which ->
                val chosen = entries[which]
                val params = exo.trackSelectionParameters.buildUpon()
                if (chosen == null) {
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    params.setOverrideForType(
                        TrackSelectionOverride(chosen.first, chosen.second)
                    )
                }
                exo.trackSelectionParameters = params.build()
            }
            .show()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            activity.enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }.onFailure {
            Toast.makeText(activity, "Picture-in-Picture não é suportado neste aparelho.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSeekBounds() {
        val exo = player ?: return
        if (isLive) return
        val duration = exo.duration.takeIf { it > 0 } ?: return
        seekBar.max = duration.toInt()
        totalTimeText.setText(formatTime(duration))
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        if (isLive) return
        progressJob = activity.lifecycleScope.launch {
            while (true) {
                val exo = player
                if (exo != null && !draggingSeek) {
                    seekBar.progress = exo.currentPosition.toInt()
                    currentTimeText.setText(formatTime(exo.currentPosition))
                }
                delay(500)
            }
        }
    }

    private fun toggleVisibility() {
        if (overlay.alpha > 0.5f) hide() else show()
    }

    private fun show() {
        overlay.animate().alpha(1f).setDuration(150).start()
        scheduleAutoHide()
    }

    private fun hide() {
        hideJob?.cancel()
        overlay.animate().alpha(0f).setDuration(150).start()
    }

    /** Some sozinho depois de 4s parado, igual a qualquer player de vídeo. */
    private fun scheduleAutoHide() {
        hideJob?.cancel()
        hideJob = activity.lifecycleScope.launch {
            delay(4000)
            overlay.animate().alpha(0f).setDuration(200).start()
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    fun release() {
        hideJob?.cancel()
        progressJob?.cancel()
    }

    private fun dp(value: Int): Int = activity.dp(value)
}
