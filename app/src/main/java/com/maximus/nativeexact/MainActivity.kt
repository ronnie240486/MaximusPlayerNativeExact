package com.maximus.nativeexact

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
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
import android.widget.Toast
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    private val backgroundColor = Color.rgb(8, 16, 30)
    private val surface = Color.rgb(21, 31, 55)
    private val cyan = Color.rgb(53, 222, 231)
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(170, 179, 198)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildHome())
    }

    private fun buildHome(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(backgroundColor)
            isFocusable = true
        }
        root.addView(buildSidebar(), LinearLayout.LayoutParams(dp(116), -1))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(16), dp(28), dp(32))
        }
        body.addView(buildHeader())
        body.addView(buildHero())
        body.addView(buildSuggestions("SUGESTÕES", listOf("Filmes em alta", "Séries populares", "Canais mais assistidos")))
        body.addView(buildSuggestions("FILMES EM ALTA", listOf("Alma de Caçador", "Exterritorial", "Thelma", "Ação", "Suspense")))
        body.addView(buildSuggestions("SÉRIES POPULARES", listOf("Netflix", "Amazon Prime", "Kids", "Dramas", "Comédia")))
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        return root
    }

    private fun buildSidebar(): View {
        val sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.rgb(26, 38, 69))
            setPadding(0, dp(12), 0, dp(10))
        }
        val tabs = listOf(
            "⌂" to "Início", "▣" to "Canais", "▤" to "Filmes", "▥" to "Séries",
            "▦" to "Placar", "●" to "Kids", "◉" to "Rádios", "◌" to "Câmeras",
            "⌕" to "Busca", "∿" to "Diagnóstico", "⚙" to "Ajustes"
        )
        tabs.forEachIndexed { index, (glyph, label) ->
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
                        label == "Rádios" -> startActivity(Intent(this@MainActivity, RadioActivity::class.java))
                        label == "Placar" -> startActivity(Intent(this@MainActivity, ScoreActivity::class.java))
                        label == "Câmeras" -> startActivity(Intent(this@MainActivity, WorldCamerasActivity::class.java))
                        label == "Ajustes" -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        label == "Diagnóstico" -> startActivity(Intent(this@MainActivity, DiagnosticActivity::class.java))
                        else -> Toast.makeText(this@MainActivity, label, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item.addView(TextView(this).apply {
                text = glyph
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) cyan else muted)
            }, LinearLayout.LayoutParams(-1, dp(26)))
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
            text = "Olá, Ronnie\nMaximus Player"
            textSize = 20f
            setTextColor(white)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "12:08   ☼ 26°   ♫   ▷"
            textSize = 17f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(250), dp(60)))
        return row
    }

    private fun buildHero(): View {
        val hero = FrameLayout(this).apply {
            isFocusable = true
            isClickable = true
        }
        val image = ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.78f
        }
        hero.addView(image, FrameLayout.LayoutParams(-1, -1))
        hero.addView(View(this).apply { setBackgroundColor(Color.argb(125, 5, 10, 22)) }, FrameLayout.LayoutParams(-1, -1))
        hero.addView(TextView(this).apply {
            text = "FILME\n\nAlma de Caçador 4K [DV][HDR]\n\n★ 5.77   2024   HD\n\nUm assassino aposentado volta à ativa quando descobre uma conspiração perigosa.\n\n▶  ASSISTIR       ▷  TRAILER       ♡"
            textSize = 18f
            setTextColor(white)
            setPadding(dp(26), dp(22), dp(26), dp(22))
        }, FrameLayout.LayoutParams(-1, -1))
        return hero.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(360)) }
    }

    private fun buildSuggestions(title: String, names: List<String>): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, 0) }
        section.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(white)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(34)))
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        names.forEach { name ->
            row.addView(TextView(this@MainActivity).apply {
                text = name
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(white)
                setBackgroundColor(surface)
                isFocusable = true
                isClickable = true
                setOnClickListener { Toast.makeText(this@MainActivity, name, Toast.LENGTH_SHORT).show() }
            }, LinearLayout.LayoutParams(dp(180), dp(78)).apply { setMargins(0, 0, dp(10), 0) })
        }
        scroll.addView(row)
        section.addView(scroll, LinearLayout.LayoutParams(-1, dp(88)))
        return section
    }

    private fun assetBitmap(name: String) = assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
