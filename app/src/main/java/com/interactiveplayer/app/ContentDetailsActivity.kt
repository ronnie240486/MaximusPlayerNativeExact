package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
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

/**
 * Detalhe de filme/série/kids, portado de `movie-details.tsx` e
 * `series-details.tsx`. Busca sinopse, nota, ano e backdrop de verdade
 * via XtreamInfoClient — o mesmo cliente que alimenta o hero da Home —
 * em vez do texto fixo "está disponível na categoria X" de antes.
 *
 * Não replica a lista de temporadas/episódios de `series-details.tsx`:
 * cada série do catálogo aqui é um único stream, sem quebra por episódio.
 */
class ContentDetailsActivity : ComponentActivity() {

    private lateinit var favoriteButton: TextView
    // Preenchido quando ContentInfoClient.fetch() responde, se o painel
    // tiver o trailer certo (campo youtube_trailer do Xtream).
    private var youtubeTrailerId: String? = null
    private lateinit var item: M3uItem
    private lateinit var plotText: TextView
    private lateinit var metaRow: LinearLayout
    private lateinit var backdrop: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item = M3uItem(
            name = intent.getStringExtra("name").orEmpty().ifBlank { "Conteúdo" },
            group = intent.getStringExtra("group").orEmpty(),
            logo = intent.getStringExtra("logo"),
            url = intent.getStringExtra("url").orEmpty(),
            kind = runCatching {
                M3uItem.Kind.valueOf(intent.getStringExtra("kind") ?: "MOVIE")
            }.getOrDefault(M3uItem.Kind.MOVIE),
        )
        setContentView(buildView())
        loadExtraInfo()
    }

    private fun buildView(): ScrollView {
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
            wireFocusHighlightCircle()
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText(item.name)
            textSize = 16f
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Theme.SPACING_SM) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_MD)
        })

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val posterBox = FrameLayout(this).apply {
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            clipToOutline = true
        }
        backdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        posterBox.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        hero.addView(posterBox, LinearLayout.LayoutParams(dp(140), dp(200)))

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, 0, 0)
        }
        info.addView(TextView(this).apply {
            setText(if (item.kind == M3uItem.Kind.SERIES) "SÉRIE" else "FILME")
            textSize = 10f
            setTextColor(Theme.black)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            background = roundRect(Theme.accentCyan, 4)
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })

        info.addView(TextView(this).apply {
            setText(item.name)
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 3
        })

        metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        info.addView(metaRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        item.group.takeIf { it.isNotBlank() }?.let { group ->
            info.addView(TextView(this).apply {
                setText(group)
                textSize = 12f
                setTextColor(Theme.textSecondary)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }

        info.addView(TextView(this).apply {
            setText("▶  ASSISTIR")
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setTextColor(Theme.black)
            background = roundRect(Theme.accentCyan, Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_MD), dp(10), dp(Theme.SPACING_MD), dp(10))
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener { openPlayer() }
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(Theme.SPACING_MD) })

        hero.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(hero)

        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_SM))
        }
        actions.addView(secondaryButton("▷  TRAILER") { openTrailer() }, actionParams())
        favoriteButton = secondaryButton("") { toggleFavorite() }
        actions.addView(favoriteButton, actionParams())
        refreshFavoriteText()
        root.addView(actions)

        root.addView(TextView(this).apply {
            setText("SINOPSE")
            textSize = 11f
            setTextColor(Theme.textMuted)
            letterSpacing = letterSpacingEm(1.5f, 11f)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Theme.SPACING_SM) })

        plotText = TextView(this).apply {
            setText("Carregando sinopse...")
            textSize = 14f
            setTextColor(Theme.white)
            setLineSpacing(dpF(6f), 1f)
        }
        root.addView(plotText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        item.logo?.takeIf { it.isNotBlank() }?.let { loadImage(it, backdrop) }
        return ScrollView(this).apply { addView(root) }
    }

    private fun loadExtraInfo() {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { ContentInfoClient.fetch(this@ContentDetailsActivity, item) }
            if (info == null || isFinishing) {
                plotText.setText("Conteúdo disponível na lista do painel.")
                return@launch
            }
            plotText.setText(info.plot?.takeIf { it.isNotBlank() } ?: "Sem sinopse disponível para este título.")
            youtubeTrailerId = info.youtubeTrailer

            info.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                metaRow.addView(TextView(this@ContentDetailsActivity).apply {
                    setText("★ $rating")
                    textSize = 12f
                    setTextColor(Theme.star)
                    setTypeface(Typeface.DEFAULT_BOLD)
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(Theme.SPACING_SM) })
            }
            info.year?.takeIf { it.isNotBlank() }?.let { year ->
                metaRow.addView(TextView(this@ContentDetailsActivity).apply {
                    setText(year)
                    textSize = 12f
                    setTextColor(Theme.textSecondary)
                })
            }
            info.backdrop?.takeIf { it.isNotBlank() }?.let { loadImage(it, backdrop) }
        }
    }

    private fun secondaryButton(label: String, action: () -> Unit): TextView =
        TextView(this).apply {
            setText(label)
            textSize = 13f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setTextColor(Theme.white)
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_SM)
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener { action() }
        }

    private fun actionParams() = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        leftMargin = dp(6)
        rightMargin = dp(6)
    }

    private fun openPlayer() {
        WatchHistoryStore.record(this, item)
        startActivity(
            android.content.Intent(this, PlayerActivity::class.java)
                .putExtra("url", item.url)
                .putExtra("title", item.name)
        )
    }

    private fun openTrailer() {
        startActivity(
            android.content.Intent(this, TrailerActivity::class.java)
                .putExtra("title", item.name)
                .putExtra("videoId", youtubeTrailerId)
                .putExtra("query", "${item.name} trailer oficial")
        )
    }

    private fun toggleFavorite() {
        FavoriteStore.toggle(this, item)
        refreshFavoriteText()
    }

    private fun refreshFavoriteText() {
        val active = FavoriteStore.contains(this, item)
        favoriteButton.setText(if (active) "♥ FAVORITADO" else "♡ FAVORITAR")
        favoriteButton.setTextColor(if (active) Theme.accentMagenta else Theme.white)
    }

    private fun loadImage(url: String, target: ImageView) {
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
}
