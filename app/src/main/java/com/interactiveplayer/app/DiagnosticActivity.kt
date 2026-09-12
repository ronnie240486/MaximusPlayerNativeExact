package com.interactiveplayer.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Diagnóstico simplificado, portado de `frontend/app/diagnostic.tsx`.
 *
 * De propósito NÃO mostra nada técnico (URLs, usuário/senha, JSON cru)
 * — só duas linhas de status pra qualquer cliente entender rapidamente
 * onde está o problema, sem expor dado sensível.
 *
 * Era uma das quatro Activities sem o helper dp() (pixel cru direto:
 * `setPadding(28, 22, 28, 22)`), e não checava nada de verdade — os
 * "status" eram texto fixo tipo "Media3 disponível".
 */
class DiagnosticActivity : ComponentActivity() {

    private enum class CheckState { CHECKING, OK, OFF }

    private var internetRow: StatusRowViews? = null
    private var listRow: StatusRowViews? = null
    private var summaryIcon: TextView? = null
    private var summaryText: TextView? = null
    private var refreshIcon: TextView? = null
    private var checking = false

    private class StatusRowViews(val progress: ProgressBar, val badge: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        runChecks()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
        }

        // styles.header
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
        }, LinearLayout.LayoutParams(-2, -2))
        header.addView(TextView(this).apply {
            setText("Diagnóstico")
            textSize = 18f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Theme.SPACING_SM) })
        refreshIcon = TextView(this).apply {
            setText("⟳")
            textSize = 20f
            setTextColor(Theme.accentCyan)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener { if (!checking) runChecks() }
        }
        header.addView(refreshIcon)
        root.addView(header)

        // styles.body
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
        }

        internetRow = buildStatusRow(body, "Internet")
        listRow = buildStatusRow(body, "Lista")

        // styles.summaryBox
        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurfaceAlt, Theme.RADIUS_MD)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
        }
        summaryIcon = TextView(this).apply { textSize = 18f }
        summary.addView(summaryIcon, LinearLayout.LayoutParams(-2, -2).apply {
            rightMargin = dp(Theme.SPACING_SM)
        })
        summaryText = TextView(this).apply {
            textSize = 14f
            setTextColor(Theme.white)
        }
        summary.addView(summaryText, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(summary, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Theme.SPACING_SM)
        })

        // Os dois botões de depuração do original abrem notificações de
        // teste e um log de sessão que o nativo não guarda hoje. Mantém
        // o log de eventos recentes, que é a parte útil pra suporte.
        body.addView(debugButton("Ver eventos recentes") { showRecentEvents() })

        root.addView(body)
        return root
    }

    private fun buildStatusRow(parent: LinearLayout, label: String): StatusRowViews {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundRect(Theme.darkSurface, Theme.RADIUS_MD)
            setPadding(
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD),
                dp(Theme.SPACING_MD)
            )
        }
        row.addView(TextView(this).apply {
            setText(label)
            textSize = 15f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))

        val progress = ProgressBar(this).apply { isIndeterminate = true }
        val badge = TextView(this).apply {
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            visibility = View.GONE
        }
        row.addView(progress, LinearLayout.LayoutParams(dp(20), dp(20)))
        row.addView(badge)

        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(Theme.SPACING_SM)
        })
        return StatusRowViews(progress, badge)
    }

    private fun setRowState(rowViews: StatusRowViews, state: CheckState) {
        when (state) {
            CheckState.CHECKING -> {
                rowViews.progress.visibility = View.VISIBLE
                rowViews.badge.visibility = View.GONE
            }
            CheckState.OK, CheckState.OFF -> {
                rowViews.progress.visibility = View.GONE
                rowViews.badge.visibility = View.VISIBLE
                rowViews.badge.setText(if (state == CheckState.OK) "OK" else "OFF")
                rowViews.badge.setTextColor(if (state == CheckState.OK) Theme.accentCyan else Theme.danger)
            }
        }
    }

    private fun runChecks() {
        checking = true
        refreshIcon?.setTextColor(Theme.textMuted)
        setRowState(internetRow!!, CheckState.CHECKING)
        setRowState(listRow!!, CheckState.CHECKING)
        updateSummary(checking = true, internetOk = false, listOk = false)

        lifecycleScope.launch {
            // 1) Internet: um endereço simples e sempre no ar, sem
            // relação com o painel — separa "sem internet" de "lista
            // fora do ar".
            val internetOk = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL("https://www.gstatic.com/generate_204")
                        .openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    val code = connection.responseCode
                    code in 200..299 || code == 204
                }.getOrDefault(false)
            }
            setRowState(internetRow!!, if (internetOk) CheckState.OK else CheckState.OFF)

            // 2) Lista: só verifica se a internet estiver de pé, usando
            // a mesma playlist que a Home usa de verdade.
            val listOk = if (!internetOk) false else withContext(Dispatchers.IO) {
                runCatching { CatalogRepository.load(this@DiagnosticActivity, force = true).isNotEmpty() }
                    .getOrDefault(false)
            }
            setRowState(listRow!!, if (listOk) CheckState.OK else CheckState.OFF)

            updateSummary(checking = false, internetOk = internetOk, listOk = listOk)
            checking = false
            refreshIcon?.setTextColor(Theme.accentCyan)
        }
    }

    private fun updateSummary(checking: Boolean, internetOk: Boolean, listOk: Boolean) {
        val icon = summaryIcon ?: return
        val text = summaryText ?: return
        when {
            checking -> {
                icon.setText("…")
                icon.setTextColor(Theme.accentCyan)
                text.setText("Verificando...")
            }
            internetOk && listOk -> {
                icon.setText("✓")
                icon.setTextColor(Theme.accentCyan)
                text.setText("Tudo certo! É só aproveitar.")
            }
            !internetOk -> {
                icon.setText("✕")
                icon.setTextColor(Theme.danger)
                text.setText("Sem internet. Verifique seu Wi-Fi ou dados móveis.")
            }
            else -> {
                icon.setText("!")
                icon.setTextColor(Theme.danger)
                text.setText("Sua lista parece fora do ar. Fale com seu revendedor.")
            }
        }
    }

    private fun debugButton(label: String, onClick: () -> Unit): View =
        TextView(this).apply {
            setText(label)
            textSize = 12f
            setTextColor(Theme.textMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            isFocusable = true
            isClickable = true
            wireFocusHighlight()
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(Theme.SPACING_LG)
            }
        }

    private fun showRecentEvents() {
        val message = CatalogRepository.lastMessage.ifBlank { "Nenhum evento registrado ainda." }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
