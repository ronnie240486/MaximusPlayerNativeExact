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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * Catálogo (Canais/Filmes/Séries/Kids), agora com RecyclerView.
 *
 * A versão anterior construía TODAS as linhas/cards como Views de uma
 * vez só, direto num LinearLayout dentro de ScrollView — com uma lista
 * de IPTV normalmente tendo milhares de canais, isso travava a thread
 * principal por vários segundos toda vez que a tela abria ou o filtro
 * mudava (o que também explicava o menu lateral "lento": ele não era
 * lento de verdade, só ficava esperando essa reconstrução gigante
 * terminar). RecyclerView só cria e liga as views que cabem na tela.
 */
class CatalogActivity : ComponentActivity() {

    private companion object {
        const val FAVORITES = "Favoritos"
    }

    private var allItems: List<M3uItem> = emptyList()
    private var selectedGroup: String? = null
    private var mode: M3uItem.Kind = M3uItem.Kind.CHANNEL
    private var searchQuery: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CatalogAdapter
    private lateinit var categoriesView: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText

    private val isTv: Boolean by lazy { DeviceType.isTV(this) }
    private val posterWidth get() = if (isTv) 160 else 130
    private val posterHeight get() = posterWidth * 130 / 90

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = runCatching {
            M3uItem.Kind.valueOf(intent.getStringExtra("mode") ?: "CHANNEL")
        }.getOrDefault(M3uItem.Kind.CHANNEL)
        setContentView(buildScreen())
        if (intent.getBooleanExtra("focusSearch", false)) search.requestFocus()
        loadPlaylist()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
        }
        root.addView(buildHeader())

        status = TextView(this).apply {
            setText("Carregando catálogo...")
            textSize = 12f
            setTextColor(Theme.textSecondary)
            isFocusable = true
            wireFocusHighlight()
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(Theme.SPACING_MD)
            rightMargin = dp(Theme.SPACING_MD)
            bottomMargin = dp(Theme.SPACING_SM)
        })

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val categoryScroll = ScrollView(this)
        categoriesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_SM), dp(Theme.SPACING_MD))
        }
        categoryScroll.addView(categoriesView)
        content.addView(categoryScroll, LinearLayout.LayoutParams(dp(if (isTv) 200 else 150), -1))

        adapter = CatalogAdapter(
            isChannelMode = mode == M3uItem.Kind.CHANNEL,
            posterWidthPx = dp(posterWidth),
            posterHeightPx = dp(posterHeight),
            posterNameSizeSp = if (isTv) 15f else 12f,
            onClick = { item -> openItem(item) },
            onToggleFavorite = { item ->
                val nowFavorite = FavoriteStore.toggle(this, item)
                if (!nowFavorite && selectedGroup == FAVORITES) renderItems()
                nowFavorite
            },
        )
        recyclerView = RecyclerView(this).apply {
            layoutManager = if (mode == M3uItem.Kind.CHANNEL) {
                LinearLayoutManager(this@CatalogActivity)
            } else {
                val columns = maxOf(
                    3,
                    (resources.displayMetrics.widthPixels * 0.72).toInt() / dp(posterWidth + Theme.SPACING_SM)
                )
                GridLayoutManager(this@CatalogActivity, columns)
            }
            adapter = this@CatalogActivity.adapter
            setPadding(dp(Theme.SPACING_MD), dp(Theme.SPACING_SM), dp(Theme.SPACING_MD), dp(Theme.SPACING_MD))
            clipToPadding = false
            setHasFixedSize(true)
        }
        content.addView(recyclerView, LinearLayout.LayoutParams(0, -1, 1f))

        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_SM)
            )
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
            setText(modeTitle())
            textSize = 18f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(-2, -2).apply {
            leftMargin = dp(Theme.SPACING_SM)
            rightMargin = dp(Theme.SPACING_MD)
        })
        search = EditText(this).apply {
            hint = "Buscar em ${modeTitle().lowercase()}"
            textSize = 13f
            setSingleLine(true)
            setTextColor(Theme.white)
            setHintTextColor(Theme.textMuted)
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_MD)
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                    renderItems()
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(search, LinearLayout.LayoutParams(0, dp(40), 1f))
        return header
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            var items = CatalogRepository.load(this@CatalogActivity)
            if (items.isEmpty()) {
                status.setText("Nenhum item carregado ainda. Puxando o catálogo...")
                items = CatalogRepository.load(this@CatalogActivity, force = true)
            }
            // Perfil infantil nunca vê conteúdo adulto, nem a categoria.
            allItems = if (ActiveProfileStore.isKidsActive(this@CatalogActivity)) {
                items.filterNot { AdultContent.isAdultGroup(it.group) }
            } else {
                items
            }
            if (allItems.isEmpty()) {
                status.setText("Nenhum item carregado. Confira sua lista nas configurações.")
                renderCategories(emptyList())
            } else {
                val groups = allItems.filterForMode(mode).map { it.group }.distinct().sorted()
                val (normal, adult) = groups.partition { !AdultContent.isAdultGroup(it) }
                renderCategories(normal + adult)
                renderItems()
            }
        }
    }

    private fun renderCategories(groups: List<String>) {
        categoriesView.removeAllViews()
        categoriesView.addView(categoryChip("Todos", null))
        categoriesView.addView(categoryChip(FAVORITES, FAVORITES))
        groups.forEach { group -> categoriesView.addView(categoryChip(group, group, locked = AdultContent.isAdultGroup(group))) }
    }

    private fun categoryChip(label: String, group: String?, locked: Boolean = false): View =
        TextView(this).apply {
            setText(if (locked) "🔒 $label" else label)
            textSize = 13f
            maxLines = 2
            setPadding(dp(Theme.SPACING_SM), dp(10), dp(Theme.SPACING_SM), dp(10))
            refreshChipColors(this, group)
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener {
                if (locked) {
                    requirePin {
                        selectedGroup = group
                        refreshAllChips()
                        renderItems()
                    }
                } else {
                    selectedGroup = group
                    refreshAllChips()
                    renderItems()
                }
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            }
        }

    /** Sem PIN nenhum configurado ainda — cria um agora, na hora, antes de liberar qualquer coisa. */
    private fun promptCreatePin(onCreated: () -> Unit) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Crie um PIN de 4 dígitos"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Proteger conteúdo adulto")
            .setMessage("Ainda não existe um PIN — crie um agora pra continuar.")
            .setView(input)
            .setPositiveButton("Criar e continuar") { _, _ ->
                val pin = input.text?.toString().orEmpty()
                if (pin.length >= 4) {
                    AppPreferences.setParentalPin(this, pin)
                    onCreated()
                } else {
                    android.widget.Toast.makeText(this, "O PIN precisa ter pelo menos 4 dígitos.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Pede o PIN parental antes de liberar conteúdo adulto — nunca libera sem PIN nenhum. */
    private fun requirePin(onCorrect: () -> Unit) {
        val pin = AppPreferences.parentalPin(this)
        if (pin.isNullOrBlank()) {
            promptCreatePin { onCorrect() }
            return
        }
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Conteúdo adulto")
            .setMessage("Digite o PIN parental pra continuar.")
            .setView(input)
            .setPositiveButton("Entrar") { _, _ ->
                if (input.text?.toString() == pin) {
                    onCorrect()
                } else {
                    android.widget.Toast.makeText(this, "PIN incorreto.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun refreshChipColors(chip: TextView, group: String?) {
        val active = selectedGroup == group
        chip.setTextColor(if (active) Theme.accentCyan else Theme.white)
        chip.background = roundRect(if (active) Theme.darkSurfaceAlt else Color.TRANSPARENT, Theme.RADIUS_SM)
    }

    private fun refreshAllChips() {
        for (index in 0 until categoriesView.childCount) {
            val chip = categoriesView.getChildAt(index) as TextView
            val group = when (index) {
                0 -> null
                1 -> FAVORITES
                else -> chip.text.toString()
            }
            refreshChipColors(chip, group)
        }
    }

    private fun renderItems() {
        val base = allItems.filterForMode(mode)
        val filtered = when (selectedGroup) {
            null -> base
            FAVORITES -> base.filter { FavoriteStore.contains(this, it) }
            else -> base.filter { it.group == selectedGroup }
        }.filter { searchQuery.isEmpty() || it.name.lowercase().contains(searchQuery) }

        // Catálogo carregou (tem canal, por exemplo), mas ESSA aba
        // especificamente veio vazia — geralmente porque só a chamada
        // dessa categoria engasgou no painel. Sem isso, a única saída
        // era sair da tela e voltar por outro caminho.
        if (base.isEmpty() && allItems.isNotEmpty() && selectedGroup == null && searchQuery.isEmpty()) {
            status.setText("${modeTitle()} veio vazio — toque aqui pra tentar de novo")
            status.setOnClickListener {
                status.setOnClickListener(null)
                status.setText("Tentando de novo...")
                lifecycleScope.launch {
                    allItems = CatalogRepository.load(this@CatalogActivity, force = true)
                    renderCategories(allItems.filterForMode(mode).map { it.group }.distinct().sorted())
                    renderItems()
                }
            }
            adapter.submit(emptyList())
            return
        }
        status.setOnClickListener(null)

        status.setText(
            if (filtered.isEmpty()) "Nenhum conteúdo encontrado"
            else "${filtered.size} ${if (mode == M3uItem.Kind.CHANNEL) "canais" else "títulos"}"
        )
        adapter.submit(filtered)
    }

    override fun onDestroy() {
        UiSound.release()
        super.onDestroy()
    }

    private fun openItem(item: M3uItem) {
        WatchHistoryStore.record(this, item)
        if (item.kind == M3uItem.Kind.CHANNEL) {
            startActivity(Intent(this, ChannelDetailsActivity::class.java).apply {
                putExtra("name", item.name)
                putExtra("group", item.group)
                putExtra("logo", item.logo)
                putExtra("url", item.url)
                putExtra("streamId", item.streamId)
            })
        } else {
            startActivity(Intent(this, ContentDetailsActivity::class.java).apply {
                putExtra("name", item.name)
                putExtra("group", item.group)
                putExtra("logo", item.logo)
                putExtra("url", item.url)
                putExtra("kind", item.kind.name)
                putExtra("streamId", item.streamId)
            })
        }
    }

    private fun modeTitle(): String = when (mode) {
        M3uItem.Kind.CHANNEL -> "Canais"
        M3uItem.Kind.MOVIE -> "Filmes"
        M3uItem.Kind.SERIES -> "Séries"
        M3uItem.Kind.KIDS -> "Kids"
    }

    private fun List<M3uItem>.filterForMode(kind: M3uItem.Kind) = filter { it.kind == kind }
}

/**
 * Adapter único pros dois formatos de item: linha numerada (canal) e
 * card de pôster (filme/série/kids) — o `isChannelMode` decide qual.
 */
private class CatalogAdapter(
    private val isChannelMode: Boolean,
    private val posterWidthPx: Int,
    private val posterHeightPx: Int,
    private val posterNameSizeSp: Float,
    private val onClick: (M3uItem) -> Unit,
    private val onToggleFavorite: (M3uItem) -> Boolean,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<M3uItem> = emptyList()

    fun submit(newItems: List<M3uItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val context = parent.context
        return if (isChannelMode) {
            ChannelRowHolder(buildChannelRow(context))
        } else {
            PosterHolder(buildPosterCard(context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ChannelRowHolder -> holder.bind(item, position, onClick, onToggleFavorite)
            is PosterHolder -> holder.bind(item, onClick)
        }
    }

    // -----------------------------------------------------------------

    private fun buildChannelRow(context: android.content.Context): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = context.roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            setPadding(context.dp(Theme.SPACING_SM), context.dp(Theme.SPACING_SM), context.dp(Theme.SPACING_SM), context.dp(Theme.SPACING_SM))
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = context.dp(6) }
        }
        val number = TextView(context).apply {
            textSize = 12f
            setTextColor(Theme.textMuted)
            tag = "number"
        }
        row.addView(number, LinearLayout.LayoutParams(context.dp(28), -2))

        val logo = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = context.roundRect(Theme.white, 6)
            tag = "logo"
        }
        row.addView(logo, LinearLayout.LayoutParams(context.dp(32), context.dp(32)).apply {
            leftMargin = context.dp(Theme.SPACING_SM)
            rightMargin = context.dp(Theme.SPACING_SM)
        })

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            tag = "texts"
        }
        val name = TextView(context).apply {
            textSize = 13f
            setTextColor(Theme.white)
            maxLines = 1
            tag = "name"
        }
        texts.addView(name)
        val epgNow = TextView(context).apply {
            textSize = 10f
            setTextColor(Theme.textMuted)
            maxLines = 1
            tag = "epgNow"
            visibility = View.GONE
        }
        texts.addView(epgNow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(2) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        val heart = TextView(context).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            tag = "heart"
        }
        row.addView(heart, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        return row
    }

    private fun buildPosterCard(context: android.content.Context): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            layoutParams = RecyclerView.LayoutParams(posterWidthPx, -2).apply {
                (this as? ViewGroup.MarginLayoutParams)?.let {
                    it.rightMargin = context.dp(Theme.SPACING_SM)
                    it.bottomMargin = context.dp(Theme.SPACING_SM)
                }
            }
        }
        val poster = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = context.roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            clipToOutline = true
            tag = "poster"
        }
        box.addView(poster, LinearLayout.LayoutParams(posterWidthPx, posterHeightPx))
        box.addView(TextView(context).apply {
            textSize = posterNameSizeSp
            setTextColor(Theme.white)
            maxLines = 1
            tag = "name"
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
        return box
    }

    private class ChannelRowHolder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        private val number = view.findViewWithTag<TextView>("number")
        private val logo = view.findViewWithTag<ImageView>("logo")
        private val name = view.findViewWithTag<TextView>("name")
        private val epgNow = view.findViewWithTag<TextView>("epgNow")
        private val heart = view.findViewWithTag<TextView>("heart")

        fun bind(item: M3uItem, position: Int, onClick: (M3uItem) -> Unit, onToggleFavorite: (M3uItem) -> Boolean) {
            number.setText((position + 1).toString())
            name.setText(item.name)

            // "Agora" embaixo do nome — só busca pra quem está
            // realmente visível na tela (o RecyclerView só chama bind()
            // pras linhas visíveis), então não trava com milhares de
            // canais na lista.
            epgNow.visibility = View.GONE
            epgNow.tag = item.streamId
            item.streamId?.let { streamId ->
                loadNowPlaying(epgNow, itemView.context, item, streamId)
            }

            // Marca qual URL este ImageView está tentando carregar agora,
            // pra descartar o resultado se a view for reciclada antes da
            // imagem chegar (senão o logo do canal errado aparece).
            logo.setImageBitmap(null)
            logo.tag = item.logo
            item.logo?.takeIf { it.isNotBlank() }?.let { url ->
                loadRecyclableImage(logo, url)
            }

            fun refreshHeart(active: Boolean) {
                heart.setText(if (active) "♥" else "♡")
                heart.setTextColor(if (active) Theme.accentMagenta else Theme.textMuted)
            }
            refreshHeart(FavoriteStore.contains(itemView.context, item))
            heart.setOnClickListener { refreshHeart(onToggleFavorite(item)) }
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private class PosterHolder(view: LinearLayout) : RecyclerView.ViewHolder(view) {
        private val poster = view.findViewWithTag<ImageView>("poster")
        private val name = view.findViewWithTag<TextView>("name")

        fun bind(item: M3uItem, onClick: (M3uItem) -> Unit) {
            name.setText(item.name)
            poster.setImageBitmap(null)
            poster.tag = item.logo
            item.logo?.takeIf { it.isNotBlank() }?.let { url -> loadRecyclableImage(poster, url) }
            itemView.setOnClickListener { onClick(item) }
        }
    }
}


