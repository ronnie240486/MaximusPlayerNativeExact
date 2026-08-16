package com.maximus.nativeexact

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class WorldCamerasActivity : ComponentActivity() {
    private val countries = listOf(
        "Brasil" to "https://example.test/cameras/brasil",
        "Estados Unidos" to "https://example.test/cameras/usa",
        "Japão" to "https://example.test/cameras/japan",
        "França" to "https://example.test/cameras/france",
        "Reino Unido" to "https://example.test/cameras/uk",
        "Itália" to "https://example.test/cameras/italy"
    )
    private val white = Color.rgb(242, 244, 248)
    private val panel = Color.rgb(28, 40, 70)
    private val muted = Color.rgb(168, 177, 196)

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
            text = "‹  Câmeras do Mundo"
            textSize = 27f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        countries.forEach { (country, url) ->
            list.addView(TextView(this).apply {
                text = "◉   $country"
                textSize = 19f
                setTextColor(white)
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(panel)
                setPadding(dp(22), 0, dp(22), 0)
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else panel) }
                setOnClickListener { startActivity(Intent(this@WorldCamerasActivity, CameraViewActivity::class.java).putExtra("url", url).putExtra("title", country)) }
            }, LinearLayout.LayoutParams(-1, dp(76)).apply { setMargins(0, 0, 0, dp(10)) })
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
