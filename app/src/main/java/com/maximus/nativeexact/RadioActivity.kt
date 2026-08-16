package com.maximus.nativeexact

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class RadioActivity : ComponentActivity() {
    private val stations = listOf(
        "Rádio Alpha" to "https://stream.example/radio-alpha",
        "Rádio Hits" to "https://stream.example/radio-hits",
        "Rádio News" to "https://stream.example/radio-news",
        "Rádio Brasil" to "https://stream.example/radio-brasil"
    )
    private val white = Color.rgb(242, 244, 248)
    private val muted = Color.rgb(168, 177, 196)
    private val cyan = Color.rgb(53, 222, 231)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildView())
    }

    private fun buildView(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 16, 30))
            setPadding(dp(24), dp(20), dp(24), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "‹  Rádios"
            textSize = 28f
            setTextColor(white)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        val search = EditText(this).apply {
            hint = "Buscar rádio..."
            setSingleLine(true)
            textSize = 18f
            setTextColor(white)
            setHintTextColor(muted)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(58)))
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(16), 0, 0) }
        stations.forEach { (name, url) ->
            list.addView(TextView(this).apply {
                text = "♫  $name"
                textSize = 19f
                setTextColor(white)
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.rgb(28, 40, 70))
                setPadding(dp(18), 0, dp(18), 0)
                isFocusable = true
                setOnFocusChangeListener { view, focused -> view.setBackgroundColor(if (focused) Color.rgb(43, 73, 96) else Color.rgb(28, 40, 70)) }
                setOnClickListener { startActivity(Intent(this@RadioActivity, PlayerActivity::class.java).putExtra("url", url)) }
            }, LinearLayout.LayoutParams(-1, dp(70)).apply { setMargins(0, 0, 0, dp(10)) })
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
