package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.Intent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home portada de `frontend/app/home.tsx` do repositório Maximus.
 *
 * Todas as medidas abaixo saem do ramo `isTV` do original — lá o
 * componente escolhe entre três tamanhos (`isTV ? A : isLandscape ? B : C`)
 * e aqui, como o alvo é exclusivamente TV Box, ficou fixo no primeiro.
 */
class MainActivity : ComponentActivity() {

    // Cada medida tem a versão de TV e a de celular em paisagem, como no
    // home.tsx (`isTV ? A : isLandscape ? B : C`). Cravar só o ramo isTV
    // fazia o conteúdo estourar a tela do celular.
    private val sideNavWidth get() = if (isTv) 112 else 78
    private val sideNavItemWidth get() = if (isTv) 92 else 52
    private val sideNavItemPadV get() = if (isTv) 6 else 0
    private val sideNavItemGap get() = if (isTv) 4 else 2
    private val navIconSize get() = if (isTv) 26f else 18f
    private val navLabelSize get() = if (isTv) 12f else 8f
    private val posterWidth get() = if (isTv) 160 else 130
    private val posterHeight get() = posterWidth * 130 / 90
    private val circularSize get() = if (isTv) 110 else 88
    private val circularImgSize get() = if (isTv) 76 else 60
    private val posterNameSize get() = if (isTv) 15f else 12f
    private val circularNameSize get() = if (isTv) 14f else 11f
    private val rowGap = 10
    private val rowPaddingH = 16

    private val isTv: Boolean by lazy { DeviceType.isTV(this) }

