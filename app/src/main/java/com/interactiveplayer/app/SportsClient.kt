package com.interactiveplayer.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SportsClient {
    data class Sport(
        val key: String,
        val label: String,
        val source: Source,
        // Mais de um caminho junta varias competicoes num so item da UI
        // (ex.: "Futebol" mistura Brasileirao, Libertadores, europeus...)
        val paths: List<String>,
    )

    enum class Source { ESPN, SPORTS_DB }

    data class Event(
        val id: String,
        val date: String,
        val time: String?,
        val home: String,
        val away: String,
        val homeScore: String?,
        val awayScore: String?,
        val homeLogo: String?,
        val awayLogo: String?,
        val status: String?,
    )

    val sports = listOf(
        Sport(
            "futebol", "Futebol", Source.ESPN,
            listOf(
                "soccer/bra.1",             // Brasileirão Série A
                "soccer/bra.2",             // Brasileirão Série B
                "soccer/bra.copa_do_brasil",
                "soccer/conmebol.libertadores",
                "soccer/conmebol.sudamericana",
                "soccer/fifa.world",
                "soccer/concacaf.league",
                "soccer/eng.1",             // Premier League
                "soccer/esp.1",             // La Liga
                "soccer/ita.1",             // Serie A
                "soccer/ger.1",             // Bundesliga
                "soccer/fra.1",             // Ligue 1
                "soccer/uefa.champions",
                "soccer/uefa.europa",
                "soccer/por.1",             // Primeira Liga
            ),
        ),
        Sport("baseball", "Beisebol", Source.ESPN, listOf("baseball/mlb")),
        Sport("tennis", "Tênis", Source.ESPN, listOf("tennis/atp")),
        Sport("nfl", "Futebol Americano", Source.ESPN, listOf("football/nfl")),
        Sport("volleyball", "Vôlei", Source.SPORTS_DB, listOf("Volleyball")),
        Sport("mma", "MMA", Source.ESPN, listOf("mma/ufc")),
        Sport("basketball", "Basquete (NBA)", Source.ESPN, listOf("basketball/nba")),
        Sport("wnba", "Basquete (WNBA)", Source.ESPN, listOf("basketball/wnba")),
        Sport("hockey", "Hóquei no Gelo", Source.ESPN, listOf("hockey/nhl")),
        Sport("golf", "Golfe", Source.ESPN, listOf("golf/pga")),
        Sport("f1", "Fórmula 1", Source.ESPN, listOf("racing/f1")),
        Sport("nascar", "Nascar", Source.ESPN, listOf("racing/nascar-premier")),
        Sport("indycar", "IndyCar", Source.ESPN, listOf("racing/irl")),
    )

    /**
     * Um "esporte" pode juntar várias competições (o Futebol soma ~15
     * ligas) e sempre busca 5 dias. Feito sequencial, isso passa de 70
     * chamadas HTTP e deixava a aba de Futebol travada por dezenas de
     * segundos. Todas as chamadas rodam em paralelo aqui.
     */
    suspend fun fetchDays(sport: Sport): List<Event> = coroutineScope {
        val today = LocalDate.now(ZoneOffset.UTC)
        val dates = (-2..2).map { today.plusDays(it.toLong()) }
        val jobs = dates.flatMap { date ->
            sport.paths.map { path ->
                async(Dispatchers.IO) { fetchOne(sport.source, path, date) }
            }
        }
        jobs.flatMap { it.await() }
            .distinctBy { it.id }
            .sortedWith(compareBy<Event> { it.date }.thenBy { it.time ?: "99:99" })
    }

    private fun fetchOne(source: Source, path: String, date: LocalDate): List<Event> = when (source) {
        Source.ESPN -> fetchEspn(path, date)
        Source.SPORTS_DB -> fetchSportsDb(path, date)
    }

    private fun fetchEspn(path: String, date: LocalDate): List<Event> {
        val yyyymmdd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val json = get("https://site.api.espn.com/apis/site/v2/sports/$path/scoreboard?dates=$yyyymmdd") ?: return emptyList()
        val events = json.optJSONArray("events") ?: return emptyList()
        val result = ArrayList<Event>(events.length())
        for (index in 0 until events.length()) {
            val raw = events.optJSONObject(index) ?: continue
            val competition = raw.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val competitors = competition.optJSONArray("competitors") ?: continue
            var home: JSONObject? = null
            var away: JSONObject? = null
            for (i in 0 until competitors.length()) {
                val competitor = competitors.optJSONObject(i) ?: continue
                if (competitor.optString("homeAway") == "home") home = competitor else if (competitor.optString("homeAway") == "away") away = competitor
            }
            val rawDate = raw.optString("date").ifBlank { "${date}T00:00:00Z" }
            val (dateText, timeText) = parseIso(rawDate)
            val status = competition.optJSONObject("status")?.optJSONObject("type")?.optString("description").orEmpty().ifBlank { null }
            result += Event(
                id = "espn-${raw.optString("id")}",
                date = dateText,
                time = timeText,
                home = competitorName(home),
                away = competitorName(away),
                homeScore = score(home),
                awayScore = score(away),
                homeLogo = home?.optJSONObject("team")?.optString("logo").orEmpty().ifBlank { null },
                awayLogo = away?.optJSONObject("team")?.optString("logo").orEmpty().ifBlank { null },
                status = status,
            )
        }
        return result
    }

    private fun fetchSportsDb(sport: String, date: LocalDate): List<Event> {
        val encoded = java.net.URLEncoder.encode(sport, "UTF-8")
        val json = get("https://www.thesportsdb.com/api/v1/json/123/eventsday.php?d=$date&s=$encoded") ?: return emptyList()
        val events = json.optJSONArray("events") ?: return emptyList()
        val result = ArrayList<Event>(events.length())
        for (index in 0 until events.length()) {
            val raw = events.optJSONObject(index) ?: continue
            result += Event(
                id = raw.optString("idEvent"),
                date = raw.optString("dateEvent").ifBlank { date.toString() },
                time = raw.optString("strTime").take(5).ifBlank { null },
                home = raw.optString("strHomeTeam").ifBlank { "—" },
                away = raw.optString("strAwayTeam").ifBlank { "—" },
                homeScore = raw.optString("intHomeScore").ifBlank { null },
                awayScore = raw.optString("intAwayScore").ifBlank { null },
                homeLogo = raw.optString("strHomeTeamBadge").ifBlank { null },
                awayLogo = raw.optString("strAwayTeamBadge").ifBlank { null },
                status = raw.optString("strStatus").ifBlank { null },
            )
        }
        return result
    }

    private fun competitorName(value: JSONObject?): String {
        val team = value?.optJSONObject("team")
        return team?.optString("displayName").orEmpty().ifBlank { value?.optJSONObject("athlete")?.optString("displayName").orEmpty() }.ifBlank { "—" }
    }

    private fun score(value: JSONObject?): String? = value?.optString("score").orEmpty().ifBlank { null }

    private fun parseIso(value: String): Pair<String, String?> {
        return runCatching {
            val instant = java.time.Instant.parse(value)
            val utc = instant.atOffset(ZoneOffset.UTC)
            utc.toLocalDate().toString() to "%02d:%02d".format(utc.hour, utc.minute)
        }.getOrElse { value.take(10) to null }
    }

    private fun get(url: String): JSONObject? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MaximusPlayer/1.0 Android")
            setRequestProperty("Accept", "application/json")
        }
        val body = if (connection.responseCode in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
        connection.disconnect()
        if (body.isBlank()) null else JSONObject(body)
    }.getOrNull()
}
