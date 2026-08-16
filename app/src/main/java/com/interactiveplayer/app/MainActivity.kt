package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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

class MainActivity : ComponentActivity() {
    private val backgroundColor = Color.rgb(8, 16, 30)
    private val surface = Color.rgb(21, 31, 55)
    private val cyan = Color.rgb(53, 222, 231)
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(170, 179, 198)
    private lateinit var homeBody: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildHome())
        lifecycleScope.launch {
            val items = CatalogRepository.load(this@MainActivity)
            renderCatalogHome(items)
        }
    }

    private fun buildHome(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(backgroundColor)
            isFocusable = true
        }
        root.addView(buildSidebar(), LinearLayout.LayoutParams(dp(116), -1))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        homeBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(16), dp(28), dp(32))
        }
        homeBody.addView(buildHeader())
        homeBody.addView(buildHero(null))
        homeBody.addView(buildEmptySection("SUGESTÕES", M3uItem.Kind.MOVIE))
        homeBody.addView(buildEmptySection("FILMES EM ALTA", M3uItem.Kind.MOVIE))
        homeBody.addView(buildEmptySection("SÉRIES POPULARES", M3uItem.Kind.SERIES))
        scroll.addView(homeBody)
        root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        return root
    }

    private fun renderCatalogHome(items: List<M3uItem>) {
        if (!::homeBody.isInitialized || items.isEmpty()) return
        val movies = items.filter { it.kind == M3uItem.Kind.MOVIE }
        val series = items.filter { it.kind == M3uItem.Kind.SERIES }
        val kids = items.filter { it.kind == M3uItem.Kind.KIDS }
        val channels = items.filter { it.kind == M3uItem.Kind.CHANNEL }
        homeBody.removeAllViews()
        homeBody.addView(buildHeader())
        homeBody.addView(buildHero((movies + series).firstOrNull()))
        homeBody.addView(buildSuggestions("SUGESTÕES", (movies + series + channels).distinctBy { it.url }.take(12), M3uItem.Kind.MOVIE))
        homeBody.addView(buildSuggestions("FILMES EM ALTA", movies.take(12), M3uItem.Kind.MOVIE))
        homeBody.addView(buildSuggestions("SÉRIES POPULARES", series.take(12), M3uItem.Kind.SERIES))
        if (kids.isNotEmpty()) homeBody.addView(buildSuggestions("KIDS", kids.take(12), M3uItem.Kind.KIDS))
    }

    private fun buildSidebar(): View {
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(26, 38, 69))
            setPadding(0, dp(12), 0, dp(10))
        }
        val tabs = listOf(
            "home" to "Início", "tv" to "Canais", "film" to "Filmes", "series" to "Séries",
            "trophy" to "Placar", "kids" to "Kids", "radio" to "Rádios", "camera" to "Câmeras",
            "search" to "Busca", "diagnostic" to "Diagnóstico", "settings" to "Ajustes"
        )
        tabs.forEachIndexed { index, (iconName, label) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setOnFocusChangeListener { view, focused ->
                    val active = focused || index == 0
                    view.setBackgroundColor(if (focused) Color.rgb(42, 58, 96) else Color.TRANSPARENT)
                    val container = view as LinearLayout
                    (container.getChildAt(0) as TextView).setTextColor(if (active) cyan else muted)
                    (container.getChildAt(1) as TextView).setTextColor(if (active) cyan else muted)
                }
                setOnClickListener {
                    val kind = when (label) {
                        "Canais" -> "CHANNEL"
                        "Filmes" -> "MOVIE"
                        "Séries" -> "SERIES"
                        "Kids" -> "KIDS"
                        else -> null
                    }
                    when {
                        kind != null -> startActivity(Intent(this@MainActivity, CatalogActivity::class.java).putExtra("mode", kind))
                        label == "Busca" -> startActivity(Intent(this@MainActivity, SearchActivity::class.java))
                        label == "Rádios" -> startActivity(Intent(this@MainActivity, RadioActivity::class.java))
                        label == "Placar" -> startActivity(Intent(this@MainActivity, ScoreActivity::class.java))
                        label == "Câmeras" -> startActivity(Intent(this@MainActivity, WorldCamerasActivity::class.java))
                        label == "Ajustes" -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        label == "Diagnóstico" -> startActivity(Intent(this@MainActivity, DiagnosticActivity::class.java))
                        label == "Início" -> Unit
                    }
                }
            }
            item.addView(TextView(this).apply {
                OriginalIcons.apply(this, iconName)
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) cyan else muted)
            }, LinearLayout.LayoutParams(-1, dp(30)))
            item.addView(TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) cyan else muted)
            }, LinearLayout.LayoutParams(-1, dp(24)))
            sidebar.addView(item, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return sidebar
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(12)) }
        val logo = ImageView(this).apply {
            setImageBitmap(assetBitmap("app-image.png"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Maximus Player"
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(68), dp(68)))
        row.addView(TextView(this).apply {
            text = "Olá\nMaximus Player"
            textSize = 20f
            setTextColor(white)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "♫   ▷"
            textSize = 17f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(160), dp(60)))
        return row
    }

    private fun buildHero(item: M3uItem?): View {
        val hero = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
            setOnClickListener {
                if (item != null) openItem(item) else openCatalog(M3uItem.Kind.MOVIE)
            }
        }
        val image = ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.78f
        }
        hero.addView(image, FrameLayout.LayoutParams(-1, -1))
        item?.logo?.takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, image) }
        hero.addView(View(this).apply { setBackgroundColor(Color.argb(125, 5, 10, 22)) }, FrameLayout.LayoutParams(-1, -1))
        hero.addView(TextView(this).apply {
            text = if (item == null) "FILMES E SÉRIES\n\nCarregando seu catálogo...\n\nSelecione para abrir o catálogo" else "${kindLabel(item.kind).uppercase()}\n\n${item.name}\n\n${item.group}\n\n▶  ASSISTIR"
            textSize = 18f
            setTextColor(white)
            setPadding(dp(26), dp(22), dp(26), dp(22))
        }, FrameLayout.LayoutParams(-1, -1))
        return hero.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(360)) }
    }

    private fun buildEmptySection(title: String, kind: M3uItem.Kind): View = buildSuggestions(title, emptyList(), kind)

    private fun buildSuggestions(title: String, items: List<M3uItem>, fallbackKind: M3uItem.Kind): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, 0) }
        section.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(white)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(34)))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (items.isEmpty()) {
            row.addView(TextView(this@MainActivity).apply {
                text = "Abra ${kindLabel(fallbackKind)} para carregar a lista real"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setBackgroundColor(surface)
                isFocusable = true
                setOnClickListener { openCatalog(fallbackKind) }
            }, LinearLayout.LayoutParams(dp(310), dp(78)).apply { setMargins(0, 0, dp(10), 0) })
        } else {
            items.forEach { item ->
                row.addView(TextView(this@MainActivity).apply {
                    text = "${item.name}\n${item.group}"
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(white)
                    setBackgroundColor(surface)
                    maxLines = 3
                    isFocusable = true
                    setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(45, 75, 100) else surface) }
                    setOnClickListener { openItem(item) }
                }, LinearLayout.LayoutParams(dp(190), dp(78)).apply { setMargins(0, 0, dp(10), 0) })
            }
        }
        scroll.addView(row)
        section.addView(scroll, LinearLayout.LayoutParams(-1, dp(88)))
        return section
    }

    private fun openCatalog(kind: M3uItem.Kind) {
        startActivity(Intent(this, CatalogActivity::class.java).putExtra("mode", kind.name))
    }

    private fun openItem(item: M3uItem) {
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

    private fun kindLabel(kind: M3uItem.Kind): String = when (kind) {
        M3uItem.Kind.CHANNEL -> "Canais"
        M3uItem.Kind.MOVIE -> "Filmes"
        M3uItem.Kind.SERIES -> "Séries"
        M3uItem.Kind.KIDS -> "Kids"
    }

    private fun loadRemoteImage(url: String, target: ImageView) {
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

    private fun assetBitmap(name: String) = assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
