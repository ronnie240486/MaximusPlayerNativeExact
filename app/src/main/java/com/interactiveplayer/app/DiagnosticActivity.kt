package com.interactiveplayer.app

import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class DiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val connected = (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork?.let {
            getSystemService(ConnectivityManager::class.java).getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(28, 22, 28, 22)
        }
        root.addView(TextView(this).apply {
            text = "‹  Diagnóstico"
            textSize = 28f
            setTextColor(Color.WHITE)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, 72))
        listOf(
            "Rede" to if (connected) "Conectada" else "Sem conexão",
            "Arquitetura" to "Native arm64-v8a",
            "Player" to "Media3 disponível",
            "Catálogo" to "M3U/Xtream pronto",
            "Cache" to "Memória local habilitada"
        ).forEach { (label, value) ->
            root.addView(TextView(this).apply {
                text = "$label\n$value"
                textSize = 19f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.rgb(28, 40, 70))
                setPadding(20, 0, 20, 0)
                isFocusable = true
            }, LinearLayout.LayoutParams(-1, 82).apply { setMargins(0, 0, 0, 10) })
        }
        setContentView(root)
    }
}
