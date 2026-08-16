package com.maximus.nativeexact

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class ProfilesActivity : ComponentActivity() {
    private val cyan = Color.rgb(61, 225, 232)
    private val white = Color.rgb(245, 246, 250)
    private val dark = Color.rgb(18, 28, 48)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildProfiles())
    }

    private fun buildProfiles(): View {
        val root = Frame(this).apply {
            background = BitmapDrawable(resources, assetBitmap("default-bg.png"))
            setPadding(dp(28), dp(28), dp(28), dp(24))
        }
        val overlay = View(this).apply { setBackgroundColor(Color.argb(150, 0, 0, 0)) }
        root.addView(overlay, android.widget.FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = "Quem assiste?"
            textSize = 32f
            setTextColor(white)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        page.addView(title, LinearLayout.LayoutParams(-1, dp(64)))
        page.addView(TextView(this).apply {
            text = "━━━━━━━━"
            textSize = 20f
            setTextColor(cyan)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val profiles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(18), dp(10), dp(10))
        }
        addProfileCard(profiles, "Ronnie", "avatar-1.jpg", false)
        addProfileCard(profiles, "Adicionar", "", true)
        scroll.addView(profiles)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = TextView(this).apply {
            text = "PERFIS                         MAC\n\n"
            textSize = 18f
            setTextColor(white)
            gravity = Gravity.CENTER
            isFocusable = true
        }
        page.addView(footer, LinearLayout.LayoutParams(-1, dp(76)))
        root.addView(page, android.widget.FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun addProfileCard(parent: LinearLayout, name: String, asset: String, add: Boolean) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            setPadding(dp(10), dp(10), dp(10), dp(8))
            setOnFocusChangeListener { v, focused ->
                v.setBackgroundColor(if (focused) Color.argb(90, 61, 225, 232) else Color.TRANSPARENT)
            }
            setOnClickListener {
                if (add) {
                    startActivity(Intent(this@ProfilesActivity, ProfileEditActivity::class.java))
                } else {
                    startActivity(Intent(this@ProfilesActivity, MainActivity::class.java))
                }
            }
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            if (add) {
                setBackgroundColor(Color.rgb(36, 48, 77))
                setImageDrawable(null)
                contentDescription = "Adicionar perfil"
            } else {
                setImageBitmap(assetBitmap(asset))
            }
        }
        card.addView(image, LinearLayout.LayoutParams(dp(170), dp(170)))
        card.addView(TextView(this).apply {
            text = if (add) "+\n$name" else name
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(white)
        }, LinearLayout.LayoutParams(dp(190), dp(58)))
        parent.addView(card, LinearLayout.LayoutParams(dp(210), dp(260)))
    }

    private fun assetBitmap(name: String) = assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class Frame(context: android.content.Context) : android.widget.FrameLayout(context)
}
