package com.interactiveplayer.app

import android.content.Context
import org.json.JSONObject

/**
 * Contagem de quantas vezes cada canal foi realmente aberto pra
 * assistir — usada pela faixa "CANAIS MAIS ASSISTIDOS" da Home.
 *
 * Antes essa faixa não tinha nenhuma relação com o que a pessoa de fato
 * assiste: era só `channels.take(20)`, ou seja, os primeiros 20 canais
 * na ordem em que vêm do catálogo/M3U. Como vários painéis colocam
 * canais infantis "24h ..." logo no início da lista (ordem alfabética
 * dentro do grupo), a faixa "mais assistidos" sempre mostrava esses
 * mesmos canais de desenho, nunca os que a pessoa realmente mais liga.
 *
 * Agora, toda vez que um canal é aberto (mesmo padrão do
 * WatchHistoryStore.record, chamado do mesmo lugar), o contador dele
 * sobe aqui — persistido em SharedPreferences, sem depender de rede
 * nem do painel — e a Home ordena por esse número de verdade.
 */
object ChannelWatchStats {
    private const val PREFS = "maximus_native_channel_watch_stats"
    private const val KEY_COUNTS = "counts"

    private fun keyFor(item: M3uItem): String =
        item.streamId?.takeIf { it.isNotBlank() } ?: item.url

    private fun readCounts(context: Context): JSONObject {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_COUNTS, "{}") ?: "{}"
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    /** Chamar sempre que um canal for aberto pra assistir. No-op para filme/série. */
    fun record(context: Context, item: M3uItem) {
        if (item.kind != M3uItem.Kind.CHANNEL) return
        val key = keyFor(item)
        if (key.isBlank()) return
        val counts = readCounts(context)
        counts.put(key, counts.optInt(key, 0) + 1)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COUNTS, counts.toString())
            .apply()
    }

    private fun countFor(counts: JSONObject, item: M3uItem): Int = counts.optInt(keyFor(item), 0)

    /**
     * Ordena os canais recebidos pelos mais assistidos primeiro, e
     * descarta quem nunca foi aberto de verdade — evita que a faixa
     * volte a virar "primeiros N canais do catálogo" quando pouco (ou
     * nada) ainda foi assistido nesse aparelho.
     */
    fun mostWatched(context: Context, channels: List<M3uItem>, limit: Int = 20): List<M3uItem> {
        if (channels.isEmpty()) return emptyList()
        val counts = readCounts(context)
        return channels
            .map { it to countFor(counts, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
