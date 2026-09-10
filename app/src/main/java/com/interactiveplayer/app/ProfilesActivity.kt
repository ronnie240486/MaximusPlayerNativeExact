package com.interactiveplayer.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * Tela "Quem assiste?", portada de `frontend/app/profiles.tsx` do
 * repositório Maximus, usando o ramo isTV de cada medida.
 *
 * A versão anterior tinha um perfil fixo escrito no código ("Ronnie"),
 * cores aproximadas e um sublinhado feito de caractere "━━━━━━━━" no
 * lugar da barra ciano de 48x3.
 */
class ProfilesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        // Voltar da edição de perfis precisa refletir o que mudou.
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Theme.black) }

        // ImageBackground com imageStyle opacity: 0.75 quando não há
        // bg_url do painel (que é o caso do nativo hoje).
        root.addView(ImageView(this).apply {
            setImageBitmap(assetBitmap("default-bg.png"))
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.75f
        }, FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(Theme.SPACING_XL), 0, 0)   // styles.safe paddingTop
        }

        // styles.title + titleTV (marginTop 88 por causa do overscan de TV)
        page.addView(TextView(this).apply {
            setText("Quem assiste?")
            textSize = 26f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(88) })

        // styles.underline: 48x3 ciano, cantos de 2
        page.addView(View(this).apply {
            background = roundRect(Theme.accentCyan, 2)
        }, LinearLayout.LayoutParams(dp(48), dp(3)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(Theme.SPACING_MD)
        })

        // styles.centerBlock + centerBlockTV (paddingTop 32)
        val centerBlock = FrameLayout(this).apply {
            setPadding(0, dp(32), 0, 0)
        }
        centerBlock.addView(buildProfileRow(), FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        page.addView(centerBlock, LinearLayout.LayoutParams(-1, 0, 1f))

        val profiles = ProfileStore(this).all()
        if (profiles.isNotEmpty()) {
            // styles.manageBtn
            page.addView(TextView(this).apply {
                setText("PERFIS")
                textSize = 13f
                setTextColor(Theme.textSecondary)
                setTypeface(Typeface.DEFAULT_BOLD)
                letterSpacing = letterSpacingEm(2f, 13f)
                gravity = Gravity.CENTER
                background = roundStroke(Theme.textSecondary, 24)
                setPadding(dp(32), dp(12), dp(32), dp(12))
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    startActivity(
                        Intent(this@ProfilesActivity, ProfileEditActivity::class.java)
                            .putExtra("manage", "1")
                    )
                }
            }, LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            })
        }

        // styles.macTag
        page.addView(TextView(this).apply {
            setText(DeviceIdentity.getMac(this@ProfilesActivity))
            textSize = 10f
            setTextColor(Theme.textMuted)
            letterSpacing = letterSpacingEm(1.5f, 10f)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_MD)
        })

        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        return root
    }

    private fun buildProfileRow(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(Theme.SPACING_LG), 0, dp(Theme.SPACING_LG), 0)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val profiles = ProfileStore(this).all()
        profiles.forEach { profile -> row.addView(buildProfileItem(profile)) }

        // O original só oferece o slot de adicionar até 6 perfis.
        if (profiles.size < 6) row.addView(buildAddItem())

        scroll.addView(row)
        return scroll
    }

    /** styles.profileItem + avatarCard + profileName. */
    private fun buildProfileItem(profile: NativeProfile): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            background = focusOutline()
            setOnClickListener {
                startActivity(Intent(this@ProfilesActivity, MainActivity::class.java))
            }
        }

        val card = FrameLayout(this).apply {
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_LG)
            clipToOutline = true
        }
        card.addView(ImageView(this).apply {
            setImageBitmap(assetBitmap(profile.avatar))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundRect(Theme.darkSurfaceAlt, 14)
            clipToOutline = true
        }, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))

        // styles.kidsBadge
        if (profile.kids) {
            card.addView(TextView(this).apply {
                setText("KIDS")
                textSize = 9f
                setTextColor(Theme.black)
                setTypeface(Typeface.DEFAULT_BOLD)
                letterSpacing = letterSpacingEm(0.5f, 9f)
                gravity = Gravity.CENTER
                background = roundRect(Theme.accentCyan, Theme.RADIUS_SM)
                setPadding(dp(8), dp(2), dp(8), dp(2))
            }, FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(6) })
        }

        item.addView(card, LinearLayout.LayoutParams(dp(100), dp(100)))
        item.addView(profileLabel(profile.name), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_SM)
        })

        return item.apply {
            layoutParams = LinearLayout.LayoutParams(dp(108), -2).apply {
                rightMargin = dp(Theme.SPACING_LG)   // contentContainer gap
            }
        }
    }

    /** styles.addCard: mesmo card, com borda tracejada. */
    private fun buildAddItem(): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            background = focusOutline()
            setOnClickListener {
                startActivity(Intent(this@ProfilesActivity, ProfileEditActivity::class.java))
            }
        }

        val card = TextView(this).apply {
            setText("+")
            textSize = 38f
            setTextColor(Theme.textSecondary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Theme.darkSurfaceAlt)
                cornerRadius = dpF(Theme.RADIUS_LG.toFloat())
                // borderWidth: 1.5, borderStyle: 'dashed'
                setStroke(dp(2), Theme.textMuted, dpF(6f), dpF(4f))
            }
        }

        item.addView(card, LinearLayout.LayoutParams(dp(100), dp(100)))
        item.addView(profileLabel("Adicionar"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_SM)
        })

        return item.apply {
            layoutParams = LinearLayout.LayoutParams(dp(108), -2)
        }
    }

    private fun profileLabel(name: String): TextView = TextView(this).apply {
        setText(name)
        textSize = 13f
        setTextColor(Theme.white)
        gravity = Gravity.CENTER
        maxLines = 1
    }

    /** styles.profileFocusTV: contorno ciano de 2dp, cantos de 20. */
    private fun focusOutline(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        cornerRadius = dpF(20f)
        setStroke(dp(2), Color.TRANSPARENT)
    }

    private fun assetBitmap(name: String) = runCatching {
        assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
