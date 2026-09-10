package com.interactiveplayer.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
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
