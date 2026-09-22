package com.interactiveplayer.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Esconde a barra de status e a de navegação do Android em TODAS as
 * telas do app (igual foi feito no Fusion e no Maximus mobile) — em vez
 * de editar as ~20 Activities uma por uma, um Application customizado
 * com ActivityLifecycleCallbacks aplica isso automaticamente sempre que
 * QUALQUER Activity é criada ou volta a ficar em primeiro plano (voltar
 * de outro app, fechar um diálogo). "BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE"
 * é o "immersive sticky": se a pessoa arrastar da borda pra revelar as
 * barras, elas somem de novo sozinhas pouco depois.
 */
class MaximusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    hideSystemBars(activity)
                }

                override fun onActivityResumed(activity: Activity) {
                    hideSystemBars(activity)
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            },
        )
    }

    private fun hideSystemBars(activity: Activity) {
        val window = activity.window ?: return
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
