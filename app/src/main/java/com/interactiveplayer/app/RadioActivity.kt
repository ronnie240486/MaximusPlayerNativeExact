package com.interactiveplayer.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tela de rádios, portada de `frontend/app/radios.tsx`, usando as cores
 * do Theme (a versão anterior tinha uma paleta própria, toda aproximada)
 * e adicionando a categoria "Favoritos" que faltava.
 *
 * O original toca a rádio inline, com um mini-player fixo embaixo da
 * lista, sem sair da tela (`useVideoPlayer` do expo-video). Portar isso
 * pediria trazer o ExoPlayer pra dentro desta Activity; por ora, tocar
 * uma estação continua abrindo o PlayerActivity — funciona, mas não é
 * o comportamento exato do original.
 */
class RadioActivity : ComponentActivity() {

    private companion object {
        const val FAVORITES_KEY = "__favorites__"
    }

    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var categoryRow: LinearLayout
    private var selectedKey: String = RadioBrowserClient.categories.first().key
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
        loadCategory(selectedKey)
    }

    private fun buildView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            setText("‹  Rádios")
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(Theme.SPACING_MD) })

        search = EditText(this).apply {
            hint = "Buscar rádio pelo nome"
            textSize = 14f
            setSingleLine(true)
            setTextColor(Theme.white)
            setHintTextColor(Theme.textMuted)
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_MD)
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString()?.trim().orEmpty()
                    when {
                        query.length >= 2 -> loadSearch(query)
                        query.isEmpty() -> loadCategory(selectedKey)
                    }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(search, LinearLayout.LayoutParams(0, dp(44), 1f))
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        })

        status = TextView(this).apply {
            textSize = 12f
            setTextColor(Theme.textSecondary)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        })

        val categoryScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // "Favoritos" é sempre a primeira aba, como no ALL_CATS do original.
        categoryRow.addView(categoryChip(FAVORITES_KEY, "Favoritos"))
        RadioBrowserClient.categories.forEach { category ->
            categoryRow.addView(categoryChip(category.key, category.label))
        }
        categoryScroll.addView(categoryRow)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        })

        val contentScroll = ScrollView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contentScroll.addView(list)
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun categoryChip(key: String, label: String): View =
        TextView(this).apply {
            setText(label)
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            refreshChipColors(this, key)
            isFocusable = true
            isClickable = true
            wireFocusHighlight(Theme.RADIUS_PILL)
            setOnClickListener {
                selectedKey = key
                search.setText("")
                refreshAllChips()
                loadCategory(key)
            }
            layoutParams = LinearLayout.LayoutParams(-2, dp(38)).apply {
                rightMargin = dp(Theme.SPACING_SM)
            }
        }

    private fun refreshChipColors(chip: TextView, key: String) {
        val active = key == selectedKey
        chip.setTextColor(if (active) Theme.black else Theme.white)
        chip.background = roundRect(if (active) Theme.accentCyan else Theme.darkSurfaceAlt, Theme.RADIUS_PILL)
        chip.setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), 0)
    }

    private fun refreshAllChips() {
        for (index in 0 until categoryRow.childCount) {
            val key = if (index == 0) FAVORITES_KEY else RadioBrowserClient.categories[index - 1].key
            refreshChipColors(categoryRow.getChildAt(index) as TextView, key)
        }
    }

    private fun loadCategory(key: String) {
        val token = ++requestId
        if (key == FAVORITES_KEY) {
            status.setText("Suas rádios favoritas")
            renderStations(RadioFavoriteStore.list(this), "favoritos")
            return
        }
        val category = RadioBrowserClient.categories.first { it.key == key }
        status.setText("Carregando ${category.label.lowercase()}...")
        showLoading()
        lifecycleScope.launch {
            val stations = withContext(Dispatchers.IO) { RadioBrowserClient.fetchByCategory(category) }
            if (token != requestId) return@launch
            renderStations(stations, category.label)
        }
    }

    private fun loadSearch(query: String) {
        val token = ++requestId
        status.setText("Buscando por $query...")
        showLoading()
        lifecycleScope.launch {
            val stations = withContext(Dispatchers.IO) { RadioBrowserClient.search(query) }
            if (token != requestId) return@launch
            renderStations(stations, "busca")
        }
    }

    private fun showLoading() {
        list.removeAllViews()
        list.gravity = Gravity.CENTER
        list.addView(
            ProgressBar(this).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
            },
            LinearLayout.LayoutParams(-1, dp(80))
        )
    }

    private fun renderStations(stations: List<RadioBrowserClient.Station>, source: String) {
        list.removeAllViews()
        list.gravity = Gravity.TOP
        status.setText(
            if (stations.isEmpty()) "Nenhuma estação disponível; tente outra categoria ou verifique a internet."
            else "${stations.size} estações em $source"
        )
        if (stations.isEmpty()) {
            list.addView(TextView(this).apply {
                setText("Rádio não encontrada")
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Theme.textMuted)
            }, LinearLayout.LayoutParams(-1, dp(120)))
            return
        }
        stations.forEach { station -> list.addView(buildStationRow(station)) }
    }

    private fun buildStationRow(station: RadioBrowserClient.Station): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            setPadding(dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM))
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener {
                startActivity(
                    Intent(this@RadioActivity, PlayerActivity::class.java)
                        .putExtra("url", station.resolvedUrl)
                        .putExtra("title", station.name)
                )
            }
        }

        val logo = ImageView(this).apply {
            background = roundRect(Theme.white, Theme.RADIUS_SM)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = station.name
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))

        row.addView(TextView(this).apply {
            setText(station.name.trim())
            textSize = 14f
            setTextColor(Theme.white)
            maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f).apply {
            leftMargin = dp(Theme.SPACING_SM)
            rightMargin = dp(Theme.SPACING_SM)
        })

        station.country?.takeIf { it.isNotBlank() }?.let { country ->
            row.addView(TextView(this).apply {
                setText(country)
                textSize = 11f
                setTextColor(Theme.textMuted)
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(Theme.SPACING_SM) })
        }

        val heart = TextView(this).apply {
            setText(if (RadioFavoriteStore.contains(this@RadioActivity, station)) "♥" else "♡")
            textSize = 18f
            setTextColor(if (RadioFavoriteStore.contains(this@RadioActivity, station)) Theme.accentMagenta else Theme.textMuted)
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
        }
        heart.setOnClickListener {
            val nowFavorite = RadioFavoriteStore.toggle(this, station)
            heart.setText(if (nowFavorite) "♥" else "♡")
            heart.setTextColor(if (nowFavorite) Theme.accentMagenta else Theme.textMuted)
            if (!nowFavorite && selectedKey == FAVORITES_KEY) loadCategory(FAVORITES_KEY)
        }
        row.addView(heart, LinearLayout.LayoutParams(dp(32), dp(32)))

        row.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        }

        station.favicon?.takeIf { it.isNotBlank() }?.let { loadLogo(it, logo) }
        return row
    }

    private fun loadLogo(url: String, target: ImageView) {
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
}
