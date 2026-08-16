package com.maximus.nativeexact

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.CheckBox
import androidx.activity.ComponentActivity

class ProfileEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
            setBackgroundColor(Color.rgb(7, 15, 29))
        }
        root.addView(TextView(this).apply {
            text = "Adicionar perfil"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, 80))
        val name = EditText(this).apply {
            hint = "Nome do perfil"
            textSize = 20f
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        root.addView(name, LinearLayout.LayoutParams(520, 72))
        val kids = CheckBox(this).apply {
            text = "Perfil Kids"
            setTextColor(Color.WHITE)
            textSize = 18f
        }
        root.addView(kids, LinearLayout.LayoutParams(320, 64))
        root.addView(Button(this).apply {
            text = "SALVAR"
            isFocusable = true
            setOnClickListener {
                val profileName = name.text.toString().trim().ifEmpty { "Perfil" }
                ProfileStore(this@ProfileEditActivity).save(NativeProfile(profileName, "avatar-1.jpg", kids.isChecked))
                setResult(RESULT_OK)
                finish()
            }
        }, LinearLayout.LayoutParams(320, 72))
        root.addView(Button(this).apply {
            text = "CANCELAR"
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(320, 72))
        setContentView(root)
    }
}
