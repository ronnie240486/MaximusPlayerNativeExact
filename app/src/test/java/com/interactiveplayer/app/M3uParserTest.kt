package com.interactiveplayer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesGroupsAndDirectUrls() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="News" group-title="Canais",News
            https://example.test/live/news.m3u8
            #EXTINF:-1 tvg-name="Alma" group-title="Filmes | Ação",Alma
            https://example.test/movie/alma.mp4
            #EXTINF:-1 tvg-name="Drama" group-title="Séries | Netflix",Drama
            https://example.test/series/drama.m3u8
            #EXTINF:-1 tvg-name="Kids" group-title="Kids",Kids
            https://example.test/kids/cartoon.mp4
        """.trimIndent()

        val items = M3uParser.parse(playlist)
        assertEquals(4, items.size)
        assertEquals(M3uItem.Kind.CHANNEL, items[0].kind)
        assertEquals(M3uItem.Kind.MOVIE, items[1].kind)
        assertEquals(M3uItem.Kind.SERIES, items[2].kind)
        assertEquals(M3uItem.Kind.KIDS, items[3].kind)
        assertTrue(items[2].url.endsWith("drama.m3u8"))
    }
}
