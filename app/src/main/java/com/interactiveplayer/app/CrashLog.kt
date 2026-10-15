package com.interactiveplayer.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.os.Build

/**
 * Registro de diagnóstico: quando o app fecha sozinho (crash), grava o motivo
 * e mostra na tela na PRÓXIMA abertura -- assim dá pra tirar uma foto da tela
 * da TV box e mandar, sem precisar de cabo/logcat.
 *
 * Dois tipos de registro:
 *  - erro do app (exceção não tratada, inclusive falta de memória): guarda o
 *    texto do erro, as primeiras linhas do rastro e quanta memória estava em uso;
 *  - fechamento "mudo" (o Android matou o app, sem exceção -- típico de falta de
 *    memória do sistema): guarda só a última etapa do carregamento em que
 *    o app estava. Se o app terminar normalmente, a etapa vira "ok" e nada
 *    aparece.
 */
object CrashLog {
    private const val PREFS = "crash_log"
    private const val KEY_CRASH = "crash"
    private const val KEY_STAGE = "stage"

    @Volatile private var reportShown = false

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val rt = Runtime.getRuntime()
                val usedMb = (rt.totalMemory() - rt.freeMemory()) / 1048576
                val maxMb = rt.maxMemory() / 1048576
                val trace = error.javaClass.name + ": " + (error.message ?: "") + "\n" +
                    error.stackTrace.take(14).joinToString("\n") { "  at $it".take(120) }
                val cause = error.cause?.let { "\nCausa: ${it.javaClass.name}: ${it.message ?: ""}".take(200) } ?: ""
                val text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MODEL}\n" +
                    "Memória: ${usedMb}MB usados de ${maxMb}MB\n" +
                    "Etapa: ${stage(app)}\n" +
                    "Thread: ${thread.name}\n$trace$cause"
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CRASH, text).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Marca em que etapa o app está (gravado na hora, sobrevive se o Android matar o app). */
    fun mark(context: Context, stage: String) {
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STAGE, stage).commit()
        }
    }

    private fun stage(context: Context): String =
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STAGE, "ok") ?: "ok"
        }.getOrDefault("ok")

    /** Mostra o relatório pendente (se existir) UMA vez por abertura do app. */
    fun showPendingReport(activity: Activity) {
        if (reportShown) return
        reportShown = true
        runCatching {
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val crash = prefs.getString(KEY_CRASH, null)
            val lastStage = prefs.getString(KEY_STAGE, "ok") ?: "ok"
            val message = when {
                crash != null -> "O app fechou por um erro na vez anterior:\n\n$crash"
                lastStage != "ok" ->
                    "O app foi fechado pelo Android sem registrar erro (provável falta de memória).\n\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MODEL}\n" +
                        "Última etapa: $lastStage"
                else -> null
            } ?: return
            prefs.edit().remove(KEY_CRASH).putString(KEY_STAGE, "ok").commit()
            AlertDialog.Builder(activity)
                .setTitle("Relatório do último fechamento")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}
