package io.github.martinezanthony.sqliteviewer.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.github.martinezanthony.sqliteviewer.R

/**
 * Displays query result rows as horizontally scrollable cells.
 * Long-press on any row copies its content to the clipboard
 */
class TableResultsAdapter(
    private val rows: List<List<String?>>,
    private val columnWidths: IntArray,
    private val cellPadding: Int,
    private val maxCellWidthPx: Int
) : RecyclerView.Adapter<TableResultsAdapter.RowViewHolder>() {

    class RowViewHolder(val rowLayout: LinearLayout) : RecyclerView.ViewHolder(rowLayout)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val context = parent.context
        val rowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val attrs = intArrayOf(android.R.attr.selectableItemBackground)
            context.obtainStyledAttributes(attrs).also {
                background = it.getDrawable(0)
                it.recycle()
            }
            isClickable = true
            isFocusable = true
        }

        columnWidths.forEach { width ->
            val tv = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(cellPadding, cellPadding, cellPadding, cellPadding)
                if (width >= maxCellWidthPx) {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                } else {
                    maxLines = Int.MAX_VALUE
                    ellipsize = null
                }
            }
            rowLayout.addView(tv)
        }

        return RowViewHolder(rowLayout)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val rowData = rows[position]

        columnWidths.indices.forEach { i ->
            (holder.rowLayout.getChildAt(i) as TextView).text = rowData.getOrNull(i) ?: ""
        }

        holder.rowLayout.setOnLongClickListener {
            copyRowToClipboard(it.context, rowData)
            true
        }
    }

    override fun getItemCount() = rows.size

    private fun copyRowToClipboard(context: Context, rowData: List<String?>) {
        val text = rowData.joinToString(separator = "\t") { it ?: "" }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("row", text))
            Toast.makeText(context, R.string.toast_row_copied, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_copy_error, e.message ?: ""),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}