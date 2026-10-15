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
        aplicarContornoSslTvAntiga()
        CrashLog.install(this)
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    hideSystemBars(activity)
                }

                override fun onActivityResumed(activity: Activity) {
                    hideSystemBars(activity)
                    // Se o app fechou sozinho na vez anterior, mostra o motivo.
                    CrashLog.showPendingReport(activity)
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    // Saiu da Home de propósito (botão voltar): não é crash.
                    if (activity is MainActivity && activity.isFinishing) CrashLog.mark(activity, "ok")
                }
            },
        )
    }

    /**
     * Bug real: TV boxes com Android antigo (abaixo da versão 8 / API 26,
     * típico das "Mxq" genéricas) davam "falha de conexão" -- o firmware
     * delas não tem os certificados raiz atuais e não fecha o handshake
     * HTTPS com o painel (Railway nem Manus, o fallback também é HTTPS).
     * No Ouro Pro, que roda normal na MESMA box, isso já era contornado
     * (SSLUtils.ignoreSSL, só em SDK < 26). Todas as chamadas de rede deste
     * app (MacPanelClient, EPG, TMDB, imagens e o próprio ExoPlayer/media3)
     * usam HttpURLConnection, então ajustar o SSLSocketFactory padrão aqui,
     * UMA vez, na inicialização, cobre tudo. Em Android >= 26 não faz nada,
     * então as boxes e celulares novos continuam com a validação normal.
     */
    private fun aplicarContornoSslTvAntiga() {
        if (android.os.Build.VERSION.SDK_INT >= 26) return
        try {
            val trustAll = arrayOf<javax.net.ssl.TrustManager>(
                object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                },
            )
            val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
            ctx.init(null, trustAll, java.security.SecureRandom())
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(ctx.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {
            // Se falhar, segue sem o contorno -- o resto do app não pode cair por isso.
        }
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
