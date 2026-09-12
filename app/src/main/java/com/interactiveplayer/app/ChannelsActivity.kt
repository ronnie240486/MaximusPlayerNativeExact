package com.interactiveplayer.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Canais numa tela só — categorias, lista e preview ao vivo com EPG
 * lado a lado, ao gosto de referências como o OuroPro Player: navegar
 * pela lista já mostra o preview+EPG do canal em foco; apertar OK de
 * novo em cima do canal que já está em preview abre o player em tela
 * cheia. Antes isso eram três telas separadas (lista, caixinha de
 * detalhes, tela cheia) — aqui as duas primeiras viram uma só.
 */
class ChannelsActivity : ComponentActivity() {

    private companion object {
        const val FAVORITES = "Favoritos"
    }

    private var allChannels: List<M3uItem> = emptyList()
    private var selectedGroup: String? = null
    private var previewItem: M3uItem? = null
    private var searchQuery: String = ""
    private var pendingFocusUrl: String? = null
    private val isTv: Boolean by lazy { DeviceType.isTV(this) }

    private lateinit var search: EditText
    private lateinit var categoriesView: LinearLayout
    private lateinit var channelRecycler: RecyclerView
    private lateinit var channelAdapter: ChannelListAdapter
    private lateinit var status: TextView

    private lateinit var previewPlayerView: PlayerView
    private lateinit var previewName: TextView
    private lateinit var previewFavorite: TextView
    private lateinit var epgList: LinearLayout
    private lateinit var epgStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingFocusUrl = intent.getStringExtra("focusUrl")
        setContentView(buildScreen())
        loadChannels()
    }

    // -----------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
            // Margem de segurança do overscan: em TV de verdade, a
            // primeira barra ficava cortada na borda da tela — TVs
            // recortam uma faixa das bordas por padrão.
            val safe = dpTV(28, 0)
            setPadding(safe, safe, safe, safe)
        }
        root.addView(buildHeader())

        status = TextView(this).apply {
            setText("Carregando canais...")
            textSize = spTV(15f, 12f)
            setTextColor(Theme.textSecondary)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(Theme.SPACING_MD)
            rightMargin = dp(Theme.SPACING_MD)
            bottomMargin = dp(Theme.SPACING_SM)
        })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(buildCategoriesColumn(), LinearLayout.LayoutParams(dpTV(230, 170), -1))
        body.addView(buildChannelListColumn(), LinearLayout.LayoutParams(dpTV(360, 280), -1).apply {
            leftMargin = dp(Theme.SPACING_SM)
        })
        body.addView(buildPreviewColumn(), LinearLayout.LayoutParams(0, -1, 1f).apply {
            leftMargin = dp(Theme.SPACING_MD)
        })
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.SPACING_MD), dp(Theme.SPACING_MD), dp(Theme.SPACING_MD), dp(Theme.SPACING_SM))
        }
        header.addView(TextView(this).apply {
            setText("‹")
            textSize = 24f
            setTextColor(Theme.white)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText("Canais")
            textSize = 18f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(Theme.SPACING_SM)
            rightMargin = dp(Theme.SPACING_MD)
        })
        search = EditText(this).apply {
            hint = "Buscar canal..."
            textSize = spTV(16f, 13f)
            setSingleLine(true)
            setTextColor(Theme.white)
            setHintTextColor(Theme.textMuted)
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_MD)
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                    renderChannelList()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(search, LinearLayout.LayoutParams(0, dp(40), 1f))
        return header
    }

    private fun buildCategoriesColumn(): View {
        val scroll = ScrollView(this)
        categoriesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_SM), dp(Theme.SPACING_MD))
        }
        scroll.addView(categoriesView)
        return scroll
    }

    private fun buildChannelListColumn(): View {
        channelRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChannelsActivity)
            setPadding(0, 0, dp(Theme.SPACING_SM), dp(Theme.SPACING_MD))
            clipToPadding = false
        }
        channelAdapter = ChannelListAdapter(
            isPreviewed = { item -> previewItem?.url == item.url },
            onFocusOrSelect = { item -> setPreview(item) },
            onActivate = { item ->
                // OK/clique no canal SÓ troca o que está tocando no
                // preview — nunca pula pra tela cheia sozinho. Com
                // D-pad, mover o foco já marca o canal como "em
                // preview" antes mesmo do OK chegar (o foco sempre vem
                // primeiro), então um "segundo clique" nunca existia de
                // verdade: já abria a tela cheia direto no primeiro OK,
                // sem deixar continuar navegando a lista. Tela cheia
                // agora só pelo vídeo/ícone de expandir.
                setPreview(item)
            },
            onToggleFavorite = { item -> FavoriteStore.toggle(this, item) },
            isFavorite = { item -> FavoriteStore.contains(this, item) },
        )
        channelRecycler.adapter = channelAdapter
        return channelRecycler
    }

    private fun buildPreviewColumn(): View {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val videoBox = FrameLayout(this).apply {
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            clipToOutline = true
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener { previewItem?.let { openFullscreen(it) } }
        }
        previewPlayerView = PlayerView(this).apply {
            useController = false
            // ZOOM em vez do FIT padrão: o vídeo preenche a caixa
            // inteira (cortando um pouco as bordas se precisar) em vez
            // de sobrar fundo vazio (as "partes azuis do lado") quando
            // a proporção da caixa não bate exatamente com a do vídeo.
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        videoBox.addView(previewPlayerView, FrameLayout.LayoutParams(-1, -1))
        videoBox.addView(
            TextView(this).apply {
                setText("⛶")
                textSize = 16f
                setTextColor(Theme.white)
                gravity = Gravity.CENTER
                background = circleDrawable(Color.argb(150, 11, 15, 26))
            },
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(Theme.SPACING_SM)
                rightMargin = dp(Theme.SPACING_SM)
            }
        )
        column.addView(videoBox, LinearLayout.LayoutParams(-1, 0, 0.55f))

        val infoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        previewName = TextView(this).apply {
            setText("Selecione um canal")
            textSize = spTV(22f, 16f)
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        }
        infoRow.addView(previewName, LinearLayout.LayoutParams(0, -2, 1f))
        previewFavorite = TextView(this).apply {
            setText("♡")
            textSize = 18f
            setTextColor(Theme.textSecondary)
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener {
                previewItem?.let { item ->
                    val nowFavorite = FavoriteStore.toggle(this@ChannelsActivity, item)
                    setText(if (nowFavorite) "♥" else "♡")
                    setTextColor(if (nowFavorite) Theme.accentMagenta else Theme.textSecondary)
                    channelAdapter.notifyDataSetChanged()
                }
            }
        }
        infoRow.addView(previewFavorite)
        column.addView(infoRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_SM)
            bottomMargin = dp(6)
        })

        epgStatus = TextView(this).apply {
            setText("Navegue pela lista pra ver a programação.")
            textSize = spTV(15f, 12f)
            setTextColor(Theme.textMuted)
        }
        column.addView(epgStatus, LinearLayout.LayoutParams(-1, -2))

        val epgScroll = ScrollView(this)
        epgList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        epgScroll.addView(epgList)
        column.addView(epgScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        return column
    }

    // -----------------------------------------------------------------
    // Dados
    // -----------------------------------------------------------------

    private fun loadChannels() {
        lifecycleScope.launch {
            var items = CatalogRepository.load(this@ChannelsActivity).filter { it.kind == M3uItem.Kind.CHANNEL }
            if (items.isEmpty()) {
                status.setText("Carregando catálogo pela primeira vez...")
                items = CatalogRepository.load(this@ChannelsActivity, force = true).filter { it.kind == M3uItem.Kind.CHANNEL }
            }
            allChannels = items
            if (allChannels.isEmpty()) {
                status.setText("Nenhum canal carregado. Confira sua lista nas configurações.")
                return@launch
            }
            status.setText("${allChannels.size} canais")
            renderCategories()
            renderChannelList()
        }
    }

    private fun renderCategories() {
        categoriesView.removeAllViews()
        categoriesView.addView(categoryRow("Todos", null, allChannels.size))
        val favoritesCount = allChannels.count { FavoriteStore.contains(this, it) }
        categoriesView.addView(categoryRow(FAVORITES, FAVORITES, favoritesCount))
        allChannels.groupingBy { it.group }.eachCount().toSortedMap().forEach { (group, count) ->
            categoriesView.addView(categoryRow(group, group, count))
        }
    }

    private fun categoryRow(label: String, group: String?, count: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setPadding(dp(Theme.SPACING_SM), dp(10), dp(Theme.SPACING_SM), dp(10))
        }
        val label1 = TextView(this).apply {
            setText(label)
            textSize = spTV(16f, 13f)
            maxLines = 2
            setTextColor(if (selectedGroup == group) Theme.accentCyan else Theme.white)
        }
        row.addView(label1, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            setText(count.toString())
            textSize = spTV(13f, 11f)
            setTextColor(Theme.textMuted)
        })
        row.setOnClickListener {
            selectedGroup = group
            renderCategories()
            renderChannelList()
        }
        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
        return row
    }

    private fun renderChannelList() {
        val base = when (selectedGroup) {
            null -> allChannels
            FAVORITES -> allChannels.filter { FavoriteStore.contains(this, it) }
            else -> allChannels.filter { it.group == selectedGroup }
        }
        val filtered = base.filter { searchQuery.isEmpty() || it.name.lowercase().contains(searchQuery) }
        channelAdapter.submit(filtered)
        val target = pendingFocusUrl?.let { url -> filtered.firstOrNull { it.url == url } }
        if (target != null) {
            pendingFocusUrl = null
            setPreview(target)
        } else if (filtered.isNotEmpty() && previewItem == null) {
            setPreview(filtered.first())
        }
    }

    // -----------------------------------------------------------------
    // Preview (mini player + EPG) — usa o MESMO SharedChannelPlayer que
    // a tela cheia, então abrir em tela cheia depois não reinicia nada.
    // -----------------------------------------------------------------

    private fun setPreview(item: M3uItem) {
        val changed = previewItem?.url != item.url
        previewItem = item
        if (!changed) return

        channelAdapter.notifyDataSetChanged()
        previewName.setText(item.name)
        val favorite = FavoriteStore.contains(this, item)
        previewFavorite.setText(if (favorite) "♥" else "♡")
        previewFavorite.setTextColor(if (favorite) Theme.accentMagenta else Theme.textSecondary)

        if (item.url.isNotBlank()) {
            SharedChannelPlayer.currentItem = item
            previewPlayerView.player = SharedChannelPlayer.playerFor(this, item.url)
        }

        loadEpgFor(item)
    }

    private fun loadEpgFor(item: M3uItem) {
        epgList.removeAllViews()
        epgStatus.visibility = View.VISIBLE
        epgStatus.setText("Carregando programação...")
        val requestedFor = item.url
        lifecycleScope.launch {
            val programs = withContext(Dispatchers.IO) { EpgClient.fetchSchedule(this@ChannelsActivity, item) }
            if (previewItem?.url != requestedFor) return@launch
            if (programs.isEmpty()) {
                epgStatus.setText("Sem informações de programação para este canal.")
                return@launch
            }
            epgStatus.visibility = View.GONE
            programs.forEach { program -> epgList.addView(buildEpgRow(program)) }
        }
    }

    private fun buildEpgRow(program: EpgClient.Program): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(TextView(this).apply {
            setText("${program.startLabel} ~ ${program.endLabel}")
            textSize = spTV(16f, 12f)
            setTextColor(if (program.isNow) Theme.accentCyan else Theme.textMuted)
            setTypeface(if (program.isNow) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
        }, LinearLayout.LayoutParams(dp(if (isTv) 190 else 150), -2))
        row.addView(TextView(this).apply {
            setText(program.title)
            textSize = spTV(17f, 12f)
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun openFullscreen(item: M3uItem) {
        WatchHistoryStore.record(this, item)
        // Solta a PlayerView desta tela antes de ir — sem isso, ao
        // voltar, o Media3 acha que ela "já tem" o player atribuído e
        // não reatribui de verdade (tela preta).
        previewPlayerView.player = null
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("title", item.name)
                .putExtra("logo", item.logo)
                .putExtra("isLive", true)
                .putExtra("streamId", item.streamId)
        )
    }

    override fun onResume() {
        super.onResume()
        // Se trocou de canal na tela cheia, reflete aqui ao voltar.
        SharedChannelPlayer.currentItem?.let { latest ->
            if (latest.url != previewItem?.url) setPreview(latest)
        }
        previewItem?.url?.takeIf { it.isNotBlank() }?.let { url ->
            previewPlayerView.player = SharedChannelPlayer.playerFor(this, url)
        }
    }

    override fun onDestroy() {
        if (isFinishing) SharedChannelPlayer.release()
        super.onDestroy()
    }
}

/** Linha da lista central: número, logo, nome e coração. */
private class ChannelListAdapter(
    private val isPreviewed: (M3uItem) -> Boolean,
    private val onFocusOrSelect: (M3uItem) -> Unit,
    private val onActivate: (M3uItem) -> Unit,
    private val onToggleFavorite: (M3uItem) -> Boolean,
    private val isFavorite: (M3uItem) -> Boolean,
) : RecyclerView.Adapter<ChannelListAdapter.Holder>() {

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
            setPadding(context.dp(Theme.SPACING_SM), context.dp(8), context.dp(Theme.SPACING_SM), context.dp(8))
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        val number = TextView(context).apply {
            textSize = context.spTV(13f, 11f)
            setTextColor(Theme.textMuted)
            tag = "number"
        }
        row.addView(number, LinearLayout.LayoutParams(context.dp(30), -2))
        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = context.roundRect(Theme.white, 6)
            tag = "logo"
        }
        row.addView(logo, LinearLayout.LayoutParams(context.dp(36), context.dp(36)).apply {
            leftMargin = context.dp(6)
            rightMargin = context.dp(10)
        })
        val name = TextView(context).apply {
            textSize = context.spTV(15f, 12f)
            setTextColor(Theme.white)
            maxLines = 1
            tag = "name"
        }
        row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        val heart = TextView(context).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            tag = "heart"
        }
        row.addView(heart, LinearLayout.LayoutParams(context.dp(34), context.dp(34)))
        return Holder(row)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.number.setText((position + 1).toString())
        holder.name.setText(item.name)
        holder.row.setBackgroundColor(if (isPreviewed(item)) Color.argb(40, 76, 232, 240) else Color.TRANSPARENT)

        holder.logo.setImageBitmap(null)
        holder.logo.tag = item.logo
        item.logo?.takeIf { it.isNotBlank() }?.let { url -> loadRecyclableImage(holder.logo, url) }

        fun refreshHeart() {
            val active = isFavorite(item)
            holder.heart.setText(if (active) "♥" else "♡")
            holder.heart.setTextColor(if (active) Theme.accentMagenta else Theme.textMuted)
        }
        refreshHeart()
        holder.heart.setOnClickListener {
            onToggleFavorite(item)
            refreshHeart()
        }

        holder.row.setOnClickListener { onActivate(item) }
        holder.row.setOnFocusChangeListener { _, focused -> if (focused) onFocusOrSelect(item) }
    }

    class Holder(val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        val number = row.findViewWithTag<TextView>("number")
        val logo = row.findViewWithTag<ImageView>("logo")
        val name = row.findViewWithTag<TextView>("name")
        val heart = row.findViewWithTag<TextView>("heart")
    }
}
