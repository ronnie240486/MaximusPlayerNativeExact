package com.interactiveplayer.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Catálogo, portado de `frontend/app/channels.tsx` (para Canais) e
 * `frontend/app/movies.tsx`/`series.tsx` (para Filmes/Séries/Kids).
 *
 * Canais usa lista numerada (como um guia de TV): número, logo pequeno,
 * nome e coração de favorito, igual ao `ChannelRow` do original. Filmes,
 * séries e kids usam grade de pôsteres, igual à Home.
 *
 * Fica de fora desta versão: o preview ao vivo do canal em destaque
 * (`TVChannelPreview`) que o original mostra ao lado da lista — pediria
 * manter um ExoPlayer rodando atrás da tela de navegação, o que é um
 * pedaço grande por si só.
 */
class CatalogActivity : ComponentActivity() {

    private companion object {
        const val FAVORITES = "Favoritos"
    }

    private var allItems: List<M3uItem> = emptyList()
    private var selectedGroup: String? = null
    private var mode: M3uItem.Kind = M3uItem.Kind.CHANNEL
    private var searchQuery: String = ""

    private lateinit var itemsHost: LinearLayout
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

        val itemScroll = ScrollView(this)
        itemsHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, dp(Theme.SPACING_MD), dp(Theme.SPACING_MD))
        }
        itemScroll.addView(itemsHost)
        content.addView(itemScroll, LinearLayout.LayoutParams(0, -1, 1f))

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
            hint = "Buscar em $modeTitleLower"
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

    private val modeTitleLower get() = modeTitle().lowercase()

    private fun loadPlaylist() {
        lifecycleScope.launch {
            allItems = CatalogRepository.load(this@CatalogActivity)
            if (allItems.isEmpty()) {
                status.setText("Nenhum item carregado ainda. Puxando o catálogo...")
                val fresh = CatalogRepository.load(this@CatalogActivity, force = true)
                allItems = fresh
            }
            if (allItems.isEmpty()) {
                status.setText("Nenhum item carregado. Confira sua lista nas configurações.")
                renderCategories(emptyList())
            } else {
                renderCategories(allItems.filterForMode(mode).map { it.group }.distinct().sorted())
                renderItems()
            }
        }
    }

    private fun renderCategories(groups: List<String>) {
        categoriesView.removeAllViews()
        categoriesView.addView(categoryChip("Todos", null))
        categoriesView.addView(categoryChip(FAVORITES, FAVORITES))
        groups.forEach { group -> categoriesView.addView(categoryChip(group, group)) }
    }

    private fun categoryChip(label: String, group: String?): View =
        TextView(this).apply {
            setText(label)
            textSize = 13f
            maxLines = 1
            setPadding(dp(Theme.SPACING_SM), dp(10), dp(Theme.SPACING_SM), dp(10))
            refreshChipColors(this, group)
            isFocusable = true
            isClickable = true
            setOnClickListener {
                selectedGroup = group
                refreshAllChips()
                renderItems()
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            }
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

        itemsHost.removeAllViews()
        if (filtered.isEmpty()) {
            itemsHost.addView(TextView(this).apply {
                setText("Nenhum conteúdo encontrado")
                textSize = 14f
                setTextColor(Theme.textMuted)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Theme.SPACING_LG) })
            return
        }

        if (mode == M3uItem.Kind.CHANNEL) {
            filtered.forEachIndexed { index, item -> itemsHost.addView(channelRow(item, index)) }
        } else {
            val columns = maxOf(3, (resources.displayMetrics.widthPixels * 0.7).toInt() / dp(posterWidth + Theme.SPACING_SM))
            var line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            filtered.forEachIndexed { index, item ->
                if (index % columns == 0) {
                    line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                    itemsHost.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(Theme.SPACING_SM)
                    })
                }
                line.addView(
                    posterCard(item),
                    LinearLayout.LayoutParams(dp(posterWidth), -2).apply { rightMargin = dp(Theme.SPACING_SM) }
                )
            }
        }
    }

    /** Fiel ao ChannelRow do original: número, logo, nome, coração. */
    private fun channelRow(item: M3uItem, index: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM))
            isFocusable = true
            isClickable = true
            setOnClickListener { openItem(item) }
        }
        row.addView(TextView(this).apply {
            setText((index + 1).toString())
            textSize = 12f
            setTextColor(Theme.textMuted)
        }, LinearLayout.LayoutParams(dp(28), -2))

        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            background = roundRect(Theme.white, 6)
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(32), dp(32)).apply {
            leftMargin = dp(Theme.SPACING_SM)
            rightMargin = dp(Theme.SPACING_SM)
        })

        row.addView(TextView(this).apply {
            setText(item.name)
            textSize = 13f
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f))

        val favorite = FavoriteStore.contains(this, item)
        row.addView(TextView(this).apply {
            setText(if (favorite) "♥" else "♡")
            textSize = 14f
            setTextColor(if (favorite) Theme.accentMagenta else Theme.textMuted)
        })

        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
        item.logo?.takeIf { it.isNotBlank() }?.let { loadImage(it, logo) }
        return row
    }

    /** Mesmo card de pôster da Home (Filmes/Séries/Kids). */
    private fun posterCard(item: M3uItem): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            setOnClickListener { openItem(item) }
        }
        val poster = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            clipToOutline = true
        }
        box.addView(poster, LinearLayout.LayoutParams(dp(posterWidth), dp(posterHeight)))
        box.addView(TextView(this).apply {
            setText(item.name)
            textSize = if (isTv) 15f else 12f
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        item.logo?.takeIf { it.isNotBlank() }?.let { loadImage(it, poster) }
        return box
    }

    private fun openItem(item: M3uItem) {
        WatchHistoryStore.record(this, item)
        if (item.kind == M3uItem.Kind.CHANNEL) {
            startActivity(Intent(this, ChannelDetailsActivity::class.java).apply {
                putExtra("name", item.name)
                putExtra("group", item.group)
                putExtra("logo", item.logo)
                putExtra("url", item.url)
            })
        } else {
            startActivity(Intent(this, ContentDetailsActivity::class.java).apply {
                putExtra("name", item.name)
                putExtra("group", item.group)
                putExtra("logo", item.logo)
                putExtra("url", item.url)
                putExtra("kind", item.kind.name)
            })
        }
    }

    private fun loadImage(url: String, target: ImageView) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 7000
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null && !isFinishing) target.setImageBitmap(bitmap)
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
