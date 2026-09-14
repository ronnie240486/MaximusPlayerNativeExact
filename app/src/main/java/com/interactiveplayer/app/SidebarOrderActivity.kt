package com.interactiveplayer.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * Arrastar pra reordenar categorias. Duas seções diferentes:
 * - "sidebar": os itens fixos da barra lateral da Home (Início,
 *   Canais, Filmes...).
 * - qualquer outro valor ("channels", "MOVIE", "SERIES", "KIDS"): as
 *   categorias de VERDADE de dentro daquela aba (ex: nomes dos grupos
 *   de canais, ou os gêneros de filme/série) — que é o que realmente
 *   foi pedido ("dentro das abas de canais e de filmes e séries").
 */
class SidebarOrderActivity : ComponentActivity() {

    private lateinit var adapter: OrderAdapter
    private lateinit var section: String
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        section = intent.getStringExtra("section") ?: "sidebar"
        setContentView(buildScreen())
        if (section != "sidebar") loadRealCategories()
    }

    private fun sectionTitle(): String = when (section) {
        "sidebar" -> "Ordem da barra lateral"
        "channels" -> "Ordem das categorias de Canais"
        "MOVIE" -> "Ordem das categorias de Filmes"
        "SERIES" -> "Ordem das categorias de Séries"
        "KIDS" -> "Ordem das categorias de Kids"
        else -> "Ordem das categorias"
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.black)
            setPadding(dp(Theme.SPACING_MD), dp(Theme.SPACING_MD), dp(Theme.SPACING_MD), dp(Theme.SPACING_MD))
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 24f
            setTextColor(Theme.white)
            setPadding(dp(4), dp(4), dp(10), dp(4))
            isFocusable = true
            isClickable = true
            wireFocusHighlightCircle()
            setOnClickListener { saveAndFinish() }
        })
        header.addView(TextView(this).apply {
            text = sectionTitle()
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        root.addView(TextView(this).apply {
            text = "Segure e arraste pra mudar a ordem que aparece na lista."
            textSize = 13f
            setTextColor(Theme.textSecondary)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.SPACING_MD) })

        status = TextView(this).apply {
            text = "Carregando categorias..."
            textSize = 13f
            setTextColor(Theme.textMuted)
            visibility = if (section == "sidebar") View.GONE else View.VISIBLE
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.SPACING_SM) })

        val recycler = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@SidebarOrderActivity) }
        val initial = if (section == "sidebar") {
            SidebarOrderStore.load(this, MainActivity.SIDEBAR_LABELS).toMutableList()
        } else {
            mutableListOf()
        }
        adapter = OrderAdapter(initial)
        recycler.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                adapter.move(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        touchHelper.attachToRecyclerView(recycler)

        root.addView(recycler, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    /** Pra Canais/Filmes/Séries/Kids, busca as categorias DE VERDADE do catálogo atual — não uma lista fixa. */
    private fun loadRealCategories() {
        lifecycleScope.launch {
            val items = CatalogRepository.load(this@SidebarOrderActivity)
            val groups = when (section) {
                "channels" -> items.filter { it.kind == M3uItem.Kind.CHANNEL }
                "MOVIE" -> items.filter { it.kind == M3uItem.Kind.MOVIE }
                "SERIES" -> items.filter { it.kind == M3uItem.Kind.SERIES }
                "KIDS" -> items.filter { it.kind == M3uItem.Kind.KIDS }
                else -> emptyList()
            }.map { it.group }.distinct().sorted()

            if (groups.isEmpty()) {
                status.setText("Nenhuma categoria encontrada — abra essa aba pelo menos uma vez primeiro.")
                return@launch
            }
            status.visibility = View.GONE
            adapter.submit(CategoryOrderStore.load(this@SidebarOrderActivity, section, groups))
        }
    }

    private fun saveAndFinish() {
        if (section == "sidebar") {
            SidebarOrderStore.save(this, adapter.items())
        } else if (adapter.items().isNotEmpty()) {
            CategoryOrderStore.save(this, section, adapter.items())
        }
        finish()
    }

    override fun onBackPressed() {
        saveAndFinish()
    }
}

private class OrderAdapter(private val items: MutableList<String>) : RecyclerView.Adapter<OrderAdapter.Holder>() {

    fun items(): List<String> = items

    fun submit(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = context.roundRect(Theme.darkSurface, Theme.RADIUS_SM)
            setPadding(context.dp(Theme.SPACING_MD), context.dp(14), context.dp(Theme.SPACING_MD), context.dp(14))
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                (this as? ViewGroup.MarginLayoutParams)?.bottomMargin = context.dp(6)
            }
        }
        row.addView(TextView(context).apply {
            text = "☰"
            textSize = 16f
            setTextColor(Theme.textMuted)
        }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = context.dp(Theme.SPACING_SM) })
        val label = TextView(context).apply {
            textSize = 15f
            setTextColor(Theme.white)
            tag = "label"
        }
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        return Holder(row, label)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.label.setText(items[position])
    }

    class Holder(view: LinearLayout, val label: TextView) : RecyclerView.ViewHolder(view)
}
