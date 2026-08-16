package com.maximus.nativeexact

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class ScoreActivity : ComponentActivity() {
    private val sports = listOf("Jogos do Dia", "Futebol", "NBA", "WNBA", "NFL", "MLB", "Tênis", "Vôlei", "MMA", "Fórmula 1", "IndyCar", "Nascar", "Golfe", "Hóquei")
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val panel = Color.rgb(28, 40, 70)
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
            text = "‹  Jogos do Dia / Placar"
            textSize = 27f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sportScroll = ScrollView(this)
        val sportList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), dp(16), 0) }
        sports.forEachIndexed { index, sport ->
            sportList.addView(TextView(this).apply {
                text = sport
                textSize = 16f
                setTextColor(if (index == 0) cyan else white)
                setBackgroundColor(if (index == 0) Color.rgb(36, 57, 92) else panel)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(10), 0)
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else panel) }
            }, LinearLayout.LayoutParams(dp(240), dp(54)).apply { setMargins(0, 0, 0, dp(7)) })
        }
        sportScroll.addView(sportList)
        body.addView(sportScroll, LinearLayout.LayoutParams(dp(260), 0, 1f))
        val matchScroll = ScrollView(this)
        val matches = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), 0, 0) }
        listOf("Jogos do Dia", "Próximas partidas", "Resultados recentes").forEach { title ->
            matches.addView(TextView(this).apply {
                text = "$title\n\nDados do placar serão carregados da fonte original."
                textSize = 20f
                setTextColor(white)
                setBackgroundColor(panel)
                setPadding(dp(22), dp(18), dp(22), dp(18))
                isFocusable = true
            }, LinearLayout.LayoutParams(-1, dp(120)).apply { setMargins(0, 0, 0, dp(12)) })
        }
        matchScroll.addView(matches)
        body.addView(matchScroll, LinearLayout.LayoutParams(0, 0, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
