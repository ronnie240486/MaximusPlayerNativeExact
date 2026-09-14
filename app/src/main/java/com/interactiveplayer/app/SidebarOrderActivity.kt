package com.interactiveplayer.app

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Arrastar pra reordenar os itens da barra lateral da Home — pedido
 * explícito: "arrastar jogos do dia lá em cima na primeira".
 */
class SidebarOrderActivity : ComponentActivity() {

    private lateinit var adapter: OrderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
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
            text = "Ordem das categorias"
            textSize = 20f
            setTextColor(Theme.white)
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        root.addView(TextView(this).apply {
            text = "Segure e arraste pra mudar a ordem que aparece na barra lateral da Home."
            textSize = 13f
            setTextColor(Theme.textSecondary)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Theme.SPACING_MD) })

        val recycler = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@SidebarOrderActivity) }
        val current = SidebarOrderStore.load(this, MainActivity.SIDEBAR_LABELS).toMutableList()
        adapter = OrderAdapter(current)
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

    private fun saveAndFinish() {
        SidebarOrderStore.save(this, adapter.items())
        finish()
    }

    override fun onBackPressed() {
        saveAndFinish()
    }
}

private class OrderAdapter(private val items: MutableList<String>) : RecyclerView.Adapter<OrderAdapter.Holder>() {

    fun items(): List<String> = items

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
