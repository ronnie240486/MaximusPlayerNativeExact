package com.interactiveplayer.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Portado 1:1 de `frontend/src/theme.ts` do repositório Maximus
 * (React Native). Os valores aqui são a fonte de verdade do visual —
 * antes cada Activity inventava as próprias cores aproximadas, o que
 * deixava o app nativo visivelmente diferente do original.
 */
object Theme {
    val black = Color.parseColor("#0B0F1A")
    val darkSurface = Color.parseColor("#161B2E")
    val darkSurfaceAlt = Color.parseColor("#1E2438")
    val accentCyan = Color.parseColor("#4CE8F0")
    val accentMagenta = Color.parseColor("#F04CC8")
    val accentPurple = Color.parseColor("#B14CF0")
    val textSecondary = Color.parseColor("#9AA3B8")
    val textMuted = Color.parseColor("#5C647A")
    val white = Color.parseColor("#FFFFFF")
    val danger = Color.parseColor("#F0997B")

    /** Amarelo da estrela de nota no hero (`#F0C24C` no home.tsx). */
    val star = Color.parseColor("#F0C24C")

    /** `rgba(255,255,255,0.12)` — fundo dos botões secundários do hero. */
    val whiteAlpha12 = Color.argb(31, 255, 255, 255)

    /** `rgba(255,255,255,0.3)` — bolinha inativa do carrossel. */
    val whiteAlpha30 = Color.argb(77, 255, 255, 255)

    /** `rgba(255,255,255,0.06)` — borda divisória da sidebar. */
    val whiteAlpha06 = Color.argb(15, 255, 255, 255)

    // spacing do theme.ts
    const val SPACING_XS = 4
    const val SPACING_SM = 8
    const val SPACING_MD = 16
    const val SPACING_LG = 24
    const val SPACING_XL = 32

    // radius do theme.ts
    const val RADIUS_SM = 8
    const val RADIUS_MD = 12
    const val RADIUS_LG = 16
    const val RADIUS_PILL = 999
}

/** Converte dp para pixels físicos. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Converte dp para pixels físicos, preservando a fração. */
fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

/** Retângulo com cantos arredondados — equivale a `borderRadius` no RN. */
fun Context.roundRect(color: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dpF(radiusDp.toFloat())
    }

/** Retângulo arredondado só com contorno — equivale a `borderWidth` no RN. */
fun Context.roundStroke(strokeColor: Int, radiusDp: Int, strokeDp: Int = 1): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        cornerRadius = dpF(radiusDp.toFloat())
        setStroke(dp(strokeDp), strokeColor)
    }

/**
 * Contorno de foco do D-pad, de verdade — usa `foreground` (não
 * `background`) pra não atropelar o fundo que a view já tiver, e liga
 * um `OnFocusChangeListener` que muda a cor da borda de verdade.
 *
 * Antes existiam vários "focusBackground()"/"focusOutline()" espalhados
 * pelo app que desenhavam uma borda TRANSPARENTE e nunca a trocavam de
 * cor em lugar nenhum — ou seja, navegar pelo D-pad nunca mostrava onde
 * o foco estava. Essa função substitui esse padrão.
 */
fun View.wireFocusHighlight(radiusDp: Int = Theme.RADIUS_SM) {
    isFocusable = true
    val ring = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        cornerRadius = context.dpF(radiusDp.toFloat())
        setStroke(context.dp(2), Color.TRANSPARENT)
    }
    foreground = ring
    setOnFocusChangeListener { _, focused ->
        ring.setStroke(context.dp(2), if (focused) Theme.accentCyan else Color.TRANSPARENT)
    }
}

/** Mesma ideia, pros botões redondos (ícones do player). */
fun View.wireFocusHighlightCircle() {
    isFocusable = true
    val ring = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
        setStroke(context.dp(2), Color.TRANSPARENT)
    }
    foreground = ring
    setOnFocusChangeListener { _, focused ->
        ring.setStroke(context.dp(2), if (focused) Theme.accentCyan else Color.TRANSPARENT)
    }
}

/** Círculo sólido — usado nos logos de canal e nos botões redondos. */
fun circleDrawable(color: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

/**
 * `letterSpacing` no Android é medido em "em" (fração do tamanho da
 * fonte), enquanto no React Native é medido em dp. Esta função faz a
 * conversão para que o espaçamento saia igual ao do original.
 */
fun letterSpacingEm(spacingDp: Float, fontSizeSp: Float): Float = spacingDp / fontSizeSp
