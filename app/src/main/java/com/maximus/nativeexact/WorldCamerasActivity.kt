package com.maximus.nativeexact

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class WorldCamerasActivity : ComponentActivity() {
    private data class Country(val id: String, val name: String, val flag: String, val url: String)

    private val countries = listOf(
        Country("brazil", "Brasil", "🇧🇷", "https://webcamera24.com/pt/countries/brazil/"),
        Country("usa", "Estados Unidos", "🇺🇸", "https://webcamera24.com/pt/countries/usa/"),
        Country("japan", "Japão", "🇯🇵", "https://webcamera24.com/pt/countries/japan/"),
        Country("canada", "Canadá", "🇨🇦", "https://webcamera24.com/pt/countries/canada/"),
        Country("spain", "Espanha", "🇪🇸", "https://webcamera24.com/pt/countries/spain/"),
        Country("turkey", "Turquia", "🇹🇷", "https://webcamera24.com/pt/countries/turkey/"),
        Country("thailand", "Tailândia", "🇹🇭", "https://webcamera24.com/pt/countries/thailand/"),
        Country("singapore", "Singapura", "🇸🇬", "https://webcamera24.com/pt/countries/singapore/"),
        Country("philippines", "Filipinas", "🇵🇭", "https://webcamera24.com/pt/countries/philippines/"),
        Country("taiwan", "Taiwan", "🇹🇼", "https://webcamera24.com/pt/countries/taiwan/"),
        Country("israel", "Israel", "🇮🇱", "https://webcamera24.com/pt/countries/israel/"),
        Country("italy", "Itália", "🇮🇹", "https://webcamera24.com/pt/countries/italy/"),
        Country("france", "França", "🇫🇷", "https://webcamera24.com/pt/countries/france/"),
        Country("uk", "Reino Unido", "🇬🇧", "https://webcamera24.com/pt/countries/united-kingdom/"),
        Country("all", "Ver todas", "🌎", "https://webcamera24.com/pt/popular/"),
    )
    private val white = Color.rgb(242, 244, 248)
    private val panel = Color.rgb(28, 40, 70)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "‹  Câmeras do Mundo"
            textSize = 27f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        root.addView(TextView(this).apply {
            text = "Escolha um país para ver as câmeras ao vivo"
            textSize = 16f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(42)))
        val scroll = ScrollView(this)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, dp(12)) }
        countries.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEach { country ->
                val card = LinearLayout(this@WorldCamerasActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setBackgroundColor(panel)
                    isFocusable = true
                    setPadding(dp(10), dp(12), dp(10), dp(12))
                    setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else panel) }
                    setOnClickListener {
                        startActivity(Intent(this@WorldCamerasActivity, CameraViewActivity::class.java).apply {
                            putExtra("url", country.url)
                            putExtra("title", country.name)
                        })
                    }
                }
                card.addView(TextView(this@WorldCamerasActivity).apply {
                    text = country.flag
                    textSize = 30f
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(-1, dp(44)))
                card.addView(TextView(this@WorldCamerasActivity).apply {
                    text = country.name
                    textSize = 15f
                    setTextColor(white)
                    gravity = Gravity.CENTER
                    maxLines = 2
                }, LinearLayout.LayoutParams(-1, dp(42)))
                row.addView(card, LinearLayout.LayoutParams(0, dp(100), 1f).apply { setMargins(0, 0, dp(10), dp(10)) })
            }
            if (pair.size == 1) row.addView(LinearLayout(this@WorldCamerasActivity), LinearLayout.LayoutParams(0, dp(100), 1f))
            grid.addView(row)
        }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
