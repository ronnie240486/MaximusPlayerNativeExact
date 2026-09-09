package com.interactiveplayer.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Portado de `frontend/src/lib/weather.ts` do repositório Maximus.
 *
 * Open-Meteo é público e não exige cadastro nem chave de API, então não
 * há segredo nenhum aqui. A localização vem do IP público em vez do GPS
 * porque a maioria das TV box não tem GPS nem os serviços de localização
 * do Google — no original isso foi justamente o que fez o clima
 * finalmente aparecer nesses aparelhos.
 */
object WeatherClient {

    data class WeatherNow(val tempC: Int, val code: Int)

    data class IpLocation(val lat: Double, val lon: Double, val city: String?)

    /** Deve ser chamado fora da thread principal. */
    fun fetchWeather(lat: Double, lon: Double): WeatherNow? = runCatching {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon&current_weather=true"
        val json = JSONObject(readText(url) ?: return@runCatching null)
        val current = json.optJSONObject("current_weather") ?: return@runCatching null
        if (!current.has("temperature")) return@runCatching null
        WeatherNow(
            tempC = Math.round(current.optDouble("temperature")).toInt(),
            code = current.optInt("weathercode", 0),
        )
    }.getOrNull()

    /** Deve ser chamado fora da thread principal. */
    fun fetchLocationByIp(): IpLocation? = runCatching {
        val json = JSONObject(readText("https://ipapi.co/json/") ?: return@runCatching null)
        if (!json.has("latitude") || !json.has("longitude")) return@runCatching null
        IpLocation(
            lat = json.optDouble("latitude"),
            lon = json.optDouble("longitude"),
            city = json.optString("city").ifBlank { null },
        )
    }.getOrNull()

    /**
     * Tabela reduzida do "WMO Weather interpretation code" da Open-Meteo.
     * O original mapeia para nomes de ícone do Ionicons; aqui vira o
     * emoji correspondente, já que o nativo desenha o clima como texto.
     */
    fun weatherIcon(code: Int): String = when {
        code == 0 -> "☀"
        code <= 2 -> "⛅"
        code == 3 -> "☁"
        code == 45 || code == 48 -> "🌫"
        code in 51..67 -> "🌧"
        code in 71..77 -> "❄"
        code in 80..82 -> "🌧"
        code >= 95 -> "⛈"
        else -> "⛅"
    }

    fun weatherLabel(code: Int): String = when {
        code == 0 -> "Céu limpo"
        code <= 2 -> "Parcialmente nublado"
        code == 3 -> "Nublado"
        code == 45 || code == 48 -> "Neblina"
        code in 51..67 -> "Chuva"
        code in 71..77 -> "Neve"
        code in 80..82 -> "Pancadas de chuva"
        code >= 95 -> "Tempestade"
        else -> "Tempo variável"
    }

    private fun readText(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "MaximusPlayer/1.0")
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