    private lateinit var homeBody: LinearLayout
    private var heroIndex = 0
    private var heroItems: List<M3uItem> = emptyList()
    private var heroHost: FrameLayout? = null
    private var heroRotation: Job? = null
    private var currentWeather: WeatherClient.WeatherNow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiSound.init(this)
        setContentView(buildRoot())
        lifecycleScope.launch {
            val cachedItems = CatalogRepository.load(this@MainActivity)
            if (cachedItems.isNotEmpty()) renderCatalogHome(cachedItems)

            val freshItems = CatalogRepository.load(this@MainActivity, force = true)
            if (freshItems.isNotEmpty()) renderCatalogHome(freshItems)
        }
    }

    override fun onResume() {
        super.onResume()
        // O histórico pode ter mudado enquanto o player estava aberto.
        if (::homeBody.isInitialized) {
            lifecycleScope.launch {
                val items = CatalogRepository.load(this@MainActivity)
                if (items.isNotEmpty()) renderCatalogHome(items)
            }
        }
    }

    override fun onDestroy() {
        UiSound.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Estrutura geral: fundo com imagem + overlay escuro, sidebar à
    // esquerda e conteúdo rolável à direita (styles.bg / styles.overlay).
    // ---------------------------------------------------------------

    private fun buildRoot(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        val background = ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))

        // styles.overlay: rgba(0,0,0,0.55)
        root.addView(
            View(this).apply { setBackgroundColor(Color.argb(140, 0, 0, 0)) },
            FrameLayout.LayoutParams(-1, -1)
        )

        val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        columns.addView(buildSideNav(), LinearLayout.LayoutParams(dp(sideNavWidth), -1))
        // borderRightWidth: 1, borderRightColor: rgba(255,255,255,0.06)
        columns.addView(
            View(this).apply { setBackgroundColor(Theme.whiteAlpha06) },
            LinearLayout.LayoutParams(dp(1), -1)
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), 0, 0)   // styles.content + isLandscape paddingTop: 3
        }
        content.addView(buildTopBar())

        val scroll = ScrollView(this).apply { isFillViewport = true }
        homeBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        homeBody.addView(buildHero(emptyList()))
        homeBody.addView(buildSectionRow("CONTINUE ASSISTINDO", emptyList(), circular = false))
        homeBody.addView(buildSectionRow("CANAIS MAIS ASSISTIDOS", emptyList(), circular = true))
        homeBody.addView(buildSectionRow("FILMES EM ALTA", emptyList(), circular = false))
        homeBody.addView(buildSectionRow("SÉRIES POPULARES", emptyList(), circular = false))
        scroll.addView(homeBody)
        content.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        columns.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(columns, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    /**
     * Reconstruir a Home inteira (hero + 4 faixas, com toda a rede de
     * pôsteres) a cada `onResume` é o que fazia a tela travar ao voltar
     * de qualquer outra Activity. Como `CatalogRepository.load` sem
     * `force` devolve a MESMA lista em cache quando nada mudou, dá pra
     * comparar por referência e pular a reconstrução pesada — só a
     * faixa "Continue assistindo" (que pode ter mudado de verdade)
     * é atualizada sempre, sozinha.
     */
    private var lastRenderedCatalog: List<M3uItem>? = null

    private fun renderCatalogHome(items: List<M3uItem>) {
        if (!::homeBody.isInitialized || items.isEmpty()) return
        if (items === lastRenderedCatalog) {
            refreshContinueWatching()
            return
        }
        lastRenderedCatalog = items

        val movies = items.filter { it.kind == M3uItem.Kind.MOVIE }
        val series = items.filter { it.kind == M3uItem.Kind.SERIES }
        val channels = items.filter { it.kind == M3uItem.Kind.CHANNEL }
        val history = historyAsItems()

        homeBody.removeAllViews()
        homeBody.addView(buildHero((movies + series).take(12)))
        if (history.isNotEmpty()) {
            homeBody.addView(buildSectionRow("CONTINUE ASSISTINDO", history, circular = false).apply {
                tag = "continue"
            })
        }
        homeBody.addView(buildSectionRow("CANAIS MAIS ASSISTIDOS", channels.take(20), circular = true))
        homeBody.addView(buildSectionRow("FILMES EM ALTA", movies.take(20), circular = false))
        homeBody.addView(buildSectionRow("SÉRIES POPULARES", series.take(20), circular = false))
    }

    private fun historyAsItems(): List<M3uItem> =
        WatchHistoryStore.list(this).map { M3uItem(it.name, it.group, it.logo, it.url, it.kind) }

    /** Substitui só a faixa "Continue assistindo" no lugar, sem tocar no resto. */
    private fun refreshContinueWatching() {
        val history = historyAsItems()
        val existingIndex = (0 until homeBody.childCount).firstOrNull {
            homeBody.getChildAt(it).tag == "continue"
        }
        when {
            history.isEmpty() && existingIndex != null -> homeBody.removeViewAt(existingIndex)
            history.isNotEmpty() -> {
                val row = buildSectionRow("CONTINUE ASSISTINDO", history, circular = false).apply {
                    tag = "continue"
                }
                if (existingIndex != null) {
                    homeBody.removeViewAt(existingIndex)
                    homeBody.addView(row, existingIndex)
                } else {
                    // Logo depois do hero, que é sempre o primeiro filho.
                    homeBody.addView(row, minOf(1, homeBody.childCount))
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Sidebar (styles.sideNav + sideNavTV)
    // ---------------------------------------------------------------

    private fun buildSideNav(): View {
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Theme.darkSurfaceAlt)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(Theme.SPACING_SM), 0, dp(Theme.SPACING_SM))
        }

        val tabs = listOf(
            "home" to "Início",
            "tv" to "Canais",
            "film" to "Filmes",
            "series" to "Séries",
            "trophy" to "Placar",
            "kids" to "Kids",
            "radio" to "Rádios",
            "camera" to "Câmeras",
            "search" to "Busca",
            "diagnostic" to "Diagnóstico",
            "settings" to "Ajustes"
        )

        tabs.forEachIndexed { index, pair ->
            val iconName = pair.first
            val label = pair.second
            val isHome = index == 0

            val icon = TextView(this).apply {
                OriginalIcons.apply(this, iconName)
                textSize = navIconSize
                gravity = Gravity.CENTER
                setTextColor(if (isHome) Theme.accentCyan else Theme.textSecondary)
            }
            val text = TextView(this).apply {
                setText(label)
                textSize = navLabelSize
                gravity = Gravity.CENTER
                setTextColor(if (isHome) Theme.accentCyan else Theme.textSecondary)
                setTypeface(Typeface.DEFAULT_BOLD)
                maxLines = 1
            }

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(sideNavItemPadV), 0, dp(sideNavItemPadV))
                isFocusable = true
                isClickable = true
                addView(icon, LinearLayout.LayoutParams(-1, -2))
                addView(
                    text,
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(sideNavItemGap) }
                )
                background = focusBackground()
                setOnFocusChangeListener { _, focused ->
                    val highlight = focused || isHome
                    icon.setTextColor(if (highlight) Theme.accentCyan else Theme.textSecondary)
                    text.setTextColor(if (highlight) Theme.accentCyan else Theme.textSecondary)
                }
                setOnClickListener {
                    UiSound.click()
                    openNavTarget(label)
                }
            }

            inner.addView(
                item,
                LinearLayout.LayoutParams(dp(sideNavItemWidth), -2).apply {
                    bottomMargin = dp(Theme.SPACING_SM)   // sideNavInner gap: 8
                }
            )
        }

        scroll.addView(inner)
        return scroll
    }

    private fun openNavTarget(label: String) {
        val kind = when (label) {
            "Canais" -> "CHANNEL"
            "Filmes" -> "MOVIE"
            "Séries" -> "SERIES"
            "Kids" -> "KIDS"
            else -> null
        }
        when {
            kind != null ->
                startActivity(
                    Intent(this@MainActivity, CatalogActivity::class.java).putExtra("mode", kind)
                )
            label == "Busca" -> startActivity(Intent(this@MainActivity, SearchActivity::class.java))
            label == "Rádios" -> startActivity(Intent(this@MainActivity, RadioActivity::class.java))
            label == "Placar" -> startActivity(Intent(this@MainActivity, ScoreActivity::class.java))
            label == "Câmeras" ->
                startActivity(Intent(this@MainActivity, WorldCamerasActivity::class.java))
            label == "Ajustes" ->
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            label == "Diagnóstico" ->
                startActivity(Intent(this@MainActivity, DiagnosticActivity::class.java))
        }
    }

    // ---------------------------------------------------------------
    // Barra superior (styles.topBar)
    // ---------------------------------------------------------------

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), dp(4))
        }

        val greeting = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        greeting.addView(TextView(this).apply {
            setText(greetingText())
            textSize = 14f                              // isLandscape fontSize: 14
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        })
        greeting.addView(TextView(this).apply {
            setText("Maximus player")
            textSize = 9f                               // isLandscape fontSize: 9
            setTextColor(Theme.accentCyan)
            letterSpacing = letterSpacingEm(1.5f, 9f)   // appNameSmall letterSpacing: 1.5
            maxLines = 1
        })
        bar.addView(greeting, LinearLayout.LayoutParams(0, -2, 1f))

        // styles.wrapTVCompact do ClockWeather: hora + ícone + temperatura.
        val clock = TextView(this)
        clock.setText(currentTime())
        clock.textSize = 12f
        clock.setTextColor(Theme.white)
        clock.setTypeface(Typeface.DEFAULT_BOLD)
        clock.gravity = Gravity.CENTER
        clock.background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
        clock.setPadding(dp(Theme.SPACING_SM), dp(6), dp(Theme.SPACING_SM), dp(6))
        bar.addView(
            clock,
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(Theme.SPACING_SM) }
        )
        startClockAndWeather(clock)

        // styles.micBtn: 34x34, círculo com borda ciano
        bar.addView(TextView(this).apply {
            OriginalIcons.apply(this, "search")
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.accentCyan)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Theme.darkSurfaceAlt)
                setStroke(dp(1), Theme.accentCyan)
            }
            isFocusable = true
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SearchActivity::class.java))
            }
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(Theme.SPACING_SM) })

        bar.addView(ImageView(this).apply {
            setImageBitmap(assetBitmap("app-image.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            isFocusable = true
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ProfilesActivity::class.java))
            }
        }, LinearLayout.LayoutParams(dp(34), dp(34)))

        return bar
    }

    /** Sem perfil salvo, a saudação fica só "Olá" — sem nome inventado. */
    private fun greetingText(): String {
        val name = runCatching { ProfileStore(this).all().firstOrNull()?.name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return if (name == null) "Olá" else "Olá, $name"
    }

    private fun currentTime(): String =
        SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())

    /**
     * Relógio a cada 30s e clima a cada 20 min — os mesmos intervalos do
     * ClockWeather.tsx (`WEATHER_REFRESH_MS = 20 * 60 * 1000`).
     */
    private fun startClockAndWeather(clock: TextView) {
        lifecycleScope.launch {
            while (true) {
                clock.setText(buildClockText())
                delay(30_000)
            }
        }
        lifecycleScope.launch {
            while (true) {
                val weather = withContext(Dispatchers.IO) {
                    val location = WeatherClient.fetchLocationByIp()
                    if (location == null) null
                    else WeatherClient.fetchWeather(location.lat, location.lon)
                }
                if (weather != null) {
                    currentWeather = weather
                    clock.setText(buildClockText())
                }
                delay(20 * 60 * 1000L)
            }
        }
    }

    private fun buildClockText(): String {
        val weather = currentWeather ?: return currentTime()
        return "${currentTime()}   ${WeatherClient.weatherIcon(weather.code)} ${weather.tempC}°"
    }

    // ---------------------------------------------------------------
    // Hero (styles.heroBg com aspectRatio 16/6 em paisagem)
    // ---------------------------------------------------------------

    private fun buildHero(items: List<M3uItem>): View {
        heroItems = items
        heroIndex = 0
        heroRotation?.cancel()

        val contentWidth = resources.displayMetrics.widthPixels - dp(sideNavWidth) - dp(1)
        val heroHeight = contentWidth * 6 / 16      // aspectRatio: 16 / 6

        val host = FrameLayout(this).apply { setBackgroundColor(Theme.darkSurface) }
        heroHost = host
        host.layoutParams = LinearLayout.LayoutParams(-1, heroHeight).apply {
            bottomMargin = dp(Theme.SPACING_LG)     // heroWrap marginBottom
        }
        renderHero()

        // "Gira sozinho a cada 7s, volta pro começo depois do último."
        if (items.size > 1) {
            heroRotation = lifecycleScope.launch {
                while (true) {
                    delay(7000)
                    heroIndex = (heroIndex + 1) % heroItems.size
                    renderHero()
                }
            }
        }
        return host
    }

    private fun renderHero() {
        val host = heroHost ?: return
        val current = heroItems.getOrNull(heroIndex)
        host.removeAllViews()

        val backdrop = ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.9f                            // imageStyle opacity: 0.9
        }
        host.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        current?.logo?.takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, backdrop) }

        // LinearGradient vertical: 0.10 -> 0.40 -> 0.90 de colors.black
        host.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.argb(26, 11, 15, 26),
                    Color.argb(102, 11, 15, 26),
                    Color.argb(230, 11, 15, 26)
                )
            )
        }, FrameLayout.LayoutParams(-1, -1))

        // LinearGradient horizontal: 0.45 -> transparente
        host.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(115, 11, 15, 26), Color.TRANSPARENT)
            )
        }, FrameLayout.LayoutParams(-1, -1))

        host.addView(
            buildHeroContent(current, heroItems.size, null),
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        )

        // Sinopse/nota/ano/backdrop chegam depois, sem travar a pintura —
        // igual ao useEffect do hero no home.tsx, que busca só o item que
        // está na tela agora e guarda em cache.
        if (current != null) {
            val requestedIndex = heroIndex
            lifecycleScope.launch {
                val info = withContext(Dispatchers.IO) { ContentInfoClient.fetch(this@MainActivity, current) }
                if (info == null || isFinishing) return@launch
                if (heroIndex != requestedIndex) return@launch
                val stillThere = heroHost ?: return@launch
                if (stillThere.childCount >= 4) {
                    stillThere.removeViewAt(stillThere.childCount - 1)
                    stillThere.addView(
                        buildHeroContent(current, heroItems.size, info),
                        FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
                    )
                }
                info.backdrop?.takeIf { it.isNotBlank() }?.let {
                    val image = stillThere.getChildAt(0)
                    if (image is ImageView) loadRemoteImage(it, image)
                }
            }
        }
    }

    private fun buildHeroContent(
        current: M3uItem?,
        total: Int,
        info: ContentInfoClient.Info?
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_LG)
            )
        }

        // styles.heroBadge
        box.addView(TextView(this).apply {
            setText(if (current?.kind == M3uItem.Kind.SERIES) "SÉRIE" else "FILME")
            textSize = 10f
            setTextColor(Theme.black)
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = letterSpacingEm(0.5f, 10f)
            background = roundRect(Theme.accentCyan, 4)
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })

        // styles.heroTitle
        box.addView(TextView(this).apply {
            setText(current?.name ?: "Carregando seu catálogo…")
            textSize = 26f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 2
        })

        // styles.heroMetaRow: estrela + nota, ano, pílula de qualidade
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        info?.rating?.takeIf { it.isNotBlank() }?.let { rating ->
            metaRow.addView(TextView(this).apply {
                setText("★ $rating")
                textSize = 12f
                setTextColor(Theme.star)
                setTypeface(Typeface.DEFAULT_BOLD)
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        }
        info?.year?.takeIf { it.isNotBlank() }?.let { year ->
            metaRow.addView(TextView(this).apply {
                setText(year)
                textSize = 12f
                setTextColor(Theme.textSecondary)
                setTypeface(Typeface.DEFAULT_BOLD)
            }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        }
        metaRow.addView(TextView(this).apply {
            setText("HD")
            textSize = 10f
            setTextColor(Theme.textSecondary)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = roundStroke(Theme.textSecondary, 4)
            setPadding(dp(6), dp(1), dp(6), dp(1))
        })
        box.addView(metaRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // styles.heroPlot
        val plot = info?.plot?.takeIf { it.isNotBlank() } ?: current?.group?.takeIf { it.isNotBlank() }
        if (plot != null) {
            box.addView(TextView(this).apply {
                setText(plot)
                textSize = 13f
                setTextColor(Theme.textSecondary)
                maxLines = 3
                setLineSpacing(dpF(6f), 1f)      // lineHeight 19 sobre fonte 13
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }

        // styles.heroActions
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(TextView(this).apply {
            setText("▶  ASSISTIR")
            textSize = 13f
            setTextColor(Theme.black)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            background = roundRect(Theme.accentCyan, Theme.RADIUS_SM)
            setPadding(dp(18), dp(10), dp(18), dp(10))
            isFocusable = true
            isClickable = true
            setOnClickListener { current?.let { openItem(it) } }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })

        actions.addView(TextView(this).apply {
            setText("▷  TRAILER")
            textSize = 13f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            background = roundRect(Theme.whiteAlpha12, Theme.RADIUS_SM)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            isFocusable = true
            isClickable = true
            setOnClickListener {
                current?.let {
                    startActivity(
                        Intent(this@MainActivity, TrailerActivity::class.java)
                            .putExtra("title", it.name)
                    )
                }
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })

        // styles.heroIconBtn: 38x38 redondo
        val heart = TextView(this)
        val alreadyFavorite = current != null && FavoriteStore.contains(this, current)
        heart.setText("♥")
        heart.textSize = 16f
        heart.setTextColor(if (alreadyFavorite) Theme.accentMagenta else Theme.white)
        heart.gravity = Gravity.CENTER
        heart.background = circleDrawable(Theme.whiteAlpha12)
        heart.isFocusable = true
        heart.isClickable = true
        heart.setOnClickListener {
            if (current != null) {
                val nowFavorite = FavoriteStore.toggle(this@MainActivity, current)
                heart.setTextColor(if (nowFavorite) Theme.accentMagenta else Theme.white)
            }
        }
        actions.addView(heart, LinearLayout.LayoutParams(dp(38), dp(38)))

        box.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_MD)
        })

        // styles.heroDots
        if (total > 1) {
            val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (index in 0 until total) {
                val active = index == heroIndex
                val dot = View(this)
                dot.background = roundRect(
                    if (active) Theme.accentCyan else Theme.whiteAlpha30,
                    3
                )
                val position = index
                dot.isFocusable = true
                dot.isClickable = true
                dot.setOnClickListener {
                    heroIndex = position
                    renderHero()
                }
                dots.addView(
                    dot,
                    LinearLayout.LayoutParams(dp(if (active) 18 else 6), dp(6)).apply {
                        rightMargin = dp(6)
                    }
                )
            }
            box.addView(dots, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(Theme.SPACING_MD)
            })
        }

        return box
    }

    // ---------------------------------------------------------------
    // Faixas horizontais (SectionRow do home.tsx)
    // ---------------------------------------------------------------

    private fun buildSectionRow(
        title: String,
        items: List<M3uItem>,
        circular: Boolean
    ): View {
        val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // styles.sectionHeader
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(rowPaddingH), 0, dp(rowPaddingH), 0)
        }
        header.addView(View(this).apply {
            background = roundRect(Theme.accentCyan, 2)
        }, LinearLayout.LayoutParams(dp(3), dp(14)).apply { rightMargin = dp(Theme.SPACING_SM) })
        header.addView(TextView(this).apply {
            setText(title)
            textSize = 14f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = letterSpacingEm(1f, 14f)
        })
        section.addView(header, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(rowPaddingH), 0, dp(rowPaddingH), 0)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { item ->
            row.addView(
                if (circular) buildCircularItem(item) else buildPosterItem(item),
                LinearLayout.LayoutParams(
                    dp(if (circular) circularSize else posterWidth),
                    -2
                ).apply { rightMargin = dp(rowGap) }
            )
        }
        scroll.addView(row)
        section.addView(scroll, LinearLayout.LayoutParams(-1, -2))

        section.layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(18)       // styles.section marginBottom
        }
        return section
    }

    /** styles.posterItem / posterCard / posterName no ramo isTV. */
    private fun buildPosterItem(item: M3uItem): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            background = focusBackground()
            setOnClickListener {
                UiSound.click()
                openItem(item)
            }
        }

        val card = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            clipToOutline = true
        }
        item.logo?.takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, card) }
        box.addView(card, LinearLayout.LayoutParams(dp(posterWidth), dp(posterHeight)))

        box.addView(TextView(this).apply {
            setText(item.name)
            textSize = posterNameSize
            setTextColor(Theme.white)
            maxLines = 1
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        return box
    }

    /** styles.circularItem / circularCard / circularName no ramo isTV. */
    private fun buildCircularItem(item: M3uItem): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            background = focusBackground()
            setOnClickListener {
                UiSound.click()
                openItem(item)
            }
        }

        val card = FrameLayout(this).apply {
            background = circleDrawable(Theme.white)
        }
        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER   // contentFit: "contain"
        }
        item.logo?.takeIf { it.isNotBlank() }?.let { loadRemoteImage(it, logo) }
        card.addView(
            logo,
            FrameLayout.LayoutParams(dp(circularImgSize), dp(circularImgSize), Gravity.CENTER)
        )
        box.addView(card, LinearLayout.LayoutParams(dp(circularSize), dp(circularSize)))

        box.addView(TextView(this).apply {
            setText(item.name)
            textSize = circularNameSize
            setTextColor(Theme.textSecondary)
            gravity = Gravity.CENTER
            maxLines = 1
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        return box
    }

    /**
     * Equivale ao `defaultFocus` do TVFocusable: contorno ciano de 2dp
     * quando o item está em foco pelo D-pad.
     */
    private fun focusBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            cornerRadius = dpF(Theme.RADIUS_SM.toFloat())
            setStroke(dp(2), Color.TRANSPARENT)
        }

    // ---------------------------------------------------------------

    private fun openItem(item: M3uItem) {
        WatchHistoryStore.record(this, item)
        if (item.kind == M3uItem.Kind.CHANNEL) {
            startActivity(Intent(this, ChannelDetailsActivity::class.java).apply {
                putExtra("name", item.name)
                putExtra("group", item.group)
                putExtra("logo", item.logo)
                putExtra("url", item.url)
            })
        } else {
            startActivity(Intent(this, ContentDetailsActivity::class.java).apply {
                putExtra("name", item.name)
                putExtra("group", item.group)
                putExtra("logo", item.logo)
                putExtra("url", item.url)
                putExtra("kind", item.kind.name)
            })
        }
    }

    private fun loadRemoteImage(url: String, target: ImageView) {
        // O tamanho real do destino (já definido no layout) diz quanto
        // dá pra reduzir a imagem na decodificação — evita carregar um
        // pôster de 1200x1600 pixels pra um card de 130dp de largura.
        val reqWidth = target.layoutParams?.width?.takeIf { it > 0 } ?: dp(posterWidth)
        val reqHeight = target.layoutParams?.height?.takeIf { it > 0 } ?: dp(posterHeight)
        lifecycleScope.launch {
            val bitmap = ImageLoader.load(url, reqWidth, reqHeight)
            if (bitmap != null && !isFinishing) target.setImageBitmap(bitmap)
        }
    }

    // Decodificar default-bg.png de novo a cada rotação do hero (a cada
    // 7s) travava visivelmente a Home — agora só decodifica uma vez por
    // nome de arquivo e reaproveita o mesmo Bitmap depois.
    private val assetBitmapCache = HashMap<String, android.graphics.Bitmap?>()

    private fun assetBitmap(name: String) = assetBitmapCache.getOrPut(name) {
        runCatching { assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    }
}
