package com.maximus.nativeexact

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class SettingsActivity : ComponentActivity() {
    private val options = listOf(
        "Conta e perfil", "Listas M3U", "Cache do catálogo", "Controle parental",
        "Player", "Áudio da apresentação", "Diagnóstico", "Sair"
    )
    private val white = Color.rgb(242, 244, 248)
    private val panel = Color.rgb(28, 40, 70)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "‹  Ajustes"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        options.forEach { option ->
            list.addView(TextView(this).apply {
                text = option
                textSize = 18f
                setTextColor(white)
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(panel)
                setPadding(dp(20), 0, dp(20), 0)
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else panel) }
                setOnClickListener { if (option == "Sair") finishAffinity() }
            }, LinearLayout.LayoutParams(-1, dp(70)).apply { setMargins(0, 0, 0, dp(10)) })
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
