package com.maximus.nativeexact

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class RadioActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val background = Color.rgb(8, 16, 30)
    private val panel = Color.rgb(28, 40, 70)
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var search: EditText
    private var selected = RadioBrowserClient.categories.first()
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
        loadCategory(selected)
    }

    private fun buildView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@RadioActivity.background)
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹  Rádios"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(280), dp(64)))
        search = EditText(this).apply {
            hint = "Buscar rádio pelo nome"
            textSize = 17f
            setSingleLine(true)
            setTextColor(white)
            setHintTextColor(muted)
            setBackgroundColor(panel)
            setPadding(dp(16), 0, dp(16), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s?.toString()?.trim().orEmpty()
                    if (query.length >= 2) loadSearch(query)
                    else if (query.isEmpty()) loadCategory(selected)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        header.addView(search, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(header)
        status = TextView(this).apply { textSize = 15f; setTextColor(muted); setPadding(0, dp(6), 0, dp(8)) }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(40)))

        val categoryScroll = ScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(10)) }
        RadioBrowserClient.categories.forEach { category ->
            categoryRow.addView(TextView(this).apply {
                text = category.label
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(if (category.key == selected.key) Color.BLACK else white)
                setBackgroundColor(if (category.key == selected.key) cyan else panel)
                setPadding(dp(18), 0, dp(18), 0)
                isFocusable = true
                setOnFocusChangeListener { view, focused ->
                    if (focused && category.key != selected.key) view.setBackgroundColor(Color.rgb(50, 72, 100))
                    else view.setBackgroundColor(if (category.key == selected.key) cyan else panel)
                }
                setOnClickListener {
                    selected = category
                    search.setText("")
                    refreshCategoryStyles(categoryRow)
                    loadCategory(category)
                }
            }, LinearLayout.LayoutParams(dp(150), dp(48)).apply { setMargins(0, 0, dp(8), 0) })
        }
        categoryScroll.addView(categoryRow)
        root.addView(categoryScroll, LinearLayout.LayoutParams(-1, dp(58)))

        val contentScroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(16))
        }
        contentScroll.addView(list)
        root.addView(contentScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun refreshCategoryStyles(row: LinearLayout) {
        for (index in 0 until row.childCount) {
            val category = RadioBrowserClient.categories[index]
            val view = row.getChildAt(index) as TextView
            view.setTextColor(if (category.key == selected.key) Color.BLACK else white)
            view.setBackgroundColor(if (category.key == selected.key) cyan else panel)
        }
    }

    private fun loadCategory(category: RadioBrowserClient.Category) {
        val token = ++requestId
        status.text = "Carregando ${category.label.lowercase()}..."
        showLoading()
        lifecycleScope.launch {
            val stations = withContext(Dispatchers.IO) { RadioBrowserClient.fetchByCategory(category) }
            if (token != requestId) return@launch
            renderStations(stations, category.label)
        }
    }

    private fun loadSearch(query: String) {
        val token = ++requestId
        status.text = "Buscando por $query..."
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
        list.addView(ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(cyan) }, LinearLayout.LayoutParams(-1, dp(80)))
    }

    private fun renderStations(stations: List<RadioBrowserClient.Station>, source: String) {
        list.removeAllViews()
        list.gravity = Gravity.TOP
        status.text = if (stations.isEmpty()) "Nenhuma estação disponível; tente outra categoria ou verifique a internet." else "${stations.size} estações em $source"
        if (stations.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Rádio não encontrada"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(muted)
            }, LinearLayout.LayoutParams(-1, dp(120)))
            return
        }
        stations.forEach { station ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(panel)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(45, 75, 100) else panel) }
                setOnClickListener { startActivity(android.content.Intent(this@RadioActivity, PlayerActivity::class.java).putExtra("url", station.resolvedUrl).putExtra("title", station.name)) }
            }
            val logo = ImageView(this).apply {
                setBackgroundColor(Color.WHITE)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = station.name
            }
            row.addView(logo, LinearLayout.LayoutParams(dp(56), dp(56)))
            row.addView(TextView(this).apply {
                text = station.name.trim()
                textSize = 18f
                setTextColor(white)
                maxLines = 2
                setPadding(dp(16), 0, dp(8), 0)
            }, LinearLayout.LayoutParams(0, -1, 1f))
            row.addView(TextView(this).apply {
                text = station.country?.ifBlank { "" }.orEmpty()
                textSize = 13f
                setTextColor(muted)
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(dp(90), -1))
            list.addView(row, LinearLayout.LayoutParams(-1, dp(76)).apply { setMargins(0, 0, 0, dp(8)) })
            station.favicon?.let { favicon -> loadLogo(favicon, logo) }
        }
        list.getChildAt(0)?.requestFocus()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
