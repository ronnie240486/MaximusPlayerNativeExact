package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
 * Série agora mostra temporada e lista de episódios de verdade (via
 * XtreamEpisodesClient) — antes tratava a série como um único stream,
 * e "ASSISTIR" tocava a URL da série em si, que não funciona na
 * maioria dos painéis Xtream (cada EPISÓDIO tem seu próprio stream_id).
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
    private lateinit var largeBackdrop: ImageView

    // Só usado quando item.kind == SERIES.
    private var seasons: List<XtreamEpisodesClient.Season> = emptyList()
    private var selectedSeasonKey: String? = null
    private lateinit var seasonSection: LinearLayout
    private lateinit var seasonRow: LinearLayout
    private lateinit var episodeList: LinearLayout
    private lateinit var episodeStatus: TextView
    private lateinit var watchButton: TextView

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
            streamId = intent.getStringExtra("streamId"),
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
        }.also { watchButton = it }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(Theme.SPACING_MD) })

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

        // Imagem de fundo grande (backdrop de verdade do filme/série) —
        // antes essa área ficava vazia; a única imagem usada era o
        // pôster pequeno lá em cima.
        largeBackdrop = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            clipToOutline = true
            visibility = View.GONE
        }
        root.addView(
            largeBackdrop,
            LinearLayout.LayoutParams(-1, dp(if (DeviceType.isTV(this)) 320 else 200)).apply {
                topMargin = dp(Theme.SPACING_MD)
            }
        )

        seasonSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val seasonScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        seasonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        seasonScroll.addView(seasonRow)
        seasonSection.addView(seasonScroll, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_MD)
            bottomMargin = dp(Theme.SPACING_SM)
        })
        episodeStatus = TextView(this).apply {
            setText("Carregando episódios...")
            textSize = 12f
            setTextColor(Theme.textMuted)
        }
        seasonSection.addView(episodeStatus)
        episodeList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        seasonSection.addView(episodeList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        root.addView(seasonSection, LinearLayout.LayoutParams(-1, -2))

        if (item.kind == M3uItem.Kind.SERIES) {
            seasonSection.visibility = View.VISIBLE
            loadEpisodes()
        }

        item.logo?.takeIf { it.isNotBlank() }?.let { loadImage(it, backdrop) }
        return ScrollView(this).apply { addView(root) }
    }

    private fun loadEpisodes() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { XtreamEpisodesClient.fetch(this@ContentDetailsActivity, item) }
            seasons = loaded
            if (loaded.isEmpty()) {
                episodeStatus.setText("Não encontramos episódios pra esta série no seu painel.")
                return@launch
            }
            episodeStatus.visibility = View.GONE
            selectedSeasonKey = loaded.first().key
            loaded.forEach { season -> seasonRow.addView(buildSeasonChip(season)) }
            renderEpisodes()
        }
    }

    private fun buildSeasonChip(season: XtreamEpisodesClient.Season): View =
        TextView(this).apply {
            setText("Temporada ${season.key}")
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(Theme.SPACING_SM), dp(8), dp(Theme.SPACING_SM), dp(8))
            isFocusable = true
            isClickable = true
            wireFocusHighlight(Theme.RADIUS_PILL)
            refreshSeasonChipColors(this, season.key)
            setOnClickListener {
                selectedSeasonKey = season.key
                for (i in 0 until seasonRow.childCount) {
                    val chip = seasonRow.getChildAt(i) as TextView
                    refreshSeasonChipColors(chip, seasons[i].key)
                }
                renderEpisodes()
            }
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) }
        }

    private fun refreshSeasonChipColors(chip: TextView, key: String) {
        val active = key == selectedSeasonKey
        chip.setTextColor(if (active) Theme.black else Theme.white)
        chip.background = roundRect(if (active) Theme.accentCyan else Theme.darkSurfaceAlt, Theme.RADIUS_PILL)
    }

    private fun renderEpisodes() {
        episodeList.removeAllViews()
        val episodes = seasons.firstOrNull { it.key == selectedSeasonKey }?.episodes.orEmpty()
        if (episodes.isEmpty()) {
            episodeList.addView(TextView(this).apply {
                setText("Nenhum episódio nesta temporada.")
                textSize = 12f
                setTextColor(Theme.textMuted)
            })
            return
        }
        episodes.forEach { episode -> episodeList.addView(buildEpisodeRow(episode)) }
    }

    private fun buildEpisodeRow(episode: XtreamEpisodesClient.Episode): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            setPadding(dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM), dp(Theme.SPACING_SM))
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener { openEpisode(episode) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }

        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_SM)
            clipToOutline = true
        }
        row.addView(thumb, LinearLayout.LayoutParams(dp(120), dp(72)).apply { rightMargin = dp(Theme.SPACING_SM) })
        val thumbUrl = episode.image?.takeIf { it.isNotBlank() } ?: item.logo
        thumbUrl?.takeIf { it.isNotBlank() }?.let { loadImage(it, thumb) }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            setText("${episode.episodeNumber}. ${episode.title}")
            textSize = 14f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 2
        })
        episode.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            texts.addView(TextView(this).apply {
                setText(plot)
                textSize = 12f
                setTextColor(Theme.textSecondary)
                maxLines = 2
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun openEpisode(episode: XtreamEpisodesClient.Episode) {
        val credentials = XtreamCredentials.load(this)
        val url = if (credentials != null) {
            XtreamEpisodesClient.episodeUrl(credentials, episode)
        } else {
            item.url
        }
        WatchHistoryStore.record(this, item)
        ChannelWatchStats.record(this, item)
        startActivity(
            android.content.Intent(this, PlayerActivity::class.java)
                .putExtra("url", url)
                .putExtra("title", "${item.name} — ${episode.title}")
        )
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
            info.backdrop?.takeIf { it.isNotBlank() }?.let {
                loadImage(it, largeBackdrop)
                largeBackdrop.visibility = View.VISIBLE
            }
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
        ChannelWatchStats.record(this, item)
        // Pra série, a URL da série em si geralmente não funciona nos
        // painéis Xtream — cada EPISÓDIO tem seu próprio stream. ASSISTIR
        // aqui toca o primeiro episódio disponível; se ainda não
        // carregou nenhum, cai de volta na URL antiga mesmo.
        if (item.kind == M3uItem.Kind.SERIES) {
            val firstEpisode = seasons.firstOrNull()?.episodes?.firstOrNull()
            if (firstEpisode != null) {
                openEpisode(firstEpisode)
                return
            }
        }
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
