package com.interactiveplayer.app

import android.app.PictureInPictureParams
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Rational
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    // Canal ao vivo usa o SharedChannelPlayer (mesmo player da caixinha
    // de detalhes, sem reiniciar ao entrar/sair da tela cheia). Filme,
    // série e rádio continuam com o ExoPlayer próprio desta tela.
    private var isLive: Boolean = false
    private var currentStreamId: String? = null
    private var mediaLogo: String? = null
    private var allChannels: List<M3uItem> = emptyList()
    private lateinit var gridOverlay: FrameLayout
    private lateinit var gridAdapter: ChannelGridAdapter
    private lateinit var epgSummary: TextView
    private lateinit var epgStrip: LinearLayout
    private lateinit var liveTitleText: TextView
    private lateinit var liveLogoView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaUrl = intent.getStringExtra("url").orEmpty()
        mediaTitle = intent.getStringExtra("title").orEmpty()
        isLive = intent.getBooleanExtra("isLive", false)
        currentStreamId = intent.getStringExtra("streamId")
        mediaLogo = intent.getStringExtra("logo")

        setContentView(buildLayout())
        // window.insetsController só existe depois que o DecorView é
        // criado, o que só acontece com setContentView() já chamado - fazer
        // isso antes derrubava a tela com NullPointerException em alguns
        // aparelhos.
        hideSystemBars()

        if (mediaUrl.isBlank()) {
            showError("Não recebi uma URL válida pra reproduzir.")
            return
        }
        startPlayback(mediaUrl)
        if (isLive) loadLiveEpg()
    }

    private fun buildLayout(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        playerView = PlayerView(this).apply { useController = false }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        controls = PlayerControls(
            activity = this,
            root = root,
            playerView = playerView,
            isLive = isLive,
            onBack = { finish() },
            onChannelGridRequested = if (isLive) { { openChannelGrid() } } else null,
        )
        root.addView(controls.build(), FrameLayout.LayoutParams(-1, -1))

        if (isLive) {
            controls.hideBuiltInTitle()
            controls.hideBuiltInLiveBadge()
            root.addView(
                buildLiveInfoBlock(),
                FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dp(64) }
            )
        }

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

        if (isLive) root.addView(buildGridOverlay(), FrameLayout.LayoutParams(-1, -1))

        return root
    }

    // -----------------------------------------------------------------
    // Lista de canais dentro da tela cheia (só ao vivo)
    // -----------------------------------------------------------------

    private fun buildGridOverlay(): View {
        gridOverlay = FrameLayout(this).apply { visibility = View.GONE }

        val backdrop = View(this).apply {
            // Tela cheia de verdade, fundo opaco — igual à referência.
            setBackgroundColor(Color.rgb(11, 15, 26))
            isClickable = true
            setOnClickListener { gridOverlay.visibility = View.GONE }
        }
        gridOverlay.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(Theme.SPACING_MD), 0, 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.SPACING_LG), 0, dp(Theme.SPACING_LG), dp(Theme.SPACING_SM))
        }
        header.addView(TextView(this).apply {
            setText("Canais")
            textSize = 26f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            setText("✕")
            textSize = 18f
            setTextColor(Theme.white)
            isFocusable = true
            isClickable = true
            setOnClickListener { gridOverlay.visibility = View.GONE }
        })
        panel.addView(header)

        val search = EditText(this).apply {
            hint = "Buscar canal..."
            textSize = 15f
            setSingleLine(true)
            setTextColor(Theme.white)
            setHintTextColor(Theme.textMuted)
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, dp(44)).apply {
            leftMargin = dp(Theme.SPACING_LG)
            rightMargin = dp(Theme.SPACING_LG)
            bottomMargin = dp(Theme.SPACING_SM)
        })

        val recycler = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@PlayerActivity) }
        gridAdapter = ChannelGridAdapter { chosen -> switchToChannel(chosen) }
        recycler.adapter = gridAdapter
        panel.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))

        gridOverlay.addView(panel, FrameLayout.LayoutParams(-1, -1))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim()?.lowercase().orEmpty()
                gridAdapter.submit(allChannels.filter { query.isEmpty() || it.name.lowercase().contains(query) })
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

        lifecycleScope.launch {
            allChannels = CatalogRepository.load(this@PlayerActivity).filter { it.kind == M3uItem.Kind.CHANNEL }
            gridAdapter.submit(allChannels)
        }

        return gridOverlay
    }

    private fun openChannelGrid() {
        gridOverlay.visibility = View.VISIBLE
    }

    /** Troca de canal sem sair da tela cheia — atualiza player, título e streamId. */
    private fun switchToChannel(chosen: M3uItem) {
        gridOverlay.visibility = View.GONE
        mediaUrl = chosen.url
        mediaTitle = chosen.name
        mediaLogo = chosen.logo
        currentStreamId = chosen.streamId
        liveTitleText.setText(mediaTitle)
        liveLogoView.setImageBitmap(null)
        mediaLogo?.takeIf { it.isNotBlank() }?.let { url ->
            lifecycleScope.launch {
                val bitmap = ImageLoader.load(url, dp(64), dp(64))
                if (bitmap != null) liveLogoView.setImageBitmap(bitmap)
            }
        }
        WatchHistoryStore.record(this, chosen)
        startPlayback(mediaUrl)
        loadLiveEpg()
    }

    private fun startPlayback(url: String) {
        errorContainer.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        val exo = if (isLive) {
            // Mesma instância que a ChannelDetailsActivity já criou —
            // continua exatamente de onde estava, sem re-buffer.
            SharedChannelPlayer.playerFor(this, url)
        } else {
            player?.release()
            ExoPlayer.Builder(this).build().also {
                it.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                it.prepare()
                it.playWhenReady = true
            }
        }
        player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                    Player.STATE_READY -> progressBar.visibility = View.GONE
                    Player.STATE_ENDED -> if (!isLive) finish()
                    else -> {}
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                showError("Não foi possível reproduzir esse conteúdo agora. Confere sua internet ou tenta de novo em instantes.")
            }
        })
        // O listener só avisa sobre MUDANÇAS futuras de estado. Se o
        // player já veio pronto (caso comum aqui: a caixinha de
        // detalhes já estava tocando o canal antes de abrir a tela
        // cheia), nenhuma mudança nunca chega e a bolinha de
        // carregamento ficava girando pra sempre. Sincroniza direto com
        // o estado atual, sem depender só do listener.
        progressBar.visibility = if (exo.playbackState == Player.STATE_READY) View.GONE else View.VISIBLE
        controls.bind(exo, mediaTitle.ifBlank { "Reproduzindo" }, url = url)
    }

    // -----------------------------------------------------------------
    // Bloco de EPG na tela cheia — logo, título, AO VIVO, Agora/A seguir
    // e a faixa de programação, igual à referência.
    // -----------------------------------------------------------------

    private fun buildLiveInfoBlock(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
        }

        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = roundRect(Theme.white, Theme.RADIUS_SM)
        }
        liveLogoView = logo
        row.addView(logo, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(Theme.SPACING_SM) })
        mediaLogo?.takeIf { it.isNotBlank() }?.let { url ->
            lifecycleScope.launch {
                val bitmap = ImageLoader.load(url, dp(64), dp(64))
                if (bitmap != null) logo.setImageBitmap(bitmap)
            }
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        liveTitleText = TextView(this).apply {
            setText(mediaTitle)
            textSize = 18f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        texts.addView(liveTitleText)
        texts.addView(TextView(this).apply {
            setText("● AO VIVO")
            textSize = 11f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Theme.black)
            gravity = Gravity.CENTER
            background = roundRect(Theme.accentCyan, 4)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })
        epgSummary = TextView(this).apply {
            textSize = 12f
            setTextColor(Theme.textSecondary)
            setLineSpacing(dpF(3f), 1f)
            maxLines = 2
        }
        texts.addView(epgSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.SPACING_SM) })

        val stripScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
        }
        epgStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stripScroll.addView(epgStrip)
        column.addView(stripScroll, LinearLayout.LayoutParams(-1, -2))

        return column
    }

    private fun loadLiveEpg() {
        val streamId = currentStreamId
        if (streamId == null) {
            // Sem streamId não dá pra chamar o EPG — mas isso não pode
            // ficar em branco sem explicação nenhuma pra quem está
            // vendo a tela.
            epgSummary.setText("Sem informações de programação para este canal.")
            return
        }
        val item = M3uItem(mediaTitle, "", mediaLogo, mediaUrl, M3uItem.Kind.CHANNEL, streamId)
        lifecycleScope.launch {
            val programs = withContext(Dispatchers.IO) { EpgClient.fetchSchedule(this@PlayerActivity, item) }
            epgStrip.removeAllViews()
            if (programs.isEmpty()) {
                epgSummary.setText("Sem informações de programação para este canal.")
                return@launch
            }
            val nowIndex = programs.indexOfFirst { it.isNow }
            val now = programs.getOrNull(nowIndex)
            val next = programs.getOrNull(nowIndex + 1) ?: programs.firstOrNull { !it.isNow }
            epgSummary.setText(
                buildList {
                    now?.let { add("Agora: ${it.title} (${it.startLabel}–${it.endLabel})") }
                    next?.let { add("A seguir: ${it.title} (${it.startLabel})") }
                }.joinToString("\n")
            )
            programs.forEachIndexed { index, program ->
                epgStrip.addView(buildEpgCard(program, index == nowIndex, streamId))
            }
        }
    }

    private fun buildEpgCard(program: EpgClient.Program, isNow: Boolean, streamId: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Color.argb(140, 22, 27, 46), Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_SM), dp(6), dp(Theme.SPACING_SM), dp(6))
            layoutParams = LinearLayout.LayoutParams(dp(140), -2).apply { rightMargin = dp(Theme.SPACING_SM) }
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(TextView(this).apply {
            setText(if (isNow) "AGORA" else "A SEGUIR")
            textSize = 9f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(if (isNow) Theme.star else Theme.textMuted)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (!isNow) {
            val reminderId = "$streamId-${program.startLabel}-${program.title}"
            val bell = TextView(this).apply { textSize = 13f; isFocusable = true; isClickable = true }
            fun refresh() {
                val scheduled = ProgramReminderStore.isScheduled(this@PlayerActivity, reminderId)
                bell.setText(if (scheduled) "🔔" else "🔕")
            }
            refresh()
            bell.setOnClickListener {
                ProgramReminderStore.toggle(
                    this,
                    ProgramReminderStore.Reminder(
                        reminderId, program.title, streamId, mediaTitle, mediaLogo,
                        startsAtMsFromLabel(program.startLabel)
                    )
                )
                refresh()
            }
            topRow.addView(bell)
        }
        card.addView(topRow)
        card.addView(TextView(this).apply {
            setText(program.title)
            textSize = 12f
            setTextColor(Theme.white)
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        card.addView(TextView(this).apply {
            setText("${program.startLabel} - ${program.endLabel}")
            textSize = 10f
            setTextColor(Theme.textMuted)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        return card
    }

    private fun startsAtMsFromLabel(label: String): Long = runCatching {
        val parts = label.split(":")
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
        }
        if (calendar.timeInMillis < System.currentTimeMillis()) calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        calendar.timeInMillis
    }.getOrDefault(System.currentTimeMillis())

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
        // Canal ao vivo: não pausa — precisa continuar tocando por trás
        // pra caixinha de detalhes retomar de onde estava.
        if (!isLive) player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        controls.release()
        if (isLive) {
            // Só desanexa: o SharedChannelPlayer continua vivo pra
            // caixinha de detalhes reaproveitar. Quem libera de vez é a
            // ChannelDetailsActivity, quando a pessoa sai do canal.
            playerView.player = null
        } else {
            player?.release()
        }
        player = null
        super.onDestroy()
    }
}
