package com.interactiveplayer.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONObject

class MacLoginActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val background = Color.rgb(8, 16, 30)
    private var mac = ""
    private var checking = false
    private var attempts = 0
    private lateinit var status: TextView
    private lateinit var detail: TextView
    private lateinit var progress: ProgressBar
    private lateinit var checkButton: TextView
    private lateinit var testButton: TextView

    private val poll = object : Runnable {
        override fun run() {
            checkNow(false)
            handler.postDelayed(this, 5000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mac = DeviceIdentity.getMac(this)
        val cached = MacSessionStore.load(this)
        if (cached?.authorized == true && cached.playlists.isNotEmpty() && cached.status == "Teste" && !expired(cached.expireDate)) {
            openWelcome()
            return
        }
        setContentView(buildView())
        checkNow(false)
        handler.postDelayed(poll, 5000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun buildView(): ScrollView {
        val scroll = ScrollView(this).apply { setBackgroundColor(this@MacLoginActivity.background) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(80), dp(24), dp(80), dp(24))
        }
        val logo = ImageView(this).apply {
            setImageBitmap(assets.open("original_media/app-image.png").use { BitmapFactory.decodeStream(it) })
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Maximus Player"
        }
        root.addView(logo, LinearLayout.LayoutParams(dp(300), dp(120)))
        root.addView(TextView(this).apply {
            text = "Como entrar"
            textSize = 26f
            setTextColor(white)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(50)))
        root.addView(TextView(this).apply {
            text = "Use este ID do dispositivo para ativar o mesmo aplicativo no painel."
            textSize = 16f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(TextView(this).apply {
            text = "ID DO DISPOSITIVO (MAC)"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(4))
        }, LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(TextView(this).apply {
            text = mac
            textSize = 28f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { copyMac() }
        }, LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(TextView(this).apply {
            text = "Toque/pressione para copiar"
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(28)))
        status = TextView(this).apply {
            text = "AGUARDANDO ATIVAÇÃO..."
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(44)))
        detail = TextView(this).apply { textSize = 14f; setTextColor(muted); gravity = Gravity.CENTER }
        root.addView(detail, LinearLayout.LayoutParams(-1, dp(34)))
        progress = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(cyan)
        }
        root.addView(progress, LinearLayout.LayoutParams(dp(42), dp(42)))
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(16), 0, 0) }
        testButton = button("TESTE") { requestTest() }
        checkButton = button("VERIFICAR AGORA") { checkNow(true) }
        buttons.addView(testButton, buttonParams())
        buttons.addView(checkButton, buttonParams())
        root.addView(buttons)
        root.addView(button("WHATSAPP / REVENDEDOR") {
            val session = MacSessionStore.load(this@MacLoginActivity)
            val raw = session?.whatsappUrl ?: session?.resellerWhatsapp.orEmpty()
            val digits = raw.filter { it.isDigit() }
            val uri = if (raw.startsWith("http")) raw else if (digits.isNotBlank()) "https://wa.me/$digits" else "https://wa.me/"
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
        }, LinearLayout.LayoutParams(dp(360), dp(58)).apply { setMargins(0, dp(14), 0, 0) })
        root.addView(TextView(this).apply {
            text = "Tentativas: 0"
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
            tag = "attempts"
        }, LinearLayout.LayoutParams(-1, dp(34)))
        scroll.addView(root)
        return scroll
    }

    private fun button(label: String, action: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.BLACK)
        gravity = Gravity.CENTER
        setBackgroundColor(cyan)
        isFocusable = true
        setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(130, 250, 255) else cyan) }
        setOnClickListener { action() }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(dp(230), dp(58)).apply { setMargins(dp(6), 0, dp(6), 0) }

    private fun checkNow(manual: Boolean) {
        if (checking || mac.isBlank()) return
        checking = true
        progress.visibility = android.view.View.VISIBLE
        status.text = "VERIFICANDO..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { MacPanelClient.checkMac(mac) }
            checking = false
            attempts += 1
            progress.visibility = android.view.View.GONE
            status.text = if (result.session.authorized) "ACESSO AUTORIZADO" else "AGUARDANDO ATIVAÇÃO..."
            detail.text = result.session.message ?: if (result.session.registered) "MAC registrado, aguardando liberação da lista." else "ID ainda não encontrado no painel."
            findViewByTag<TextView>("attempts")?.text = "Tentativas: $attempts"
            if (result.session.authorized && result.session.playlists.isNotEmpty()) {
                MacSessionStore.save(this@MacLoginActivity, result.session)
                openWelcome()
            } else if (manual && result.session.message == "Falha de conexão.") {
                detail.text = "Falha de conexão. Verifique a internet da TV Box."
            }
        }
    }

    private fun requestTest() {
        if (checking || mac.isBlank()) return
        checking = true
        progress.visibility = android.view.View.VISIBLE
        status.text = "SOLICITANDO TESTE..."
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { MacPanelClient.registerTestDevice(mac) }
            if (!result.first) {
                checking = false
                progress.visibility = android.view.View.GONE
                status.text = "NÃO FOI POSSÍVEL GERAR O TESTE"
                detail.text = "Tente novamente ou fale com o revendedor."
                return@launch
            }
            val parsed = runCatching { JSONObject(result.second) }.getOrNull()
            val dns = parsed?.optString("dns").orEmpty()
            val username = parsed?.optString("username").orEmpty()
            val password = parsed?.optString("password").orEmpty()
            if (dns.isBlank() || username.isBlank() || password.isBlank()) {
                checking = false
                progress.visibility = android.view.View.GONE
                status.text = "TESTE SOLICITADO"
                detail.text = "Aguarde a ativação do MAC no painel e pressione VERIFICAR AGORA."
                return@launch
            }
            val server = if (dns.startsWith("http", true)) dns.trimEnd('/') else "http://${dns.trimEnd('/')}"
            val playlistUrl = "$server/get.php?username=${java.net.URLEncoder.encode(username, "UTF-8")}&password=${java.net.URLEncoder.encode(password, "UTF-8")}&type=m3u_plus&output=mpegts"
            val session = MacSessionStore.Session(true, true, mac, "Teste", parsed?.optString("expiresAt")?.ifBlank { null }, listOf(MacSessionStore.Playlist("Teste", playlistUrl, "m3u_plus")), null, null, null, "Maximus Player", null, null, null)
            MacSessionStore.save(this@MacLoginActivity, session)
            openWelcome()
        }
    }

    private fun copyMac() {
        getSystemService(android.content.ClipboardManager::class.java)?.setPrimaryClip(android.content.ClipData.newPlainText("MAC", mac))
        detail.text = "MAC copiado."
    }

    private fun openWelcome() {
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }

    private fun expired(date: String?): Boolean {
        if (date.isNullOrBlank()) return false
        return runCatching { java.time.LocalDateTime.parse(date.replace(' ', 'T')).isBefore(java.time.LocalDateTime.now()) }.getOrDefault(false)
    }

    private fun <T : android.view.View> findViewByTag(tag: String): T? = (window.decorView.findViewWithTag(tag) as? T)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
