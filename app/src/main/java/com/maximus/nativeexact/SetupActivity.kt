package com.maximus.nativeexact

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class SetupActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val background = Color.rgb(8, 16, 30)
    private val panel = Color.rgb(28, 40, 70)
    private lateinit var serverInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var status: TextView
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
        AppPreferences.credentials(this)?.let {
            serverInput.setText(it.server)
            usernameInput.setText(it.username)
            passwordInput.setText(it.password)
        }
        serverInput.requestFocus()
    }

    private fun buildView(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(this@SetupActivity.background) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(80), dp(34), dp(80), dp(34))
        }
        root.addView(TextView(this).apply {
            text = "MAXIMUS PLAYER"
            textSize = 30f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, dp(60)))
        root.addView(TextView(this).apply {
            text = "Configuração da lista IPTV"
            textSize = 24f
            setTextColor(white)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(TextView(this).apply {
            text = "Informe o endereço do servidor, usuário e senha fornecidos pelo seu serviço."
            textSize = 16f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(18))
        }, LinearLayout.LayoutParams(-1, dp(54)))

        serverInput = field("URL do servidor", "http://servidor:porta")
        usernameInput = field("Usuário", "Digite seu usuário")
        passwordInput = field("Senha", "Digite sua senha")
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(serverInput, fieldParams())
        root.addView(usernameInput, fieldParams())
        root.addView(passwordInput, fieldParams())

        status = TextView(this).apply {
            textSize = 15f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status, LinearLayout.LayoutParams(-1, dp(44)))
        saveButton = Button(this).apply {
            text = "TESTAR E SALVAR"
            textSize = 17f
            setTextColor(Color.BLACK)
            setBackgroundColor(cyan)
            isFocusable = true
            isAllCaps = false
            setOnClickListener { validateAndSave() }
        }
        root.addView(saveButton, LinearLayout.LayoutParams(dp(360), dp(62)))
        root.addView(TextView(this).apply {
            text = "Você poderá alterar estes dados em Ajustes."
            textSize = 14f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(46)))
        scroll.addView(root)
        return scroll
    }

    private fun field(label: String, hint: String): EditText = EditText(this).apply {
        this.hint = "$label  —  $hint"
        textSize = 18f
        setTextColor(white)
        setHintTextColor(muted)
        setSingleLine(true)
        setPadding(dp(18), 0, dp(18), 0)
        setBackgroundColor(panel)
        imeOptions = EditorInfo.IME_ACTION_NEXT
        isFocusable = true
    }

    private fun fieldParams() = LinearLayout.LayoutParams(dp(720), dp(62)).apply {
        setMargins(0, 0, 0, dp(12))
    }

    private fun validateAndSave() {
        val server = AppPreferences.normalizeServer(serverInput.text.toString())
        val user = usernameInput.text.toString().trim()
        val pass = passwordInput.text.toString()
        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            status.text = "A URL deve começar com http:// ou https://"
            status.setTextColor(Color.rgb(255, 150, 130))
            serverInput.requestFocus()
            return
        }
        if (user.isBlank() || pass.isBlank()) {
            status.text = "Preencha usuário e senha."
            status.setTextColor(Color.rgb(255, 150, 130))
            if (user.isBlank()) usernameInput.requestFocus() else passwordInput.requestFocus()
            return
        }
        saveButton.isEnabled = false
        status.text = "Testando a conexão..."
        status.setTextColor(muted)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { testPlaylist(server, user, pass) }
            saveButton.isEnabled = true
            if (result.first) {
                AppPreferences.save(this@SetupActivity, server, user, pass)
                status.text = "Conexão aprovada. Abrindo o Maximus..."
                status.setTextColor(cyan)
                saveButton.postDelayed({ openProfiles() }, 250)
            } else {
                status.text = result.second
                status.setTextColor(Color.rgb(255, 190, 130))
            }
        }
    }

    private fun testPlaylist(server: String, user: String, pass: String): Pair<Boolean, String> {
        return runCatching {
            val endpoint = "$server/get.php?username=${java.net.URLEncoder.encode(user, "UTF-8")}&password=${java.net.URLEncoder.encode(pass, "UTF-8")}&type=m3u_plus&output=ts"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MaximusPlayer/1.0 Android")
            }
            val code = connection.responseCode
            val preview = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText().take(2048) } else ""
            connection.disconnect()
            if (code in 200..299 && (preview.contains("#EXTM3U", true) || preview.contains("#EXTINF", true))) {
                true to ""
            } else if (code in 200..299) {
                false to "O servidor respondeu, mas não retornou uma lista M3U válida."
            } else {
                false to "Servidor recusou a conexão (HTTP $code)."
            }
        }.getOrElse { false to "Não foi possível conectar. Confira o endereço e a internet." }
    }

    private fun openProfiles() {
        startActivity(android.content.Intent(this, ProfilesActivity::class.java))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
