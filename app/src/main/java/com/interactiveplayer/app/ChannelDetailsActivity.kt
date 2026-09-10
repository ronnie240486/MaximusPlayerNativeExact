package com.interactiveplayer.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Detalhe de canal, com as cores corretas do Theme no lugar da paleta
 * aproximada anterior. Mantém o preview ao vivo via ExoPlayer, que já
 * existia e funciona bem.
 */
class ChannelDetailsActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var favoriteButton: TextView
    private lateinit var item: M3uItem
    private lateinit var playerView: PlayerView

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
            setBackgroundColor(Theme.black)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_LG)
            )
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            setText("‹")
            textSize = 24f
            setTextColor(Theme.white)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText(item.name)
            textSize = 18f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Theme.SPACING_SM) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        })

        playerView = PlayerView(this).apply {
            useController = true
            setBackgroundColor(Theme.black)
            background = roundRect(Theme.black, Theme.RADIUS_MD)
        }
        val previewHeight = (resources.displayMetrics.widthPixels * 0.5).toInt()
        root.addView(playerView, LinearLayout.LayoutParams(-1, previewHeight).apply {
            bottomMargin = dp(Theme.SPACING_MD)
        })

        root.addView(TextView(this).apply {
            setText(item.group.ifBlank { "Canais" } + "  •  EPG disponível quando enviado pelo painel")
            textSize = 13f
            setTextColor(Theme.textSecondary)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.SPACING_MD) })

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        actions.addView(actionButton("ASSISTIR EM TELA CHEIA", Theme.accentCyan, Theme.black) {
            startActivity(
                android.content.Intent(this, PlayerActivity::class.java)
                    .putExtra("url", item.url)
                    .putExtra("title", item.name)
            )
        }, actionParams())

        favoriteButton = actionButton("", Theme.darkSurfaceAlt, Theme.white) {
            FavoriteStore.toggle(this, item)
            refreshFavorite()
        }
        actions.addView(favoriteButton, actionParams())
        refreshFavorite()
        root.addView(actions)
        return root
    }

    private fun refreshFavorite() {
        val active = FavoriteStore.contains(this, item)
        favoriteButton.setText(if (active) "♥ FAVORITADO" else "♡ FAVORITAR")
        favoriteButton.setTextColor(if (active) Theme.accentMagenta else Theme.white)
    }

    private fun actionButton(label: String, background: Int, foreground: Int, action: () -> Unit): TextView =
        TextView(this).apply {
            setText(label)
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setTextColor(foreground)
            this.background = roundRect(background, Theme.RADIUS_SM)
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
        }

    private fun actionParams() = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
        leftMargin = dp(6)
        rightMargin = dp(6)
    }

    private fun startPreview() {
        if (item.url.isBlank()) return
        player = ExoPlayer.Builder(this).build().also {
            playerView.player = it
            it.setMediaItem(MediaItem.fromUri(item.url))
            it.prepare()
            it.playWhenReady = true
        }
    }

    override fun onStop() {
        player?.release()
        player = null
        super.onStop()
    }
}
