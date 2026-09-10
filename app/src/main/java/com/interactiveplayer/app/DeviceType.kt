package com.interactiveplayer.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Portado de `frontend/src/hooks/useIsTV.ts`.
 *
 * O layout do Maximus escolhe medidas diferentes conforme o aparelho
 * (`isTV ? A : isLandscape ? B : C`). Ao portar as telas eu vinha
 * cravando o ramo isTV em tudo, porque o alvo é TV box — mas isso
 * quebra no celular, onde a tela em paisagem tem cerca de 360dp de
 * altura e margens pensadas pro overscan de TV engolem o conteúdo.
 */
object DeviceType {

    private var cached: Boolean? = null

    fun isTV(context: Context): Boolean {
        cached?.let { return it }

        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val byUiMode = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val packageManager = context.packageManager
        val byFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature("android.hardware.type.television")

        // Aparelho sem tela sensível ao toque é, na prática, TV box.
        val noTouch = !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        val result = byUiMode || byFeature || noTouch
        cached = result
        return result
    }

    /**
     * Escolhe entre o valor de TV e o de celular, do mesmo jeito que o
     * original faz com `isTV ? a : b`.
     */
    fun <T> pick(context: Context, tv: T, phone: T): T = if (isTV(context)) tv else phone
}

/** Atalho: `dpTV(88, 48)` devolve 88dp na TV e 48dp no celular. */
fun Context.dpTV(tvValue: Int, phoneValue: Int): Int =
    dp(if (DeviceType.isTV(this)) tvValue else phoneValue)

/** Mesma ideia, para tamanhos de fonte em sp. */
fun Context.spTV(tvValue: Float, phoneValue: Float): Float =
    if (DeviceType.isTV(this)) tvValue else phoneValue
