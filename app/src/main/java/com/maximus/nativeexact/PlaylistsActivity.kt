package com.maximus.nativeexact

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class PlaylistsActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)
    private val panel = Color.rgb(28, 40, 70)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        root.addView(TextView(this).apply {
            text = "‹  Listas"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        val session = MacSessionStore.load(this)
        root.addView(TextView(this).apply {
            text = "O painel disponibilizou ${session?.playlists?.size ?: 0} lista(s) para este MAC. Escolha qual usar."
            textSize = 16f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }, LinearLayout.LayoutParams(-1, dp(54)))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val playlists = session?.playlists.orEmpty()
        if (playlists.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nenhuma lista encontrada para este MAC."
                textSize = 18f
                setTextColor(muted)
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(130)))
        } else {
            val active = MacSessionStore.activePlaylistIndex(this)
            playlists.forEachIndexed { index, playlist ->
                val row = TextView(this).apply {
                    text = "${if (index == active) "✓  " else ""}${playlist.name}"
                    textSize = 18f
                    setTextColor(if (index == active) cyan else white)
                    gravity = Gravity.CENTER_VERTICAL
                    setBackgroundColor(if (index == active) Color.rgb(36, 57, 92) else panel)
                    setPadding(dp(20), 0, dp(20), 0)
                    isFocusable = true
                    setOnFocusChangeListener { view, focused ->
                        if (focused) view.setBackgroundColor(Color.rgb(43, 73, 96))
                        else view.setBackgroundColor(if (index == active) Color.rgb(36, 57, 92) else panel)
                    }
                    setOnClickListener {
                        MacSessionStore.setActivePlaylistIndex(this@PlaylistsActivity, index)
                        CatalogRepository.clear()
                        finish()
                    }
                }
                list.addView(row, LinearLayout.LayoutParams(-1, dp(70)).apply { setMargins(0, 0, 0, dp(10)) })
            }
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
