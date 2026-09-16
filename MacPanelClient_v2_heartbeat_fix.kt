package com.interactiveplayer.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object MacPanelClient {
    // Migração Railway (ver "Migração dos Apps para o Railway"): o
    // painel de administração (onde MAC/cliente é cadastrado) mudou do
    // Manus pro Railway. Railway é tentado primeiro; se ele disser que
    // o cliente está liberado, usa a resposta dele. Se não (ou se a
    // chamada falhar), tenta o Manus antes de negar — igual à lógica
    // já implementada em maximus-player-source/src/api/client.ts do
    // app React Native, pra clientes que ainda não foram migrados pro
    // Railway continuarem funcionando.
    private const val PANEL_BASE_PRIMARY = "https://renciaapp-production.up.railway.app/api/v5"
    private const val PANEL_ROOT_PRIMARY = "https://renciaapp-production.up.railway.app"
    private const val PANEL_BASE_FALLBACK = "https://renciaapp.manus.space/api/v5"
    private const val PANEL_ROOT_FALLBACK = "https://renciaapp.manus.space"
    private const val PANEL_BASE_V4_PRIMARY = "https://renciaapp-production.up.railway.app/api/v4"
    private const val PANEL_BASE_V4_FALLBACK = "https://renciaapp.manus.space/api/v4"
    private const val TEST_REGISTER_FALLBACK = "https://nuvixtv.sigmab.pro/api/chatbot/Yen129WPEa/XYgD9JWr6V"

    data class CheckResult(
        val session: MacSessionStore.Session,
        val raw: String,
    )

    fun checkMac(mac: String): CheckResult {
        val primary = checkMacAt(PANEL_BASE_PRIMARY, mac)
        if (primary != null && primary.session.authorized) return primary

        val fallback = checkMacAt(PANEL_BASE_FALLBACK, mac)
        if (fallback != null) return fallback

        // Nenhum dos dois respondeu — devolve o que o Railway disse (se
        // respondeu, mesmo negando) ou uma falha de conexão genérica.
        return primary ?: CheckResult(
            MacSessionStore.Session(false, false, mac, null, null, emptyList(), null, null, null, null, null, null, "Falha de conexão."),
            "",
        )
    }

    private fun checkMacAt(base: String, mac: String): CheckResult? {
        val endpoint = "$base/check_mac.php?mac=${encode(mac)}"
        return runCatching {
            val jsonText = get(endpoint)
            val json = JSONObject(jsonText)
            CheckResult(normalize(json, mac), jsonText)
        }.getOrNull()
    }

    fun registerTestDevice(mac: String): Pair<Boolean, String> {
        val registerUrl = runCatching {
            val guim = guimFor(mac)
            JSONObject(guim).optString("gpcpro_server_url").ifBlank { TEST_REGISTER_FALLBACK }
        }.getOrDefault(TEST_REGISTER_FALLBACK)
        return runCatching {
            val connection = (URL("$registerUrl?mac=${encode(mac)}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            connection.outputStream.use { it.write(JSONObject().put("mac", mac).toString().toByteArray()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            (code in 200..299) to body
        }.getOrElse { false to it.message.orEmpty() }
    }

    /**
     * Avisa o painel o que está sendo assistido nesse MAC agora — é isso
     * que faz "Dispositivos Conectados" mostrar o nome do canal, não só
     * "online". Best-effort: chamado de um timer periódico (ver
     * HeartbeatReporter), nunca deve atrapalhar a reprodução se falhar.
     *
     * Testei o endpoint (`GET .../heartbeat.php?mac=...`) direto no painel
     * de produção: ele SEMPRE responde 200 com o último valor já salvo,
     * mesmo mandando `content` diferente por query string — ou seja, o GET
     * é só leitura, nunca grava. Só descobri isso testando de fora; não dá
     * pra saber com certeza qual formato de POST cada um dos ~12 painéis
     * (todos rodando o mesmo backend, mas possivelmente versões/configs
     * diferentes) realmente espera. Por isso, em vez de apostar num único
     * formato, tenta alguns em sequência e CONFERE de verdade (com um GET
     * de volta) se o painel realmente gravou o conteúdo antes de desistir —
     * assim funciona mesmo que o formato certo não seja o que eu assumi.
     */
    fun sendHeartbeat(mac: String, content: String) {
        if (!sendHeartbeatAt(PANEL_BASE_V4_PRIMARY, mac, content)) {
            sendHeartbeatAt(PANEL_BASE_V4_FALLBACK, mac, content)
        }
    }

    // Lembra qual formato funcionou da última vez pra tentar ele primeiro
    // na próxima chamada (evita repetir 2 tentativas erradas a cada 30s
    // depois que já se sabe qual formato esse painel aceita).
    private var lastWorkingStrategy = 0

    private fun sendHeartbeatAt(base: String, mac: String, content: String): Boolean {
        val url = "$base/heartbeat.php"
        val strategies: List<() -> Boolean> = listOf(
            { postJson(url, mac, content) },
            { postForm(url, mac, content) },
            { getWrite(url, mac, content) },
        )
        val order = (strategies.indices.toList().drop(lastWorkingStrategy) + strategies.indices.toList().take(lastWorkingStrategy))
        for (index in order) {
            if (strategies[index]() && verifyHeartbeat(url, mac, content)) {
                lastWorkingStrategy = index
                return true
            }
        }
        return false
    }

    /** Confere se o painel realmente gravou `content` pra esse `mac` (não confia só no HTTP 200). */
    private fun verifyHeartbeat(url: String, mac: String, expected: String): Boolean = runCatching {
        val body = get("$url?mac=${encode(mac)}")
        JSONObject(body).optString("content").trim().equals(expected.trim(), ignoreCase = true)
    }.getOrDefault(false)

    private fun postJson(url: String, mac: String, content: String): Boolean = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        connection.outputStream.use {
            it.write(JSONObject().put("mac", mac).put("content", content).toString().toByteArray())
        }
        val code = connection.responseCode
        connection.disconnect()
        code in 200..299
    }.getOrDefault(false)

    /** Fallback: alguns backends PHP só leem `$_POST` (form-urlencoded), não JSON cru. */
    private fun postForm(url: String, mac: String, content: String): Boolean = runCatching {
        val body = "mac=${encode(mac)}&content=${encode(content)}"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        connection.outputStream.use { it.write(body.toByteArray()) }
        val code = connection.responseCode
        connection.disconnect()
        code in 200..299
    }.getOrDefault(false)

    /** Fallback: alguns painéis mais antigos gravam via GET com querystring. */
    private fun getWrite(url: String, mac: String, content: String): Boolean = runCatching {
        get("$url?mac=${encode(mac)}&content=${encode(content)}")
        true
    }.getOrDefault(false)

    fun fetchExtras(mac: String): Map<String, String> = runCatching {
        val json = JSONObject(guimFor(mac))
        buildMap {
            listOf("impactPhrase", "lockTitle", "lockMessage", "lockButtonText", "lockButtonUrl", "websiteUrl", "contactInfo", "resellerEmail", "legalNotice").forEach { key ->
                json.optString(key).ifBlank { null }?.let { put(key, it) }
            }
        }
    }.getOrDefault(emptyMap())

    /** Railway primeiro, cai pro Manus se não responder — mesmo padrão do checkMac. */
    private fun guimFor(mac: String): String =
        runCatching { get("$PANEL_ROOT_PRIMARY/api/guim.php?mac=${encode(mac)}") }
            .getOrElse { get("$PANEL_ROOT_FALLBACK/api/guim.php?mac=${encode(mac)}") }

    private fun normalize(json: JSONObject, mac: String): MacSessionStore.Session {
        val registered = json.optBoolean("mac_registered") || json.optBoolean("registered") || json.optInt("registered") == 1 || json.optBoolean("found") || json.optString("status").equals("ativo", true)
        val allowed = json.optBoolean("allowed", registered) && json.optBoolean("success", true) && !json.optBoolean("blocked", false)
        val playlists = ArrayList<MacSessionStore.Playlist>()
        listOf("playlists", "lista", "listas").forEach { key -> appendPlaylistValue(json.opt(key), playlists) }
        if (playlists.isEmpty()) appendPlaylistValue(json.opt("playlist"), playlists)
        if (playlists.isEmpty()) {
            listOf("playlist_url", "playlistUrl", "urlM3u8", "url_m3u8", "m3u_url", "m3u8", "m3u", "url", "link").forEach { key ->
                val url = json.optString(key).trim()
                if (url.isNotBlank()) appendPlaylist(url, json.optString("playlist_name").ifBlank { json.optString("nomeServer").ifBlank { "Playlist" } }, playlists)
            }
        }
        if (playlists.isEmpty()) {
            val server = json.optString("dns").ifBlank { json.optString("server").ifBlank { json.optString("server_url") } }.trimEnd('/')
            val user = json.optString("username").ifBlank { json.optString("user").ifBlank { json.optString("login") } }
            val pass = json.optString("password").ifBlank { json.optString("pass").ifBlank { json.optString("senha") } }
            if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                appendPlaylist("$server/get.php?username=${encode(user)}&password=${encode(pass)}&type=m3u_plus&output=ts", json.optString("playlist_name").ifBlank { "Playlist" }, playlists)
            }
        }
        return MacSessionStore.Session(
            authorized = registered && allowed && playlists.isNotEmpty(),
            registered = registered,
            mac = json.optString("mac").ifBlank { mac },
            status = json.optString("status").ifBlank { null },
            expireDate = json.optString("dataExpiracao").ifBlank { json.optString("expire_date").ifBlank { null } },
            playlists = playlists,
            logoUrl = json.optString("logo_url").ifBlank { null },
            bgUrl = json.optString("bg_url").ifBlank { null },
            bannerUrl = json.optString("banner_url").ifBlank { null },
            appName = json.optString("app_name").ifBlank { json.optString("app").ifBlank { null } },
            whatsappUrl = json.optString("whatsapp_url").ifBlank { null },
            resellerWhatsapp = json.optString("reseller_whatsapp").ifBlank { null },
            message = json.optString("message").ifBlank { json.optString("mensagem").ifBlank { json.optString("error").ifBlank { null } } },
        )
    }

    private fun appendPlaylistValue(value: Any?, playlists: MutableList<MacSessionStore.Playlist>) {
        when (value) {
            is org.json.JSONArray -> for (index in 0 until value.length()) appendPlaylistValue(value.opt(index), playlists)
            is JSONObject -> {
                val url = value.optString("playlist_url").ifBlank { value.optString("url").ifBlank { value.optString("urlM3u8") } }.trim()
                if (url.isNotBlank()) appendPlaylist(url, value.optString("playlist_name").ifBlank { value.optString("name").ifBlank { "Playlist" } }, playlists)
            }
            is String -> if (value.isNotBlank()) appendPlaylist(value.trim(), "Playlist", playlists)
        }
    }

    private fun appendPlaylist(url: String, name: String, playlists: MutableList<MacSessionStore.Playlist>) {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            if (playlists.none { it.url == url }) playlists += MacSessionStore.Playlist(name, url, "m3u_plus")
        }
    }

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) error("HTTP $code")
        return body
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private const val USER_AGENT = "VLC/3.5.5 (Linux;Android 12) LibVLC/3.5.5"
}
