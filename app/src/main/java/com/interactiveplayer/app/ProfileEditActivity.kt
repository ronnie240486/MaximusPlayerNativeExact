package com.interactiveplayer.app

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Tela de criar/editar/gerenciar perfil, portada de
 * `frontend/app/profile-edit.tsx`.
 *
 * A versão anterior era uma das quatro Activities sem o helper dp() —
 * usava pixel cru (`setPadding(48, 32, 48, 32)`), o que em densidade 2.0
 * cortava o título e espremia os campos. E não tinha grade de avatar
 * nenhuma, mesmo com os 35 avatares já presentes nos assets.
 */
class ProfileEditActivity : ComponentActivity() {

    private val avatarIds: List<String> = (1..35).map { "avatar-$it.jpg" }
    private val kidAvatarIds: List<String> = (1..7).map { "kid-avatar-$it.jpg" }

    private lateinit var store: ProfileStore
    private var isManage = false
    private var selectedName: String? = null
    private var avatarId: String = "avatar-1.jpg"
    private var isKids = false
    private var nameInput: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ProfileStore(this)
        isManage = intent.getStringExtra("manage") == "1"
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
        }
        root.addView(buildHeader())

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // styles.scroll: padding lg, paddingBottom 40
            setPadding(
                dp(Theme.SPACING_LG),
                dp(Theme.SPACING_LG),
                dp(Theme.SPACING_LG),
                dp(40)
            )
        }

        if (isManage) {
            body.addView(smallLabel("SEUS PERFIS"))
            body.addView(buildProfilesRow())
        }

        body.addView(buildPreview())
        body.addView(buildKidsToggle())
        body.addView(buildAvatarGrid())

        body.addView(smallLabel("NOME").apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(Theme.SPACING_MD)
        })
        body.addView(buildNameInput())
        body.addView(buildButtonRow())

        if (selectedName != null) body.addView(buildDeleteButton())

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    /** styles.header: voltar à esquerda, título ao centro. */
    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
        }

        header.addView(TextView(this).apply {
            setText("‹")
            textSize = 24f
            setTextColor(Theme.white)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(32), -2))

        header.addView(TextView(this).apply {
            setText(
                when {
                    isManage -> "Gerenciar perfis"
                    selectedName != null -> "Editar perfil"
                    else -> "Novo perfil"
                }
            )
            textSize = 18f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, -2, 1f))

        // O original reserva a mesma largura à direita pra centralizar.
        header.addView(View(this), LinearLayout.LayoutParams(dp(32), dp(1)))
        return header
    }

    /** styles.profilesRow + profileMini + addPlus. */
    private fun buildProfilesRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        store.all().forEach { profile ->
            val mini = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    selectedName = profile.name
                    avatarId = profile.avatar
                    isKids = profile.kids
                    render()
                }
            }

            // styles.miniWrap: padding 3, radius 14, borda 2 (ciano se ativo)
            val wrap = FrameLayout(this).apply {
                setPadding(dp(3), dp(3), dp(3), dp(3))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpF(14f)
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        dp(2),
                        if (selectedName == profile.name) Theme.accentCyan else Color.TRANSPARENT
                    )
                }
            }
            wrap.addView(avatarImage(profile.avatar, 54, 11))
            mini.addView(wrap)

            mini.addView(TextView(this).apply {
                setText(profile.name)
                textSize = 11f
                setTextColor(Theme.white)
                gravity = Gravity.CENTER
                maxLines = 1
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

            row.addView(mini, LinearLayout.LayoutParams(dp(72), -2).apply {
                rightMargin = dp(Theme.SPACING_MD)   // gap: spacing.md
            })
        }

        // Slot "Novo"
        val novo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            setOnClickListener {
                selectedName = null
                avatarId = "avatar-1.jpg"
                isKids = false
                render()
            }
        }
        novo.addView(TextView(this).apply {
            setText("+")
            textSize = 28f
            setTextColor(Theme.textSecondary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(12f)
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), Theme.textMuted, dpF(6f), dpF(4f))  // dashed
            }
        }, LinearLayout.LayoutParams(dp(60), dp(60)))
        novo.addView(TextView(this).apply {
            setText("Novo")
            textSize = 11f
            setTextColor(Theme.white)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        row.addView(novo, LinearLayout.LayoutParams(dp(72), -2))

        val scroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        return scroll.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(Theme.SPACING_LG)
            }
        }
    }

    /** styles.previewWrap + preview: card de 110dp com avatar de 90dp. */
    private fun buildPreview(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val preview = FrameLayout(this).apply {
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_LG)
            clipToOutline = true
        }
        preview.addView(
            avatarImage(avatarId, 90, 14),
            FrameLayout.LayoutParams(dp(90), dp(90), Gravity.CENTER)
        )
        wrap.addView(preview, LinearLayout.LayoutParams(dp(110), dp(110)).apply {
            bottomMargin = dp(6)
        })

        wrap.addView(TextView(this).apply {
            setText("AVATAR")
            textSize = 11f
            setTextColor(Theme.textMuted)
            letterSpacing = letterSpacingEm(1.5f, 11f)
            gravity = Gravity.CENTER
        })

        return wrap.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(Theme.SPACING_MD)
            }
        }
    }

    /** styles.kidsToggleRow + switchTrack/switchThumb. */
    private fun buildKidsToggle(): View {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_MD)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
            isFocusable = true
            isClickable = true
        }

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            setText("Perfil infantil")
            textSize = 14f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        texts.addView(TextView(this).apply {
            setText("Sem canais e filmes adultos — nem com PIN, o conteúdo simplesmente não aparece")
            textSize = 12f
            setTextColor(Theme.textMuted)
            setLineSpacing(dpF(4f), 1f)      // lineHeight 16 sobre fonte 12
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        rowView.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        // Interruptor: trilho 46x26 com botão de 20dp em cada ponta.
        val track = FrameLayout(this).apply {
            background = roundRect(
                if (isKids) Theme.accentCyan else Theme.darkSurface,
                13
            )
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        track.addView(View(this).apply {
            background = circleDrawable(if (isKids) Theme.black else Theme.textMuted)
        }, FrameLayout.LayoutParams(
            dp(20),
            dp(20),
            (if (isKids) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        ))
        rowView.addView(track, LinearLayout.LayoutParams(dp(46), dp(26)).apply {
            leftMargin = dp(Theme.SPACING_MD)
        })

        rowView.setOnClickListener {
            isKids = !isKids
            // Trocar de tipo muda a lista de avatares, então o escolhido
            // precisa voltar pro primeiro da lista nova.
            avatarId = if (isKids) kidAvatarIds.first() else avatarIds.first()
            render()
        }

        return rowView.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(Theme.SPACING_MD)
            }
        }
    }

    /**
     * styles.avatarGrid: no RN é um flexWrap. Aqui as linhas são montadas
     * à mão, com o número de colunas calculado a partir da largura real
     * da tela — assim funciona tanto no celular quanto na TV box.
     */
    private fun buildAvatarGrid(): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val choices = if (isKids) kidAvatarIds else avatarIds
        val cellSize = dp(54 + 8 + 4)      // avatar + padding 4 + borda 2
        val available = resources.displayMetrics.widthPixels - dp(Theme.SPACING_LG * 2)
        val columns = maxOf(4, available / (cellSize + dp(Theme.SPACING_SM)))

        var line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        choices.forEachIndexed { index, id ->
            if (index % columns == 0) {
                line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(line, LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(Theme.SPACING_SM)
                })
            }

            // styles.avatarChoice: padding 4, radius 14, borda 2
            val choice = FrameLayout(this).apply {
                setPadding(dp(4), dp(4), dp(4), dp(4))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dpF(14f)
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        dp(2),
                        if (avatarId == id) Theme.accentCyan else Color.TRANSPARENT
                    )
                }
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    avatarId = id
                    render()
                }
            }
            choice.addView(avatarImage(id, 54, 12))
            line.addView(choice, LinearLayout.LayoutParams(-2, -2).apply {
                rightMargin = dp(Theme.SPACING_SM)
            })
        }

        return grid
    }

    /** styles.input */
    private fun buildNameInput(): View {
        val input = EditText(this).apply {
            setText(selectedName ?: "")
            hint = "Digite um nome"
            setHintTextColor(Theme.textMuted)
            setTextColor(Theme.white)
            textSize = 16f
            background = roundRect(Theme.darkSurfaceAlt, 10)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            filters = arrayOf(InputFilter.LengthFilter(30))   // maxLength: 30
            isSingleLine = true
        }
        nameInput = input
        return input.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
        }
    }

    /** styles.btnRow: SALVAR e CANCELAR lado a lado, meio a meio. */
    private fun buildButtonRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        row.addView(TextView(this).apply {
            setText("SALVAR")
            textSize = 14f
            setTextColor(Theme.black)
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = letterSpacingEm(1.5f, 14f)
            gravity = Gravity.CENTER
            background = roundRect(Theme.accentCyan, 10)
            setPadding(0, dp(14), 0, dp(14))
            isFocusable = true
            isClickable = true
            setOnClickListener { saveProfile() }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply {
            rightMargin = dp(Theme.SPACING_SM)
        })

        row.addView(TextView(this).apply {
            setText("CANCELAR")
            textSize = 14f
            setTextColor(Theme.textSecondary)
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = letterSpacingEm(1.5f, 14f)
            gravity = Gravity.CENTER
            background = roundStroke(Theme.textSecondary, 10)
            setPadding(0, dp(14), 0, dp(14))
            isFocusable = true
            isClickable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, -2, 1f))

        return row.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(Theme.SPACING_XL)
            }
        }
    }

    /** styles.deleteBtn: só aparece com um perfil selecionado. */
    private fun buildDeleteButton(): View = TextView(this).apply {
        setText("EXCLUIR PERFIL")
        textSize = 14f
        setTextColor(Theme.danger)
        setTypeface(Typeface.DEFAULT_BOLD)
        letterSpacing = letterSpacingEm(1.5f, 14f)
        gravity = Gravity.CENTER
        background = roundStroke(Theme.danger, 10)
        setPadding(0, dp(14), 0, dp(14))
        isFocusable = true
        isClickable = true
        setOnClickListener {
            selectedName?.let { store.remove(it) }
            finish()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_MD)
        }
    }

    private fun saveProfile() {
        val typed = nameInput?.text?.toString()?.trim().orEmpty()
        if (typed.isBlank()) {
            Toast.makeText(this, "Digite um nome", Toast.LENGTH_SHORT).show()
            return
        }
        // Renomear tem que apagar o registro antigo, senão sobra duplicado.
        selectedName?.takeIf { !it.equals(typed, ignoreCase = true) }?.let { store.remove(it) }
        store.save(NativeProfile(typed, avatarId, isKids))
        finish()
    }

    private fun smallLabel(text: String): TextView = TextView(this).apply {
        setText(text)
        textSize = 11f
        setTextColor(Theme.textMuted)
        letterSpacing = letterSpacingEm(1.5f, 11f)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        }
    }

    private fun avatarImage(id: String, sizeDp: Int, radiusDp: Int): ImageView =
        ImageView(this).apply {
            setImageBitmap(assetBitmap(id))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = roundRect(Theme.darkSurfaceAlt, radiusDp)
            clipToOutline = true
            layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }

    private fun assetBitmap(name: String) = runCatching {
        assets.open("original_media/$name").use { BitmapFactory.decodeStream(it) }
    }.getOrNull()
}
