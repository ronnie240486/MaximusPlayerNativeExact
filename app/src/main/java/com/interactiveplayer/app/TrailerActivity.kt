package com.interactiveplayer.app

import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.net.URLEncoder

/**
 * Trailer, via busca embutida do YouTube.
 *
 * A versão anterior carregava `youtube.com/results?search_query=...` —
 * a página COMPLETA de resultados de busca do YouTube, um site pesado
 * de verdade (SPA grande, thumbnails, rolagem infinita, anúncios) — num
 * WebView dentro do app. Numa TV box isso travava a tela inteira até
 * fechar, exatamente o problema relatado.
 *
 * A troca é o parâmetro `listType=search` do player embutido do
 * YouTube: ele toca o primeiro resultado da busca dentro do player leve
 * de embed, sem carregar o site inteiro e sem precisar de chave de API.
 */
class TrailerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Trailer" }
        setContentView(buildScreen(title))
    }

    private fun buildScreen(title: String): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        val progress = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
        }

        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = View.GONE
                }
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) progress.visibility = View.GONE
                }
            }
            val query = URLEncoder.encode("$title trailer oficial", "UTF-8")
            loadUrl("https://www.youtube.com/embed?listType=search&list=$query&autoplay=1")
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(android.graphics.Color.argb(160, 11, 15, 26))
            setPadding(dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM))
        }
        header.addView(TextView(this).apply {
            setText("‹")
            textSize = 22f
            setTextColor(Theme.white)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText("Trailer — $title")
            textSize = 15f
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Theme.SPACING_SM) })
        root.addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        return root
    }
}
