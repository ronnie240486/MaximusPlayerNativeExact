package com.interactiveplayer.app

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

/**
 * Trailer, portado de `frontend/app/trailer.tsx`.
 *
 * O original NÃO faz uma busca "adivinhada" no YouTube — o próprio
 * Xtream já devolve o ID do trailer certo no campo `youtube_trailer`
 * de `get_vod_info`/`get_series_info`. Com o ID em mãos, ele carrega o
 * embed padrão do YouTube (`/embed/{id}`), que é bem mais confiável do
 * que os truques de busca (o comentário do original é explícito sobre
 * isso: "YouTube can be picky about — error 153 for a top-level
 * navigation" com abordagens tipo `listType=search`, que foi o que eu
 * tinha tentado antes).
 *
 * Só cai na busca (mostrando os resultados crus do YouTube, como uma
 * lista pra pessoa escolher) quando o painel não tinha o ID do
 * trailer — exatamente como o original faz.
 */
class TrailerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra("title").orEmpty().ifBlank { "Trailer" }
        val videoId = intent.getStringExtra("videoId")?.let { extractYoutubeId(it) }
        val query = intent.getStringExtra("query") ?: "$title trailer oficial"
        setContentView(buildScreen(title, videoId, query))
    }

    private fun buildScreen(title: String, videoId: String?, query: String): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(buildHeader(title))

        if (videoId != null) {
            page.addView(buildEmbeddedPlayer(videoId), LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            page.addView(TextView(this).apply {
                setText("Não achamos o trailer oficial direto — toca no vídeo certo aqui embaixo.")
                textSize = 11f
                setTextColor(Theme.textSecondary)
                gravity = Gravity.CENTER
                setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), dp(Theme.SPACING_SM))
            })
            page.addView(buildSearchWebView(query), LinearLayout.LayoutParams(-1, 0, 1f))
        }

        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun buildHeader(title: String): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.SPACING_MD), dp(Theme.SPACING_MD), dp(Theme.SPACING_MD), dp(Theme.SPACING_MD))
        }
        header.addView(TextView(this).apply {
            setText("‹")
            textSize = 22f
            setTextColor(Theme.white)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText("Trailer • $title")
            textSize = 15f
            setTextColor(Theme.white)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            maxLines = 1
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))
        return header
    }

    /** Embed padrão do YouTube por ID — o mesmo formato usado pelo player embutido (react-native-youtube-iframe). */
    private fun buildEmbeddedPlayer(videoId: String): View {
        val wrap = FrameLayout(this)
        val progress = ProgressBar(this).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Theme.accentCyan)
        }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    progress.visibility = View.GONE
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) progress.visibility = View.GONE
                }
            }
            loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1")
        }
        wrap.addView(web, FrameLayout.LayoutParams(-1, -1))
        wrap.addView(progress, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        return wrap
    }

    /** Fallback: resultados crus de busca, exatamente como o original faz quando não tem o ID. */
    private fun buildSearchWebView(query: String): View =
        WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setBackgroundColor(Theme.black)
            loadUrl("https://m.youtube.com/results?search_query=${java.net.URLEncoder.encode(query, "UTF-8")}")
        }

    /** Mesma extração do original: aceita ID puro, URL completa ou link de embed. */
    private fun extractYoutubeId(raw: String): String? {
        val trimmed = raw.trim()
        if (Regex("^[a-zA-Z0-9_-]{11}$").matches(trimmed)) return trimmed
        runCatching {
            val uri = android.net.Uri.parse(trimmed)
            if (uri.host?.contains("youtu.be") == true) {
                return uri.pathSegments.firstOrNull()
            }
            uri.getQueryParameter("v")?.let { return it }
            val embedMatch = Regex("/embed/([a-zA-Z0-9_-]{11})").find(uri.path.orEmpty())
            if (embedMatch != null) return embedMatch.groupValues[1]
        }
        return Regex("[a-zA-Z0-9_-]{11}").find(trimmed)?.value
    }
}
