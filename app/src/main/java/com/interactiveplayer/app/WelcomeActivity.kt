package com.interactiveplayer.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tela de abertura, portada de `frontend/app/welcome.tsx`.
 *
 * A versão anterior desenhava o logo em 360x220dp fixos, o que estourava
 * a tela em paisagem e empurrava o "Toque para continuar" pra fora. O
 * original usa 55% da largura, limitado a 260dp, e sempre quadrado.
 *
 * Também faltava o auto-avanço: lá `FALLBACK_MS = 6000` leva pros perfis
 * sozinho, sem depender de toque — importante em TV box, onde nem sempre
 * há um clique óbvio a dar.
 */
class WelcomeActivity : ComponentActivity() {

    private companion object {
        /** const FALLBACK_MS = 6000 do welcome.tsx. */
        const val FALLBACK_MS = 6000L
    }

    private var audio: MediaPlayer? = null
    private var autoAdvance: Job? = null
    private var advanced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = MacSessionStore.load(this)

        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        val background = ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))

        // styles.bgOverlay: rgba(11,15,26,0.55) sem fundo do painel.
        val overlay = View(this).apply {
            setBackgroundColor(Color.argb(140, 11, 15, 26))
        }
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))

        // styles.tapArea: ocupa a tela inteira e centraliza o conteúdo.
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(Theme.SPACING_XL), 0, dp(Theme.SPACING_XL), 0)
        }

        // styles.bannerBox: width 55%, maxWidth 260, aspectRatio 1.
        val bannerSide = minOf(
            (resources.displayMetrics.widthPixels * 0.55f).toInt(),
            dp(260)
        )
        val logo = ImageView(this).apply {
            setImageBitmap(assetBitmap("app-image.png"))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Maximus Player"
            background = roundRect(Color.TRANSPARENT, Theme.RADIUS_LG)
            clipToOutline = true
        }
        center.addView(logo, LinearLayout.LayoutParams(bannerSide, bannerSide))

        // styles.welcomeText
        center.addView(TextView(this).apply {
            setText(session?.appName ?: "Maximus player")
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Theme.SPACING_LG) })

        root.addView(center, FrameLayout.LayoutParams(-1, -1))

        // styles.skipHint: fixo no rodapé, não empilhado embaixo do texto —
        // era isso que fazia a dica sumir da tela em paisagem.
        root.addView(TextView(this).apply {
            setText("Toque para pular")
            textSize = 12f
            setTextColor(Theme.textMuted)
            letterSpacing = letterSpacingEm(1f, 12f)
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            bottomMargin = dp(Theme.SPACING_XL)
        })

        root.isFocusable = true
        root.isClickable = true
        root.setOnClickListener { openProfiles() }
        setContentView(root)

        session?.bgUrl?.let { url ->
            loadRemoteBitmap(url) { bitmap ->
                background.setImageBitmap(bitmap)
                // styles.bgOverlayLight: rgba(11,15,26,0.2) quando o fundo
                // vem do painel, que já é escuro o bastante.
                overlay.setBackgroundColor(Color.argb(51, 11, 15, 26))
            }
        }
        session?.bannerUrl?.let { url -> loadRemoteBitmap(url) { logo.setImageBitmap(it) } }
        session?.logoUrl?.let { url -> loadRemoteBitmap(url) { logo.setImageBitmap(it) } }

        runCatching {
            val descriptor = assets.openFd("original_media/welcome.wav")
            audio = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                prepare()
                start()
            }
            descriptor.close()
        }

        // Entra sozinho depois de 6s, mesmo sem nenhum toque.
        autoAdvance = lifecycleScope.launch {
            delay(FALLBACK_MS)
            openProfiles()
        }
    }

    /** Protegido contra chamada dupla: toque e timer podem coincidir. */
    private fun openProfiles() {
        if (advanced || isFinishing) return
        advanced = true
        autoAdvance?.cancel()
        startActivity(Intent(this, ProfilesActivity::class.java))
        finish()
    }

    private fun loadRemoteBitmap(url: String, onReady: (Bitmap) -> Unit) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 8000
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null && !isFinishing) onReady(bitmap)
        }
    }

    private fun assetBitmap(name: String) = runCatching {
        assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    override fun onDestroy() {
        autoAdvance?.cancel()
        audio?.release()
        audio = null
        super.onDestroy()
    }
}
