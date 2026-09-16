package com.interactiveplayer.app

import android.content.Context
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
 */
object HeartbeatReporter {
    private const val INTERVAL_MS = 30_000L
    private var job: Job? = null

    /** Chame de novo (com o novo nome) toda vez que o canal/conteúdo mudar. */
    fun start(owner: LifecycleOwner, context: Context, content: String) {
        stop()
        if (content.isBlank()) return
        val mac = MacSessionStore.load(context)?.mac?.takeIf { it.isNotBlank() } ?: return
        job = owner.lifecycleScope.launch {
            while (isActive) {
                withContext(Dispatchers.IO) {
                    runCatching { MacPanelClient.sendHeartbeat(mac, content) }
                }
                delay(INTERVAL_MS)
            }
        }
    }

    /** Chame ao sair da tela/parar de tocar, senão continua avisando um canal que não toca mais. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
