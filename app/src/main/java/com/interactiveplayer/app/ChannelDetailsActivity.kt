package com.interactiveplayer.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detalhe de canal, portado de `frontend/app/channel-details.tsx` —
 * NÃO é a tela de player em si (isso é a PlayerActivity, aberta pelo
 * ícone de tela cheia ou tocando no vídeo). É uma página com um preview
 * do canal numa caixa, guia de programação embaixo, e um painel lateral
 * pra trocar de canal sem sair da tela (o ícone de grade no topo do
 * vídeo).
 *
 * A versão anterior tinha virado a própria tela de player em tela
 * cheia — decisão errada, misturando os dois conceitos que o original
 * mantém separados.
 */
class ChannelDetailsActivity : ComponentActivity() {

    // O player em si vive no SharedChannelPlayer agora — não há mais um
    // ExoPlayer próprio desta tela.
    private lateinit var playerView: PlayerView
    private lateinit var channelNameText: TextView
    private lateinit var favoriteButton: TextView
    private lateinit var categoryText: TextView
    private lateinit var epgList: LinearLayout
    private lateinit var epgStatus: TextView
    private lateinit var gridOverlay: FrameLayout
    private lateinit var gridAdapter: ChannelGridAdapter

    private lateinit var item: M3uItem
    private var allChannels: List<M3uItem> = emptyList()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = itemFromIntent(intent)
        setContentView(buildScreen())
        startPlayback()
        loadEpg()
        askNotificationPermissionIfNeeded()
    }

    private fun itemFromIntent(source: Intent): M3uItem {
        val streamId = source.getStringExtra("streamId")
        var url = source.getStringExtra("url").orEmpty()
        // A notificação de lembrete só manda nome/streamId (não guarda a
        // URL completa) — dá pra remontar com as credenciais salvas,
        // do mesmo jeito que o painel monta a URL de um canal ao vivo.
        if (url.isBlank() && !streamId.isNullOrBlank()) {
            XtreamCredentials.load(this)?.let { creds ->
                url = "${creds.server}/live/${creds.username}/${creds.password}/$streamId.m3u8"
            }
        }
        return M3uItem(
            name = source.getStringExtra("name").orEmpty().ifBlank { "Canal" },
            group = source.getStringExtra("group").orEmpty(),
            logo = source.getStringExtra("logo"),
            url = url,
            kind = M3uItem.Kind.CHANNEL,
            streamId = streamId,
        )
    }

    // -----------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.SPACING_MD), dp(Theme.SPACING_SM), dp(Theme.SPACING_MD), dp(Theme.SPACING_SM))
        }
        page.addView(buildHeaderRow())

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(buildVideoColumn(), LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(buildEpgColumn(), LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = dp(Theme.SPACING_MD)
        })
        page.addView(body, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(Theme.SPACING_SM)
        })

        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildGridOverlay(), FrameLayout.LayoutParams(-1, -1))
        return root
    }

    /** headerRow: voltar, nome do canal, favorito. */
    private fun buildHeaderRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            setText("‹")
            textSize = 22f
            setTextColor(Theme.white)
            setPadding(dp(6), dp(4), dp(10), dp(4))
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        })
        channelNameText = TextView(this).apply {
            setText(item.name)
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        }
        row.addView(channelNameText, LinearLayout.LayoutParams(0, -2, 1f))
        favoriteButton = TextView(this).apply {
            textSize = 20f
            isFocusable = true
            isClickable = true
            setOnClickListener {
                FavoriteStore.toggle(this@ChannelDetailsActivity, item)
                refreshFavorite()
            }
        }
        refreshFavorite()
        row.addView(favoriteButton)
        return row
    }

    private fun refreshFavorite() {
        val active = FavoriteStore.contains(this, item)
        favoriteButton.setText(if (active) "♥" else "♡")
        favoriteButton.setTextColor(if (active) Theme.accentMagenta else Theme.textSecondary)
    }

    /** videoWrap + categoryLine: caixa de preview (não tela cheia) + categoria. */
    private fun buildVideoColumn(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val videoBox = FrameLayout(this).apply {
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            clipToOutline = true
            isFocusable = true
            isClickable = true
            setOnClickListener { openFullscreenPlayer() }
        }
        playerView = PlayerView(this).apply { useController = false }
        videoBox.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        val topActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        topActions.addView(videoIconButton("⊞") { openChannelGrid() }, LinearLayout.LayoutParams(dp(34), dp(34)))
        topActions.addView(
            videoIconButton("⛶") { openFullscreenPlayer() },
            LinearLayout.LayoutParams(dp(34), dp(34)).apply { leftMargin = dp(8) }
        )
        videoBox.addView(
            topActions,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(Theme.SPACING_SM)
                rightMargin = dp(Theme.SPACING_SM)
            }
        )

        column.addView(videoBox, LinearLayout.LayoutParams(-1, 0, 1f))

        categoryText = TextView(this).apply {
            setText(if (item.group.isNotBlank()) "Categoria ${item.group}" else "")
            textSize = 12f
            setTextColor(Theme.textSecondary)
        }
        column.addView(categoryText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        return column
    }

    private fun videoIconButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            setText(label)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Theme.white)
            background = circleDrawable(Color.argb(150, 11, 15, 26))
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
        }

    /** epgHeaderRow + epgRow: guia de programação, com o selo "Hoje". */
    private fun buildEpgColumn(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            setText("Guia de programação")
            textSize = 15f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            setText("📅 Hoje")
            textSize = 11f
            setTextColor(Theme.textSecondary)
            background = roundRect(Theme.darkSurface, Theme.RADIUS_PILL)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        })
        column.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        epgStatus = TextView(this).apply {
            setText("Carregando programação...")
            textSize = 12f
            setTextColor(Theme.textMuted)
        }
        column.addView(epgStatus, LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this)
        epgList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(epgList)
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return column
    }

    // -----------------------------------------------------------------
    // Player embutido (preview, não tela cheia)
    // -----------------------------------------------------------------

    private fun startPlayback() {
        if (item.url.isBlank()) return
        val shared = SharedChannelPlayer.playerFor(this, item.url)
        playerView.player = shared
    }

    private fun openFullscreenPlayer() {
        // Solta a PlayerView desta tela ANTES de abrir a tela cheia. Sem
        // isso, quando a pessoa volta, o Media3 vê que esta PlayerView
        // já "tem" esse player atribuído (mesmo que a superfície de
        // vídeo tenha ido pra outra tela nesse meio tempo) e ignora a
        // reatribuição no onResume — resultado: caixinha fica com tela
        // preta em vez de voltar a mostrar o vídeo.
        playerView.player = null
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("title", item.name)
                .putExtra("isLive", true)
                .putExtra("streamId", item.streamId)
        )
    }

    // -----------------------------------------------------------------
    // EPG (Agora/A seguir + lembrete real de notificação)
    // -----------------------------------------------------------------

    private fun loadEpg() {
        lifecycleScope.launch {
            val programs = withContext(Dispatchers.IO) { EpgClient.fetchSchedule(this@ChannelDetailsActivity, item) }
            epgList.removeAllViews()
            if (programs.isEmpty()) {
                epgStatus.setText("Sem informações de programação para este canal.")
                return@launch
            }
            epgStatus.visibility = View.GONE
            val nowIndex = programs.indexOfFirst { it.isNow }
            programs.forEachIndexed { index, program ->
                val isNow = index == nowIndex
                val isNext = index == nowIndex + 1 || (nowIndex == -1 && index == 0)
                epgList.addView(buildEpgRow(program, isNow, isNext))
            }
        }
    }

    private fun buildEpgRow(program: EpgClient.Program, isNow: Boolean, isNext: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM))
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (isNow || isNext) {
            texts.addView(TextView(this).apply {
                setText(if (isNow) "AGORA" else "A SEGUIR")
                textSize = 9f
                setTypeface(Typeface.DEFAULT_BOLD)
                letterSpacing = letterSpacingEm(1f, 9f)
                setTextColor(if (isNow) Theme.star else Theme.textMuted)
            })
        }
        texts.addView(TextView(this).apply {
            setText(program.title)
            textSize = 13f
            setTextColor(Theme.white)
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        texts.addView(TextView(this).apply {
            setText("${program.startLabel} – ${program.endLabel}")
            textSize = 11f
            setTextColor(Theme.textMuted)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        // "Agora" mostra o tanto que já passou do programa, os outros
        // ganham o sino de lembrete — igual ao original.
        if (isNow) {
            val startMs = System.currentTimeMillis()
            row.addView(TextView(this).apply {
                setText("● AO VIVO")
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setTextColor(Theme.accentCyan)
            })
        } else {
            val reminderId = "${item.streamId}-${program.startLabel}-${program.title}"
            val bell = TextView(this).apply {
                textSize = 16f
                isFocusable = true
                isClickable = true
            }
            fun refreshBell() {
                val scheduled = ProgramReminderStore.isScheduled(this@ChannelDetailsActivity, reminderId)
                bell.setText(if (scheduled) "🔔" else "🔕")
                bell.setTextColor(if (scheduled) Theme.star else Theme.textMuted)
            }
            refreshBell()
            bell.setOnClickListener {
                val startsAt = parseTodayHm(program.startLabel)
                val nowScheduled = ProgramReminderStore.toggle(
                    this,
                    ProgramReminderStore.Reminder(
                        id = reminderId,
                        title = program.title,
                        channelStreamId = item.streamId.orEmpty(),
                        channelName = item.name,
                        channelLogo = item.logo,
                        startsAtMs = startsAt,
                    )
                )
                refreshBell()
                Toast.makeText(
                    this,
                    if (nowScheduled) "A gente te avisa quando esse programa começar." else "Lembrete removido.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            row.addView(bell)
        }

        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        return row
    }

    /** "HH:mm" de hoje vira epoch ms — se já passou, assume amanhã. */
    private fun parseTodayHm(label: String): Long = runCatching {
        val parts = label.split(":")
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(java.util.Calendar.MINUTE, parts[1].toInt())
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        calendar.timeInMillis
    }.getOrDefault(System.currentTimeMillis())

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // -----------------------------------------------------------------
    // Painel de troca de canal (ícone de grade)
    // -----------------------------------------------------------------

    private fun buildGridOverlay(): View {
        gridOverlay = FrameLayout(this).apply { visibility = View.GONE }

        val backdrop = View(this).apply {
            setBackgroundColor(Color.argb(1, 0, 0, 0))
            isClickable = true
            setOnClickListener { closeChannelGrid() }
        }
        gridOverlay.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        val panelWidth = minOf((resources.displayMetrics.widthPixels * 0.5f).toInt(), dp(380))
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(235, 11, 15, 26))
            setPadding(0, dp(Theme.SPACING_MD), 0, 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), dp(8))
        }
        header.addView(TextView(this).apply {
            setText("Canais")
            textSize = 16f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            setText("✕")
            textSize = 18f
            setTextColor(Theme.white)
            isFocusable = true
            isClickable = true
            setOnClickListener { closeChannelGrid() }
        })
        panel.addView(header)

        val search = EditText(this).apply {
            hint = "Buscar canal..."
            textSize = 13f
            setSingleLine(true)
            setTextColor(Theme.white)
            setHintTextColor(Theme.textMuted)
            background = roundRect(Color.argb(20, 255, 255, 255), Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_SM), 0, dp(Theme.SPACING_SM), 0)
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, dp(34)).apply {
            leftMargin = dp(Theme.SPACING_MD)
            rightMargin = dp(Theme.SPACING_MD)
            bottomMargin = dp(8)
        })

        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
            addView(categoryRow)
        }
        panel.addView(categoryScroll, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChannelDetailsActivity)
        }
        gridAdapter = ChannelGridAdapter { chosen -> switchToChannel(chosen) }
        recycler.adapter = gridAdapter
        panel.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))

        gridOverlay.addView(panel, FrameLayout.LayoutParams(panelWidth, -1, Gravity.END))

        var selectedCategory: String? = null
        fun applyFilter() {
            val query = search.text?.toString()?.trim()?.lowercase().orEmpty()
            gridAdapter.submit(
                allChannels.filter { channel ->
                    (selectedCategory == null || channel.group == selectedCategory) &&
                        (query.isEmpty() || channel.name.lowercase().contains(query))
                }
            )
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyFilter()
            override fun afterTextChanged(s: Editable?) = Unit
        })

        fun addCategoryChip(label: String, group: String?) {
            categoryRow.addView(TextView(this).apply {
                setText(label)
                textSize = 10f
                setTypeface(Typeface.DEFAULT_BOLD)
                setPadding(dp(Theme.SPACING_SM), dp(6), dp(Theme.SPACING_SM), dp(6))
                fun refresh() {
                    val active = selectedCategory == group
                    setTextColor(if (active) Theme.accentCyan else Theme.textSecondary)
                    background = roundRect(
                        if (active) Color.argb(46, 76, 232, 240) else Color.argb(15, 255, 255, 255),
                        Theme.RADIUS_PILL
                    )
                }
                refresh()
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    selectedCategory = group
                    for (i in 0 until categoryRow.childCount) {
                        val chip = categoryRow.getChildAt(i) as TextView
                        val chipGroup = if (i == 0) null else chip.text.toString()
                        val active = selectedCategory == chipGroup
                        chip.setTextColor(if (active) Theme.accentCyan else Theme.textSecondary)
                        chip.background = roundRect(
                            if (active) Color.argb(46, 76, 232, 240) else Color.argb(15, 255, 255, 255),
                            Theme.RADIUS_PILL
                        )
                    }
                    applyFilter()
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) }
            })
        }
        addCategoryChip("Todos", null)

        lifecycleScope.launch {
            val items = CatalogRepository.load(this@ChannelDetailsActivity)
            allChannels = items.filter { it.kind == M3uItem.Kind.CHANNEL }
            allChannels.map { it.group }.distinct().sorted().forEach { addCategoryChip(it, it) }
            applyFilter()
        }

        return gridOverlay
    }

    private fun openChannelGrid() {
        gridOverlay.visibility = View.VISIBLE
    }

    private fun closeChannelGrid() {
        gridOverlay.visibility = View.GONE
    }

    /** Troca o canal tocando SEM sair desta tela — igual ao switchToChannel do original. */
    private fun switchToChannel(chosen: M3uItem) {
        closeChannelGrid()
        item = chosen
        channelNameText.setText(chosen.name)
        categoryText.setText(if (chosen.group.isNotBlank()) "Categoria ${chosen.group}" else "")
        refreshFavorite()
        startPlayback()
        epgList.removeAllViews()
        epgStatus.visibility = View.VISIBLE
        epgStatus.setText("Carregando programação...")
        loadEpg()
        WatchHistoryStore.record(this, chosen)
    }

    override fun onResume() {
        super.onResume()
        WatchHistoryStore.record(this, item)
        // Reanexa a MESMA instância — se a pessoa voltou da tela cheia,
        // o canal continua tocando exatamente de onde estava, sem
        // reiniciar. Só recria de verdade se a URL mudou por fora.
        if (item.url.isNotBlank()) {
            playerView.player = SharedChannelPlayer.playerFor(this, item.url)
        }
    }

    override fun onStop() {
        // Não pausa nem libera aqui: pode ser só uma ida rápida pra
        // tela cheia (PlayerActivity), e o canal deve continuar tocando
        // por trás pra voltar exatamente de onde estava.
        super.onStop()
    }

    override fun onDestroy() {
        // "Sair de vez" desta tela (não indo pra tela cheia) é o único
        // momento de liberar o player de verdade.
        if (isFinishing) SharedChannelPlayer.release()
        super.onDestroy()
    }
}

/** Linha simples (logo + nome) do painel de troca de canal — reaproveitado pela PlayerActivity. */
internal class ChannelGridAdapter(
    private val onClick: (M3uItem) -> Unit,
) : RecyclerView.Adapter<ChannelGridAdapter.Holder>() {

    private var items: List<M3uItem> = emptyList()

    fun submit(newItems: List<M3uItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(Theme.SPACING_MD), context.dp(8), context.dp(Theme.SPACING_MD), context.dp(8))
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = context.roundRect(Theme.white, 6)
            tag = "logo"
        }
        row.addView(logo, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        val name = TextView(context).apply {
            textSize = 13f
            setTextColor(Theme.white)
            maxLines = 1
            tag = "name"
        }
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = context.dp(10) })
        return Holder(row)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.setText(item.name)
        holder.logo.setImageBitmap(null)
        holder.logo.tag = item.logo
        item.logo?.takeIf { it.isNotBlank() }?.let { loadRecyclableImage(holder.logo, it) }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class Holder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        val logo = view.findViewWithTag<ImageView>("logo")
        val name = view.findViewWithTag<TextView>("name")
    }
}
