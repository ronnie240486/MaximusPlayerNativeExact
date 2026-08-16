package com.interactiveplayer.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class CameraViewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title") ?: "Câmera"
        val url = intent.getStringExtra("url").orEmpty()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            if (url.isNotBlank()) loadUrl(url)
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(TextView(this).apply {
            text = "‹  $title"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 0, 24, 0)
            isFocusable = true
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(-1, 64))
        setContentView(root)
    }
}
