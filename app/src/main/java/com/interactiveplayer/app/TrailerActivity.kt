package com.interactiveplayer.app

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class TrailerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Trailer" }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        root.addView(TextView(this).apply {
            text = "‹  Trailer — $title"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(24, 12, 24, 12)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, 64))
        val web = WebView(this).apply {
            webViewClient = WebViewClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            loadUrl("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("$title trailer oficial", "UTF-8"))
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
