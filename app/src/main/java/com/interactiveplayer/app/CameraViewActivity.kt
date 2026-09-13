package com.interactiveplayer.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
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
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setPadding(dp(8), 0, dp(24), 0)
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(8), dp(16), dp(8))
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            text = title
            textSize = 22f
            setTextColor(Color.WHITE)
        })
        root.addView(header, FrameLayout.LayoutParams(-1, dp(64)))
        setContentView(root)
    }
}
