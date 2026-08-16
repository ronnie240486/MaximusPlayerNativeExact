package com.maximus.nativeexact

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

class FavoritesActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val panel = Color.rgb(28, 40, 70)
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "‹  Favoritos"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, dp(18)) }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun render() {
        list.removeAllViews()
        val favorites = FavoriteStore.list(this)
        if (favorites.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nenhum favorito salvo ainda.\nUse o botão ♡ nos conteúdos para adicionar."
                textSize = 19f
                setTextColor(muted)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(80), dp(20), dp(80))
            }, LinearLayout.LayoutParams(-1, dp(220)))
            return
        }
        favorites.forEach { favorite -> renderFavorite(favorite) }
        list.getChildAt(0)?.requestFocus()
    }

    private fun renderFavorite(favorite: FavoriteStore.Favorite) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(12), dp(8), dp(10), dp(8))
            isFocusable = true
            setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(45, 75, 100) else panel) }
            setOnClickListener { open(favorite) }
        }
        val poster = ImageView(this).apply {
            setBackgroundColor(Color.rgb(35, 48, 80))
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = favorite.name
        }
        row.addView(poster, LinearLayout.LayoutParams(dp(64), dp(78)))
        row.addView(TextView(this).apply {
            text = "${favorite.name}\n${favorite.kindLabel()}${if (favorite.group.isNotBlank()) "  •  ${favorite.group}" else ""}"
            textSize = 16f
            setTextColor(white)
            maxLines = 3
            setPadding(dp(14), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
        row.addView(TextView(this).apply {
            text = "REMOVER"
            textSize = 13f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { FavoriteStore.remove(this@FavoritesActivity, favorite); render() }
        }, LinearLayout.LayoutParams(dp(110), dp(52)))
        list.addView(row, LinearLayout.LayoutParams(-1, dp(94)).apply { setMargins(0, 0, 0, dp(8)) })
        favorite.logo?.let { loadPoster(it, poster) }
    }

    private fun open(favorite: FavoriteStore.Favorite) {
        startActivity(android.content.Intent(this, PlayerActivity::class.java).putExtra("url", favorite.url).putExtra("title", favorite.name))
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

    private fun FavoriteStore.Favorite.kindLabel(): String = when (kind) {
        M3uItem.Kind.CHANNEL -> "Canal"
        M3uItem.Kind.MOVIE -> "Filme"
        M3uItem.Kind.SERIES -> "Série"
        M3uItem.Kind.KIDS -> "Kids"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
