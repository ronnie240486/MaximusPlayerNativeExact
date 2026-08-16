package com.interactiveplayer.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SearchActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val panel = Color.rgb(28, 40, 70)
    private lateinit var query: EditText
    private lateinit var results: LinearLayout
    private var items: List<M3uItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
        query.requestFocus()
        lifecycleScope.launch {
            items = CatalogRepository.load(this@SearchActivity)
            render()
        }
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(20))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹  Busca"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(220), dp(62)))
        query = EditText(this).apply {
            hint = "Digite o nome do canal, filme ou série"
            textSize = 18f
            setTextColor(white)
            setHintTextColor(muted)
            setSingleLine(true)
            setOnEditorActionListener { _, _, _ -> render(); false }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        header.addView(query, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(header)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(14), 0, dp(20)) }
        root.addView(ScrollView(this).apply { addView(results) }, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun render() {
        if (!::results.isInitialized) return
        val queryText = query.text?.toString()?.trim()?.lowercase().orEmpty()
        val found = if (queryText.isBlank()) emptyList() else items.filter { it.name.lowercase().contains(queryText) || it.group.lowercase().contains(queryText) }.take(100)
        results.removeAllViews()
        results.addView(TextView(this).apply {
            text = if (queryText.isBlank()) "Digite para pesquisar no catálogo completo" else "${found.size} resultado(s)"
            textSize = 16f
            setTextColor(if (found.isEmpty() && queryText.isNotBlank()) muted else cyan)
        }, LinearLayout.LayoutParams(-1, dp(44)))
        found.forEach { item ->
            val row = TextView(this).apply {
                text = "${kindLabel(item.kind)}  •  ${item.name}\n${item.group}"
                textSize = 17f
                setTextColor(white)
                setBackgroundColor(panel)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else panel) }
                setOnClickListener {
                    if (item.kind == M3uItem.Kind.CHANNEL) startActivity(android.content.Intent(this@SearchActivity, PlayerActivity::class.java).putExtra("url", item.url).putExtra("title", item.name))
                    else startActivity(android.content.Intent(this@SearchActivity, ContentDetailsActivity::class.java).apply { putExtra("name", item.name); putExtra("group", item.group); putExtra("logo", item.logo); putExtra("url", item.url); putExtra("kind", item.kind.name) })
                }
            }
            results.addView(row, LinearLayout.LayoutParams(-1, dp(74)).apply { setMargins(0, 0, 0, dp(8)) })
        }
    }

    private fun kindLabel(kind: M3uItem.Kind) = when (kind) {
        M3uItem.Kind.CHANNEL -> "Canal"
        M3uItem.Kind.MOVIE -> "Filme"
        M3uItem.Kind.SERIES -> "Série"
        M3uItem.Kind.KIDS -> "Kids"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
