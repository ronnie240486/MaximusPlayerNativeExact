package com.maximus.nativeexact

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class FavoritesActivity : ComponentActivity() {
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(24, 18, 24, 18)
        }
        root.addView(TextView(this).apply {
            text = "‹  Favoritos"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, 64))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(TextView(this).apply {
            text = "Nenhum favorito salvo ainda.\nUse o botão ♡ nos conteúdos para adicionar."
            textSize = 19f
            setTextColor(muted)
            gravity = Gravity.CENTER
            setPadding(20, 80, 20, 80)
        }, LinearLayout.LayoutParams(-1, 220))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
}
