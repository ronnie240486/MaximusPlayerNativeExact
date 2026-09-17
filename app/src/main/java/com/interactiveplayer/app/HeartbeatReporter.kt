package com.interactiveplayer.app

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Avisa o painel periodicamente qual canal está sendo assistido nesse
 * MAC — é isso que faz "Dispositivos Conectados" mostrar o nome do
 * conteúdo, não só "online". Mesmo padrão do app React Native
 * (heartbeat.php a cada 30s, best-effort).
 *
 * Antes o app nativo não mandava isso NUNCA, então o painel ficava
 * preso mostrando o último canal que outro app (ou nenhum) tinha
 * reportado pra esse MAC, mesmo trocando de canal aqui dentro.
 *
 * DEBUG_TOAST: desligado. Serviu pra descobrir que a rota v5 estava
 * certa e o problema real era MAC divergente entre o painel e o
 * aparelho — não mais necessário no dia a dia (ligar de novo (= true)
 * só se precisar depurar algo parecido no futuro).
 */
object HeartbeatReporter {
    private const val INTERVAL_MS = 30_000L
    private const val DEBUG_TOAST = false
    private var job: Job? = null

    /** Chame de novo (com o novo nome) toda vez que o canal/conteúdo mudar. */
    fun start(owner: LifecycleOwner, context: Context, content: String) {
        stop()
        if (content.isBlank()) return
        val appContext = context.applicationContext
        val mac = MacSessionStore.load(context)?.mac?.takeIf { it.isNotBlank() }
        if (mac == null) {
            if (DEBUG_TOAST) toast(appContext, "Heartbeat: sem MAC na sessão, não enviado")
            return
        }
        job = owner.lifecycleScope.launch {
            while (isActive) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { MacPanelClient.sendHeartbeat(mac, content) }.getOrElse { "ERRO: ${it.message ?: it}" }
                }
                if (DEBUG_TOAST) toast(appContext, "Heartbeat [$content]: $result")
                delay(INTERVAL_MS)
            }
        }
    }

    private fun toast(context: Context, message: String) {
        runCatching { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    }

    /** Chame ao sair da tela/parar de tocar, senão continua avisando um canal que não toca mais. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
