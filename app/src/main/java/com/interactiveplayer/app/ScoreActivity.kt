package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
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

class ScoreActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val panel = Color.rgb(28, 40, 70)
    private val cyan = Color.rgb(53, 222, 231)
    private val background = Color.rgb(8, 16, 30)
    private lateinit var matches: LinearLayout
    private lateinit var status: TextView
    private var selected = SportsClient.sports.first()
    private var selectedView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
        loadSport(selected)
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@ScoreActivity.background)
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "‹  Jogos do Dia / Placar"
            textSize = 27f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        status = TextView(this).apply { textSize = 15f; setTextColor(muted); setPadding(0, 0, 0, dp(8)) }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(38)))
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sportScroll = ScrollView(this)
        val sportList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), dp(16), 0) }
        SportsClient.sports.forEach { sport ->
            val item = TextView(this).apply {
                text = sport.label
                textSize = 16f
                setTextColor(if (sport.key == selected.key) Color.BLACK else white)
                setBackgroundColor(if (sport.key == selected.key) cyan else panel)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(10), 0)
                isFocusable = true
                setOnFocusChangeListener { view, focused ->
                    if (focused && sport.key != selected.key) view.setBackgroundColor(Color.rgb(43, 73, 96))
                    else view.setBackgroundColor(if (sport.key == selected.key) cyan else panel)
                }
                setOnClickListener {
                    selected = sport
                    selectedView = this
                    refreshSportStyles(sportList)
                    loadSport(sport)
                }
            }
            if (sport.key == selected.key) selectedView = item
            sportList.addView(item, LinearLayout.LayoutParams(dp(250), dp(54)).apply { setMargins(0, 0, 0, dp(7)) })
        }
        sportScroll.addView(sportList)
        body.addView(sportScroll, LinearLayout.LayoutParams(dp(270), 0, 1f))
        val matchScroll = ScrollView(this)
        matches = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), 0, dp(16)) }
        matchScroll.addView(matches)
        body.addView(matchScroll, LinearLayout.LayoutParams(0, 0, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun refreshSportStyles(list: LinearLayout) {
        for (index in 0 until list.childCount) {
            val view = list.getChildAt(index) as TextView
            val sport = SportsClient.sports[index]
            view.setTextColor(if (sport.key == selected.key) Color.BLACK else white)
            view.setBackgroundColor(if (sport.key == selected.key) cyan else panel)
        }
    }

    private fun loadSport(sport: SportsClient.Sport) {
        status.text = "Carregando ${sport.label}..."
        matches.removeAllViews()
        matches.gravity = Gravity.CENTER
        matches.addView(ProgressBar(this).apply { indeterminateTintList = android.content.res.ColorStateList.valueOf(cyan) }, LinearLayout.LayoutParams(-1, dp(80)))
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) { SportsClient.fetchDays(sport) }
            renderEvents(events)
        }
    }

    private fun renderEvents(events: List<SportsClient.Event>) {
        matches.removeAllViews()
        matches.gravity = Gravity.TOP
        if (events.isEmpty()) {
            status.text = "Nenhum jogo encontrado nos últimos e próximos dias."
            matches.addView(TextView(this).apply {
                text = "Pode ser que este esporte esteja fora de temporada ou sem partidas nesta semana."
                textSize = 18f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }, LinearLayout.LayoutParams(-1, dp(160)))
            return
        }
        status.text = "${events.size} partidas encontradas — dados atualizados online"
        var currentDate = ""
        events.forEach { event ->
            if (event.date != currentDate) {
                currentDate = event.date
                matches.addView(TextView(this).apply {
                    text = dayLabel(event.date)
                    textSize = 15f
                    setTextColor(cyan)
                    setPadding(dp(4), dp(12), 0, dp(8))
                }, LinearLayout.LayoutParams(-1, dp(42)))
            }
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(panel)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(45, 75, 100) else panel) }
            }
            val teams = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            teams.addView(teamRow(event.home, event.homeLogo))
            teams.addView(teamRow(event.away, event.awayLogo))
            card.addView(teams, LinearLayout.LayoutParams(0, -1, 1f))
            val score = TextView(this).apply {
                val hasScore = event.homeScore != null || event.awayScore != null
                text = if (hasScore) "${event.homeScore ?: "-"}\n${event.awayScore ?: "-"}" else event.time ?: "--:--"
                textSize = if (hasScore) 18f else 15f
                setTextColor(if (hasScore) cyan else white)
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            card.addView(score, LinearLayout.LayoutParams(dp(90), -1))
            matches.addView(card, LinearLayout.LayoutParams(-1, dp(88)).apply { setMargins(0, 0, 0, dp(8)) })
        }
        matches.getChildAt(1)?.requestFocus()
    }

    private fun teamRow(name: String, logoUrl: String?): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply { setBackgroundColor(Color.rgb(48, 60, 84)); scaleType = ImageView.ScaleType.CENTER_INSIDE; contentDescription = name }
        row.addView(logo, LinearLayout.LayoutParams(dp(28), dp(28)))
        row.addView(TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(white)
            maxLines = 1
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(34), 1f))
        logoUrl?.let { loadLogo(it, logo) }
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

    private fun dayLabel(date: String): String {
        val today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val tomorrow = java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).toString()
        val yesterday = java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1).toString()
        return when (date) {
            today -> "HOJE — $date"
            tomorrow -> "AMANHÃ — $date"
            yesterday -> "ONTEM — $date"
            else -> date
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
