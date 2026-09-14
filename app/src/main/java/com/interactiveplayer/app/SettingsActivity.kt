package com.interactiveplayer.app

import android.content.Intent
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.os.Bundle
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Tela de Ajustes, portada de `frontend/app/settings.tsx`.
 *
 * A versão anterior era uma lista de 8 rótulos sem ícone, sem legenda e
 * sem quase nenhuma ação de verdade. Portado como uma lista de linhas
 * (ícone + título + legenda + seta/interruptor), igual ao original.
 *
 * O PIN do controle parental usa um diálogo simples do Android em vez
 * do modal customizado do RN — a lógica (criar, confirmar, trocar,
 * desativar) segue a mesma.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        // Toggles e PIN podem ter mudado numa tela anterior.
        render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
        }
        root.addView(buildHeader())

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Theme.SPACING_MD), 0, dp(Theme.SPACING_MD), dp(40))
        }

        val session = MacSessionStore.load(this)
        val mac = DeviceIdentity.getMac(this)
        val playlistCount = session?.playlists?.size?.takeIf { it > 0 } ?: 1

        list.addView(row(
            icon = "👤",
            title = "Conta",
            subtitle = session?.status ?: "Sem sessão ativa",
            onClick = { startActivity(Intent(this, ProfilesActivity::class.java)) }
        ))
        list.addView(row(
            icon = "🆔",
            title = "ID do dispositivo",
            subtitle = mac,
            onClick = { startActivity(Intent(this, ProfilesActivity::class.java)) }
        ))
        list.addView(row(
            icon = "📃",
            title = "Listas",
            subtitle = "$playlistCount lista${if (playlistCount == 1) "" else "s"} disponível${if (playlistCount == 1) "" else "is"}",
            onClick = { startActivity(Intent(this, PlaylistsActivity::class.java)) }
        ))
        list.addView(row(
            icon = "🔄",
            title = "Atualizar conteúdo",
            subtitle = "Busca canais, filmes e séries de novo",
            onClick = { confirmUpdateContent() }
        ))
        list.addView(row(
            icon = "🗑",
            title = "Cache",
            subtitle = "Limpar cache do app",
            onClick = { confirmClearCache() }
        ))
        list.addView(row(
            icon = "🌐",
            title = "Idioma",
            subtitle = "Português (Brasil)",
            onClick = {
                Toast.makeText(this, "No momento o app está disponível apenas em Português (Brasil).", Toast.LENGTH_LONG).show()
            }
        ))

        val parentalOn = AppPreferences.isParentalLockOn(this)
        list.addView(row(
            icon = "🔒",
            title = "Controle parental",
            subtitle = if (parentalOn) "Ativado — conteúdo adulto bloqueado" else "Desativado",
            toggleValue = parentalOn,
            onToggle = { turningOn -> onToggleParental(turningOn) }
        ))
        if (parentalOn) {
            list.addView(row(
                icon = "🔑",
                title = "Alterar PIN",
                subtitle = "Trocar o PIN do controle parental",
                onClick = { startChangePin() }
            ))
        }

        val autoplay = AppPreferences.autoplayNext(this)
        list.addView(row(
            icon = "▶",
            title = "Player",
            subtitle = "Próximo episódio automático: ${if (autoplay) "ativado" else "desativado"}",
            toggleValue = autoplay,
            onToggle = { value ->
                AppPreferences.setAutoplayNext(this, value)
                render()
            }
        ))

        val welcomeAudio = AppPreferences.welcomeAudio(this)
        list.addView(row(
            icon = "🔊",
            title = "Áudio de boas-vindas",
            subtitle = if (welcomeAudio) "Ativado" else "Desativado",
            toggleValue = welcomeAudio,
            onToggle = { value ->
                AppPreferences.setWelcomeAudio(this, value)
                render()
            }
        ))
        list.addView(row(
            icon = "☰",
            title = "Ordem das categorias",
            subtitle = "Arrastar pra mudar a ordem da barra lateral",
            onClick = { startActivity(Intent(this, SidebarOrderActivity::class.java)) }
        ))
        list.addView(row(
            icon = "🩺",
            title = "Diagnóstico",
            subtitle = "Testar conexão com o backend",
            onClick = { startActivity(Intent(this, DiagnosticActivity::class.java)) }
        ))
        list.addView(row(
            icon = "ℹ",
            title = "Versão",
            subtitle = "v${BuildConfig.VERSION_NAME}",
            onClick = {
                Toast.makeText(this, "Maximus Player v${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show()
            }
        ))
        list.addView(row(
            icon = "🚪",
            title = "Sair / Trocar dispositivo",
            subtitle = "Apaga o login salvo neste app",
            danger = true,
            onClick = { confirmLogout() }
        ))

        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

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
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener { finish() }
        })
        header.addView(TextView(this).apply {
            setText("Configurações")
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Theme.SPACING_SM) })
        return header
    }

    /** styles.row: ícone + título/legenda + seta ou interruptor. */
    private fun row(
        icon: String,
        title: String,
        subtitle: String,
        danger: Boolean = false,
        toggleValue: Boolean? = null,
        onToggle: ((Boolean) -> Unit)? = null,
        onClick: (() -> Unit)? = null,
    ): View {
        val rowView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
        }

        rowView.addView(TextView(this).apply {
            setText(icon)
            textSize = 18f
            gravity = Gravity.CENTER
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_SM)
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(Theme.SPACING_MD) })

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            setText(title)
            textSize = 15f
            setTextColor(if (danger) Theme.danger else Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        texts.addView(TextView(this).apply {
            setText(subtitle)
            textSize = 12f
            setTextColor(Theme.textSecondary)
            maxLines = 1
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        rowView.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))

        if (toggleValue != null && onToggle != null) {
            // Switch (framework), não SwitchCompat: o app roda com
            // Theme.DeviceDefault, não Theme.AppCompat, e o widget do
            // appcompat crashava ao resolver atributos de tema que só
            // existem sob AppCompat.
            val switch = android.widget.Switch(this).apply {
                isChecked = toggleValue
                setOnCheckedChangeListener { _, checked -> onToggle(checked) }
            }
            rowView.addView(switch)
            rowView.setOnClickListener { switch.isChecked = !switch.isChecked }
        } else {
            rowView.addView(TextView(this).apply {
                setText("›")
                textSize = 16f
                setTextColor(Theme.textMuted)
            })
            rowView.setOnClickListener { onClick?.invoke() }
        }

        return rowView.apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(Theme.SPACING_SM)
            }
        }
    }

    // -----------------------------------------------------------------
    // Ações
    // -----------------------------------------------------------------

    private fun confirmUpdateContent() {
        AlertDialog.Builder(this)
            .setTitle("Atualizar conteúdo")
            .setMessage("Isso vai buscar canais, filmes e séries de novo. Pode levar alguns segundos.")
            .setPositiveButton("Atualizar") { _, _ ->
                lifecycleScope.launch {
                    CatalogRepository.clear(this@SettingsActivity)
                    CatalogRepository.load(this@SettingsActivity, force = true)
                    Toast.makeText(this@SettingsActivity, "Conteúdo atualizado.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmClearCache() {
        AlertDialog.Builder(this)
            .setTitle("Limpar cache")
            .setMessage("As listas vão recarregar na próxima vez que você abrir cada tela.")
            .setPositiveButton("Limpar") { _, _ ->
                CatalogRepository.clear(this)
                Toast.makeText(this, "Cache limpo.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Isso vai apagar o login salvo (você vai precisar ativar o MAC de novo no painel). Seu ID de dispositivo continua o mesmo.")
            .setPositiveButton("Sair") { _, _ ->
                MacSessionStore.clear(this)
                CatalogRepository.clear(this)
                startActivity(
                    Intent(this, MacLoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun onToggleParental(turningOn: Boolean) {
        if (turningOn) {
            promptNewPin(title = "Criar PIN", subtitle = "Escolha um PIN de 4 dígitos pra proteger o conteúdo adulto.") { pin ->
                AppPreferences.setParentalPin(this, pin)
                Toast.makeText(this, "Controle parental ativado.", Toast.LENGTH_SHORT).show()
                render()
            }
        } else {
            promptPin(title = "Desativar controle parental", subtitle = "Digite o PIN atual pra desativar.") { entered ->
                if (entered == AppPreferences.parentalPin(this)) {
                    AppPreferences.setParentalPin(this, null)
                    Toast.makeText(this, "Controle parental desativado.", Toast.LENGTH_SHORT).show()
                    render()
                } else {
                    Toast.makeText(this, "PIN incorreto.", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }

    private fun startChangePin() {
        promptPin(title = "Alterar PIN", subtitle = "Digite o PIN atual pra continuar.") { entered ->
            if (entered == AppPreferences.parentalPin(this)) {
                promptNewPin(title = "Novo PIN", subtitle = "Escolha o novo PIN de 4 dígitos.") { pin ->
                    AppPreferences.setParentalPin(this, pin)
                    Toast.makeText(this, "PIN alterado com sucesso.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "PIN incorreto.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Pede um PIN de 4 dígitos e confirma digitando de novo. */
    private fun promptNewPin(title: String, subtitle: String, onConfirmed: (String) -> Unit) {
        promptPin(title = title, subtitle = subtitle) { firstPin ->
            promptPin(title = "Confirme o PIN", subtitle = "Digite o mesmo PIN de novo.") { secondPin ->
                if (firstPin == secondPin) {
                    onConfirmed(firstPin)
                } else {
                    Toast.makeText(this, "Os PINs não coincidem. Tente de novo.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun promptPin(title: String, subtitle: String, onEntered: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            hint = "0000"
        }
        val padded = FrameLayout(this).apply {
            setPadding(dp(Theme.SPACING_LG), dp(Theme.SPACING_SM), dp(Theme.SPACING_LG), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(subtitle)
            .setView(padded)
            .setPositiveButton("OK") { _, _ ->
                val pin = input.text.toString()
                if (pin.length == 4) onEntered(pin)
                else Toast.makeText(this, "Digite os 4 dígitos.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
