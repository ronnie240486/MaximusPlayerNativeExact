package com.maximus.nativeexact

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

class WelcomeActivity : ComponentActivity() {
    private var audio: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = BitmapDrawable(resources, assets.open("original_media/default-bg.png").use { BitmapFactory.decodeStream(it) })
            setPadding(32, 32, 32, 32)
        }
        root.addView(View(this).apply { setBackgroundColor(Color.argb(130, 0, 0, 0)) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val logo = ImageView(this).apply {
            setImageBitmap(assets.open("original_media/app-image.png").use { BitmapFactory.decodeStream(it) })
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Maximus Player"
        }
        root.addView(logo, LinearLayout.LayoutParams(260, 180))
        root.addView(TextView(this).apply {
            text = "Toque para pular"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            isFocusable = true
            setOnClickListener { openProfiles() }
        }, LinearLayout.LayoutParams(-1, 70))
        root.setOnClickListener { openProfiles() }
        setContentView(root)
        runCatching {
            val descriptor = assets.openFd("original_media/welcome.wav")
            audio = MediaPlayer().apply {
                setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                prepare()
            }
            descriptor.close()
            audio?.start()
        }
    }

    private fun openProfiles() {
        startActivity(Intent(this, ProfilesActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        audio?.release()
        audio = null
        super.onDestroy()
    }
}
