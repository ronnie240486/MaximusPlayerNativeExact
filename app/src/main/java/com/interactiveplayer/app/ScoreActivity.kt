package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Typeface
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

/**
 * Placar, portado das cores aproximadas anteriores para o Theme oficial.
 *
 * `frontend/app/placar.tsx` (603 linhas) também tem abas de classificação
 * e detalhes de partida que esta versão não replica — mantém o formato
 * de lista de jogos por dia que já existia, só com o visual correto
 * (cards arredondados, cores do Theme.kt) em vez da paleta improvisada.
 */
class ScoreActivity : ComponentActivity() {

    private lateinit var matches: LinearLayout
    private lateinit var status: TextView
    private lateinit var sportList: LinearLayout
    private var selected = SportsClient.sports.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
        loadSport(selected)
    }

    private fun buildView(): LinearLayout {
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
        root.addView(TextView(this).apply {
            setText("‹  Jogos do Dia / Placar")
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.SPACING_SM) })

        status = TextView(this).apply {
            textSize = 12f
            setTextColor(Theme.textSecondary)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val sportScroll = ScrollView(this)
        sportList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        SportsClient.sports.forEach { sport -> sportList.addView(sportChip(sport)) }
        sportScroll.addView(sportList)
        body.addView(sportScroll, LinearLayout.LayoutParams(dp(180), -1).apply {
            rightMargin = dp(Theme.SPACING_MD)
        })

        val matchScroll = ScrollView(this)
        matches = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        matchScroll.addView(matches)
        body.addView(matchScroll, LinearLayout.LayoutParams(0, -1, 1f))

        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun sportChip(sport: SportsClient.Sport): TextView =
        TextView(this).apply {
            setText(sport.label)
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_SM), 0)
            refreshChip(this, sport)
            isFocusable = true
            isClickable = true
            setOnClickListener {
                selected = sport
                refreshAllChips()
                loadSport(sport)
            }
            layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply {
                bottomMargin = dp(Theme.SPACING_SM)
            }
        }

    private fun refreshChip(chip: TextView, sport: SportsClient.Sport) {
        val active = sport.key == selected.key
        chip.setTextColor(if (active) Theme.black else Theme.white)
        chip.background = roundRect(if (active) Theme.accentCyan else Theme.darkSurface, Theme.RADIUS_SM)
    }

    private fun refreshAllChips() {
        for (index in 0 until sportList.childCount) {
            refreshChip(sportList.getChildAt(index) as TextView, SportsClient.sports[index])
        }
    }

    private fun loadSport(sport: SportsClient.Sport) {
        status.setText("Carregando ${sport.label}...")
        matches.removeAllViews()
        matches.gravity = Gravity.CENTER
        matches.addView(
            ProgressBar(this).apply {
                indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
            },
            LinearLayout.LayoutParams(-1, dp(80))
        )
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) { SportsClient.fetchDays(sport) }
            renderEvents(events)
        }
    }

    private fun renderEvents(events: List<SportsClient.Event>) {
        matches.removeAllViews()
        matches.gravity = Gravity.TOP
        if (events.isEmpty()) {
            status.setText("Nenhum jogo encontrado nos últimos e próximos dias.")
            matches.addView(TextView(this).apply {
                setText("Pode ser que este esporte esteja fora de temporada ou sem partidas nesta semana.")
                textSize = 14f
                setTextColor(Theme.textMuted)
                gravity = Gravity.CENTER
                setPadding(dp(Theme.SPACING_LG), dp(Theme.SPACING_LG), dp(Theme.SPACING_LG), dp(Theme.SPACING_LG))
            }, LinearLayout.LayoutParams(-1, dp(160)))
            return
        }
        status.setText("${events.size} partidas encontradas — dados atualizados online")

        var currentDate = ""
        events.forEach { event ->
            if (event.date != currentDate) {
                currentDate = event.date
                matches.addView(TextView(this).apply {
                    setText(dayLabel(event.date))
                    textSize = 13f
                    setTextColor(Theme.accentCyan)
                    setTypeface(Typeface.DEFAULT_BOLD)
                    setPadding(dp(4), dp(Theme.SPACING_SM), 0, dp(6))
                })
            }
            matches.addView(buildMatchCard(event))
        }
    }

    private fun buildMatchCard(event: SportsClient.Event): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_SM),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_SM)
            )
            isFocusable = true
        }

        val teams = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        teams.addView(teamRow(event.home, event.homeLogo))
        teams.addView(teamRow(event.away, event.awayLogo))
        card.addView(teams, LinearLayout.LayoutParams(0, -2, 1f))

        val hasScore = event.homeScore != null || event.awayScore != null
        card.addView(TextView(this).apply {
            setText(if (hasScore) "${event.homeScore ?: "-"}\n${event.awayScore ?: "-"}" else event.time ?: "--:--")
            textSize = if (hasScore) 16f else 13f
            setTextColor(if (hasScore) Theme.accentCyan else Theme.white)
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(dp(64), -2))

        card.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        }
        return card
    }

    private fun teamRow(name: String, logoUrl: String?): LinearLayout {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            background = roundRect(Theme.darkSurfaceAlt, 6)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = name
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(24), dp(24)))
        row.addView(TextView(this).apply {
            setText(name)
            textSize = 13f
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Theme.SPACING_SM) })
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
}
