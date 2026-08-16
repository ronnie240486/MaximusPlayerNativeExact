package com.maximus.nativeexact

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class CatalogActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val panel = Color.rgb(28, 40, 70)
    private var allItems: List<M3uItem> = emptyList()
    private var selectedGroup: String? = null
    private lateinit var grid: LinearLayout
    private lateinit var categoriesView: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var mode: M3uItem.Kind = M3uItem.Kind.CHANNEL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = runCatching { M3uItem.Kind.valueOf(intent.getStringExtra("mode") ?: "CHANNEL") }.getOrDefault(M3uItem.Kind.CHANNEL)
        setContentView(buildCatalog())
        if (intent.getBooleanExtra("focusSearch", false)) search.requestFocus()
        loadPlaylist()
    }

    private fun buildCatalog(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(22), dp(18), dp(22), dp(22))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹  ${modeTitle()}"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(300), dp(64)))
        search = EditText(this).apply {
            hint = "Buscar neste catálogo"
            textSize = 18f
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
            setOnEditorActionListener { _, _, _ -> renderItems(); false }
        }
        header.addView(search, LinearLayout.LayoutParams(0, dp(58), 1f))
        header.addView(TextView(this).apply {
            text = "VOLTAR"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(cyan)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(150), dp(58)))
        root.addView(header)
        status = TextView(this).apply { text = "Carregando catálogo completo..."; textSize = 16f; setTextColor(muted) }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(42)))

        val content = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val categoryScroll = ScrollView(this)
        categoriesView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), dp(16), 0) }
        categoryScroll.addView(categoriesView)
        content.addView(categoryScroll, LinearLayout.LayoutParams(dp(260), 0, 1f))

        val itemScroll = ScrollView(this)
        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), 0, dp(16)) }
        itemScroll.addView(grid)
        content.addView(itemScroll, LinearLayout.LayoutParams(0, -1, 3f))
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            allItems = CatalogRepository.load(this@CatalogActivity)
            if (allItems.isEmpty()) {
                status.text = "Nenhum item carregado. Configure a lista M3U nas configurações."
                renderCategories(emptyList())
            } else {
                status.text = "${allItems.size} itens carregados"
                renderCategories(allItems.filterForMode(mode).map { it.group }.distinct().sorted())
                renderItems()
            }
        }
    }

    private fun renderCategories(groups: List<String>) {
        if (!::categoriesView.isInitialized) return
        val root = categoriesView
        root.removeAllViews()
        addCategory(root, "Todos", null)
        groups.forEach { addCategory(root, it, it) }
    }

    private fun addCategory(root: LinearLayout, label: String, group: String?) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(if (selectedGroup == group) cyan else white)
            setBackgroundColor(if (selectedGroup == group) Color.rgb(36, 57, 92) else panel)
            setPadding(dp(16), dp(13), dp(12), dp(13))
            isFocusable = true
            setOnClickListener { selectedGroup = group; renderCategories(root.childrenLabels()); renderItems() }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(0, 0, 0, dp(7)) })
    }

    private fun renderItems() {
        if (!::grid.isInitialized) return
        val query = search.text?.toString()?.trim()?.lowercase().orEmpty()
        val items = allItems.filterForMode(mode).filter { selectedGroup == null || it.group == selectedGroup }.filter { query.isEmpty() || it.name.lowercase().contains(query) }
        grid.removeAllViews()
        if (items.isEmpty()) {
            grid.addView(TextView(this).apply { text = "Nenhum conteúdo encontrado"; textSize = 18f; setTextColor(muted) })
            return
        }
        items.chunked(5).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { item ->
                val card = LinearLayout(this@CatalogActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(panel)
                    isFocusable = true
                    setOnClickListener {
                        startActivity(Intent(this@CatalogActivity, PlayerActivity::class.java).putExtra("url", item.url))
                    }
                }
                val poster = ImageView(this@CatalogActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.rgb(35, 48, 80))
                    contentDescription = item.name
                }
                card.addView(poster, LinearLayout.LayoutParams(-1, dp(112)))
                card.addView(TextView(this@CatalogActivity).apply {
                    text = item.name
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                    maxLines = 2
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                }, LinearLayout.LayoutParams(-1, dp(48)))
                val favorite = TextView(this@CatalogActivity).apply {
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(4))
                    isFocusable = true
                    setOnFocusChangeListener { view, focused ->
                        (view as TextView).setTextColor(if (focused) cyan else white)
                    }
                    fun refresh() { text = if (FavoriteStore.contains(this@CatalogActivity, item)) "♥ Favorito" else "♡ Favoritar" }
                    refresh()
                    setOnClickListener {
                        FavoriteStore.toggle(this@CatalogActivity, item)
                        refresh()
                    }
                }
                card.addView(favorite, LinearLayout.LayoutParams(-1, dp(28)))
                row.addView(card, LinearLayout.LayoutParams(0, dp(194), 1f).apply { setMargins(0, 0, dp(8), dp(10)) })
                item.logo?.takeIf { it.isNotBlank() }?.let { logo -> loadPoster(logo, poster) }
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, dp(204)))
        }
    }

    private fun loadPoster(url: String, target: ImageView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 7000
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bitmap != null) withContext(Dispatchers.Main) { target.setImageBitmap(bitmap) }
        }
    }

    private fun modeTitle(): String = when (mode) {
        M3uItem.Kind.CHANNEL -> "Canais"
        M3uItem.Kind.MOVIE -> "Filmes"
        M3uItem.Kind.SERIES -> "Séries"
        M3uItem.Kind.KIDS -> "Kids"
    }

    private fun List<M3uItem>.filterForMode(kind: M3uItem.Kind) = filter { it.kind == kind || (kind == M3uItem.Kind.KIDS && it.kind == M3uItem.Kind.KIDS) }
    private fun LinearLayout.childrenLabels(): List<String> = (0 until childCount).map { (getChildAt(it) as TextView).text.toString() }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
