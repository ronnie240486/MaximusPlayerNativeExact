package com.maximus.nativeexact

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
        val path: String,
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
        Sport("futebol", "Futebol (Brasileirão)", Source.ESPN, "soccer/bra.1"),
        Sport("baseball", "Beisebol", Source.ESPN, "baseball/mlb"),
        Sport("tennis", "Tênis", Source.ESPN, "tennis/atp"),
        Sport("nfl", "Futebol Americano", Source.ESPN, "football/nfl"),
        Sport("volleyball", "Vôlei", Source.SPORTS_DB, "Volleyball"),
        Sport("mma", "MMA", Source.ESPN, "mma/ufc"),
        Sport("basketball", "Basquete (NBA)", Source.ESPN, "basketball/nba"),
        Sport("wnba", "Basquete (WNBA)", Source.ESPN, "basketball/wnba"),
        Sport("hockey", "Hóquei no Gelo", Source.ESPN, "hockey/nhl"),
        Sport("golf", "Golfe", Source.ESPN, "golf/pga"),
        Sport("f1", "Fórmula 1", Source.ESPN, "racing/f1"),
        Sport("nascar", "Nascar", Source.ESPN, "racing/nascar-premier"),
        Sport("indycar", "IndyCar", Source.ESPN, "racing/irl"),
    )

    fun fetchDays(sport: Sport): List<Event> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val dates = (-2..2).map { today.plusDays(it.toLong()) }
        return dates.flatMap { date -> fetchDay(sport, date) }.distinctBy { it.id }.sortedWith(compareBy<Event> { it.date }.thenBy { it.time ?: "99:99" })
    }

    private fun fetchDay(sport: Sport, date: LocalDate): List<Event> {
        return when (sport.source) {
            Source.ESPN -> fetchEspn(sport.path, date)
            Source.SPORTS_DB -> fetchSportsDb(sport.path, date)
        }
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
