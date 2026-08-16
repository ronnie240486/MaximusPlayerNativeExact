package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Color
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class WelcomeActivity : ComponentActivity() {
    private var audio: MediaPlayer? = null
    private val white = Color.rgb(242, 244, 248)
    private val cyan = Color.rgb(53, 222, 231)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = MacSessionStore.load(this)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 16, 30)) }
        val background = ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.78f
        }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { setBackgroundColor(Color.argb(125, 0, 0, 0)) }, FrameLayout.LayoutParams(-1, -1))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(36), dp(30), dp(36), dp(30))
        }
        val logo = ImageView(this).apply {
            setImageBitmap(assetBitmap("app-image.png"))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Maximus Player"
        }
        content.addView(logo, LinearLayout.LayoutParams(dp(360), dp(220)))
        content.addView(TextView(this).apply {
            text = session?.appName ?: "Maximus Player"
            textSize = 26f
            setTextColor(white)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(56)))
        content.addView(TextView(this).apply {
            text = "Toque para continuar"
            textSize = 18f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { openProfiles() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        content.setOnClickListener { openProfiles() }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        session?.bgUrl?.let { loadRemoteBitmap(it) { background.setImageBitmap(it); background.alpha = 0.32f } }
        session?.bannerUrl?.let { loadRemoteBitmap(it) { logo.setImageBitmap(it) } }
        session?.logoUrl?.let { loadRemoteBitmap(it) { logo.setImageBitmap(it) } }
        runCatching {
            val descriptor = assets.openFd("original_media/welcome.wav")
            audio = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                prepare()
                start()
            }
            descriptor.close()
        }
    }

    private fun openProfiles() {
        startActivity(android.content.Intent(this, ProfilesActivity::class.java))
        finish()
    }

    private fun loadRemoteBitmap(url: String, apply: (android.graphics.Bitmap) -> Unit) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 8000
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null && !isFinishing) apply(bitmap)
        }
    }

    private fun assetBitmap(name: String) = assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        audio?.release()
        audio = null
        super.onDestroy()
    }
}
