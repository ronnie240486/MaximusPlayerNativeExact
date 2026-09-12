package com.interactiveplayer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Carregador de imagens com cache em memória e redução de resolução.
 *
 * Antes, cada tela (Home, Catálogo, detalhes) baixava e decodificava a
 * imagem em resolução total toda vez que a View aparecia — inclusive ao
 * simplesmente voltar pra Home depois de abrir outra tela. Numa TV box
 * (CPU fraca, pouca RAM), isso causava engasgos visíveis: dezenas de
 * decodificações de bitmap grande disparadas ao mesmo tempo.
 *
 * Esta classe resolve as duas partes do problema:
 * 1. Cache em memória por URL — a segunda exibição é instantânea.
 * 2. `inSampleSize` calculado a partir do tamanho real do destino — uma
 *    imagem de 1200x1600 exibida num card de 130x188dp não precisa ser
 *    decodificada em resolução total.
 *
 * Um semáforo limita a 4 decodificações simultâneas, pra não afogar a
 * CPU/rede da TV box quando uma tela inteira de pôsteres carrega junto.
 */
object ImageLoader {
    private val cache: LruCache<String, Bitmap> = run {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Um oitavo da memória disponível para o processo, em KB.
        LruCache(maxMemoryKb / 8)
    }

    private val concurrencyLimit = Semaphore(4)

    /** [reqWidthPx]/[reqHeightPx]: tamanho de exibição alvo, em pixels reais (já convertido de dp). */
    suspend fun load(url: String, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
        if (url.isBlank()) return null
        cache.get(url)?.let { return it }

        return concurrencyLimit.withPermit {
            // Checa de novo: outra chamada pode ter preenchido o cache
            // enquanto esta esperava a vez no semáforo.
            cache.get(url)?.let { return@withPermit it }

            val bitmap = withContext(Dispatchers.IO) {
                runCatching { downloadAndDecode(url, reqWidthPx, reqHeightPx) }.getOrNull()
            }
            if (bitmap != null) cache.put(url, bitmap)
            bitmap
        }
    }

    private fun downloadAndDecode(url: String, reqWidthPx: Int, reqHeightPx: Int): Bitmap? {
        val bytes = readBytes(url) ?: return null

        // Primeiro só lê as dimensões (inJustDecodeBounds), sem alocar
        // pixel nenhum, pra calcular o fator de redução correto.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidthPx, reqHeightPx)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0 || rawWidth <= 0 || rawHeight <= 0) return 1
        var sample = 1
        var halfWidth = rawWidth / 2
        var halfHeight = rawHeight / 2
        while (halfWidth / sample >= reqWidth && halfHeight / sample >= reqHeight) {
            sample *= 2
        }
        return sample
    }

    private fun readBytes(url: String): ByteArray? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000
        connection.readTimeout = 8000
        connection.inputStream.use { it.readBytes() }
    }.getOrNull()
}

/**
 * "Agora" embaixo do nome do canal, na lista. Segue o mesmo cuidado do
 * loadRecyclableImage: guarda o streamId pedido na tag e só aplica o
 * resultado se a view ainda for daquele mesmo canal quando a resposta
 * chegar — senão o "agora" de um canal errado apareceria embaixo de
 * outro, depois de rolar a lista rápido.
 */
fun loadNowPlaying(view: TextView, context: android.content.Context, item: M3uItem, streamId: String) {
    val activity = context as? ComponentActivity ?: return
    activity.lifecycleScope.launch {
        val title = withContext(Dispatchers.IO) {
            EpgClient.fetchNowTitle(context, item)
        }
        if (view.tag == streamId && !title.isNullOrBlank()) {
            view.setText("Agora: $title")
            view.visibility = View.VISIBLE
        }
    }
}

/**
 * Carrega uma imagem numa ImageView dentro de uma célula reciclável
 * (RecyclerView). Guarda a URL pedida na `tag`; quando o bitmap chega,
 * só aplica se a `tag` ainda for a mesma — senão a view já foi
 * reaproveitada por outro item da lista e a imagem errada apareceria.
 */
fun loadRecyclableImage(view: android.widget.ImageView, url: String) {
    val activity = view.context as? androidx.activity.ComponentActivity ?: return
    val requestedFor = url
    activity.lifecycleScope.launch {
        val width = view.layoutParams?.width?.takeIf { it > 0 } ?: view.context.dp(160)
        val height = view.layoutParams?.height?.takeIf { it > 0 } ?: view.context.dp(230)
        val bitmap = ImageLoader.load(requestedFor, width, height)
        if (bitmap != null && view.tag == requestedFor) view.setImageBitmap(bitmap)
    }
}
