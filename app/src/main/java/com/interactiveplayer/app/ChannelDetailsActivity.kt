package com.interactiveplayer.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

class ChannelDetailsActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val magenta = Color.rgb(255, 80, 180)
    private val panel = Color.rgb(28, 40, 70)
    private var player: ExoPlayer? = null
    private lateinit var favorite: TextView
    private lateinit var item: M3uItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = M3uItem(
            name = intent.getStringExtra("name").orEmpty().ifBlank { "Canal" },
            group = intent.getStringExtra("group").orEmpty(),
            logo = intent.getStringExtra("logo"),
            url = intent.getStringExtra("url").orEmpty(),
            kind = M3uItem.Kind.CHANNEL,
        )
        setContentView(buildView())
        startPreview()
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(20))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(white)
            gravity = Gravity.CENTER
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(60), dp(58)))
        header.addView(TextView(this).apply {
            text = item.name
            textSize = 22f
            setTextColor(white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(header)
        val preview = PlayerView(this).apply { useController = true; setBackgroundColor(Color.BLACK) }
        root.addView(preview, LinearLayout.LayoutParams(-1, dp(390)))
        root.addView(TextView(this).apply {
            text = "${item.group.ifBlank { "Canais" }}  •  EPG disponível quando enviado pelo painel"
            textSize = 16f
            setTextColor(muted)
            setPadding(dp(4), dp(16), dp(4), dp(12))
        }, LinearLayout.LayoutParams(-1, dp(52)))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(button("ASSISTIR", cyan, Color.BLACK) {
            startActivity(android.content.Intent(this@ChannelDetailsActivity, PlayerActivity::class.java).putExtra("url", item.url).putExtra("title", item.name))
        }, actionParams())
        favorite = button("", panel, white) { FavoriteStore.toggle(this@ChannelDetailsActivity, item); refreshFavorite() }
        actions.addView(favorite, actionParams())
        refreshFavorite()
        root.addView(actions)
        return root
    }

    private fun startPreview() {
        val view = (window.decorView as? android.view.ViewGroup)?.findViewById<PlayerView>(android.R.id.content)
        val playerView = findPlayerView(window.decorView)
        if (item.url.isBlank() || playerView == null) return
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaItem(MediaItem.fromUri(item.url))
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun findPlayerView(view: android.view.View): PlayerView? {
        if (view is PlayerView) return view
        if (view is android.view.ViewGroup) for (index in 0 until view.childCount) findPlayerView(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun refreshFavorite() {
        val active = FavoriteStore.contains(this, item)
        favorite.text = if (active) "♥ FAVORITADO" else "♡ FAVORITAR"
        favorite.setTextColor(if (active) magenta else white)
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

    private fun actionParams() = LinearLayout.LayoutParams(dp(230), dp(58)).apply { setMargins(dp(6), 0, dp(6), 0) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }
}
