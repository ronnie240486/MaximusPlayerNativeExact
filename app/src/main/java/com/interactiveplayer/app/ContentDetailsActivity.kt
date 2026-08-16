package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
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

class ContentDetailsActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val magenta = Color.rgb(255, 80, 180)
    private val panel = Color.rgb(28, 40, 70)
    private lateinit var favoriteButton: TextView
    private lateinit var item: M3uItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = M3uItem(
            name = intent.getStringExtra("name").orEmpty().ifBlank { "Conteúdo" },
            group = intent.getStringExtra("group").orEmpty(),
            logo = intent.getStringExtra("logo"),
            url = intent.getStringExtra("url").orEmpty(),
            kind = runCatching { M3uItem.Kind.valueOf(intent.getStringExtra("kind") ?: "MOVIE") }.getOrDefault(M3uItem.Kind.MOVIE),
        )
        setContentView(buildView())
    }

    private fun buildView(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(30))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(white)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(60), dp(56)))
        header.addView(TextView(this).apply {
            text = item.name
            textSize = 20f
            setTextColor(white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(header)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val poster = ImageView(this).apply {
            setBackgroundColor(Color.rgb(35, 48, 80))
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = item.name
        }
        hero.addView(poster, LinearLayout.LayoutParams(dp(220), dp(300)))
        hero.addView(LinearLayout(this@ContentDetailsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, 0, 0)
            addView(TextView(this@ContentDetailsActivity).apply {
                text = item.name
                textSize = 28f
                setTextColor(white)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(-1, dp(90)))
            addView(TextView(this@ContentDetailsActivity).apply {
                text = "${kindLabel(item.kind)}${if (item.group.isNotBlank()) "  •  ${item.group}" else ""}"
                textSize = 16f
                setTextColor(cyan)
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addView(TextView(this@ContentDetailsActivity).apply {
                text = "Conteúdo disponível na lista do painel."
                textSize = 16f
                setTextColor(muted)
            }, LinearLayout.LayoutParams(-1, dp(56)))
            addView(button("ASSISTIR", cyan, Color.BLACK) { openPlayer() }, LinearLayout.LayoutParams(dp(260), dp(58)))
        }, LinearLayout.LayoutParams(0, dp(336), 1f))
        root.addView(hero, LinearLayout.LayoutParams(-1, dp(350)))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(12)) }
        actions.addView(button("TRAILER", panel, white) { openTrailer() }, actionParams())
        favoriteButton = button("", panel, white) { toggleFavorite() }
        actions.addView(favoriteButton, actionParams())
        refreshFavoriteText()
        root.addView(actions)
        root.addView(TextView(this).apply {
            text = "Sinopse\n\n${item.name} está disponível na categoria ${item.group.ifBlank { "do catálogo" }}. A reprodução utiliza o player Native Media3 para TV Box."
            textSize = 17f
            setTextColor(white)
            setPadding(dp(6), dp(16), dp(6), 0)
        }, LinearLayout.LayoutParams(-1, dp(170)))
        item.logo?.let { loadPoster(it, poster) }
        return ScrollView(this).apply { addView(root) }
    }

    private fun button(label: String, background: Int, foreground: Int, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(foreground)
        setBackgroundColor(background)
        isFocusable = true
        setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else background) }
        setOnClickListener { action() }
    }

    private fun actionParams() = LinearLayout.LayoutParams(dp(220), dp(56)).apply { setMargins(dp(6), 0, dp(6), 0) }

    private fun openPlayer() {
        startActivity(android.content.Intent(this, PlayerActivity::class.java).putExtra("url", item.url).putExtra("title", item.name))
    }

    private fun openTrailer() {
        startActivity(android.content.Intent(this, TrailerActivity::class.java).putExtra("title", item.name))
    }

    private fun toggleFavorite() {
        FavoriteStore.toggle(this, item)
        refreshFavoriteText()
    }

    private fun refreshFavoriteText() {
        favoriteButton.text = if (FavoriteStore.contains(this, item)) "♥ FAVORITADO" else "♡ FAVORITAR"
        favoriteButton.setTextColor(if (FavoriteStore.contains(this, item)) magenta else white)
    }

    private fun loadPoster(url: String, target: ImageView) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 7000
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (bitmap != null && !isFinishing) target.setImageBitmap(bitmap)
        }
    }

    private fun kindLabel(kind: M3uItem.Kind): String = when (kind) {
        M3uItem.Kind.CHANNEL -> "Canal"
        M3uItem.Kind.MOVIE -> "Filme"
        M3uItem.Kind.SERIES -> "Série"
        M3uItem.Kind.KIDS -> "Kids"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
