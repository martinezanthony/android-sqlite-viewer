package io.github.martinezanthony.sqliteviewer.ui.main

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.martinezanthony.sqliteviewer.R
import io.github.martinezanthony.sqliteviewer.databinding.ItemRecentFileBinding
import io.github.martinezanthony.sqliteviewer.utils.FileUtils

/**
 * RecyclerView adapter for the recent files list on MainActivity.
 */

class RecentFilesAdapter(
    private var uris: List<Uri>,
    private val onClick: (Uri) -> Unit,
    private val onDeleteClick: (Uri) -> Unit
) : RecyclerView.Adapter<RecentFilesAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemRecentFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentFileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = uris[position]
        val context = holder.itemView.context

        holder.binding.tvFileName.text =
            FileUtils.getDisplayName(context.contentResolver, uri)
                ?: uri.path?.substringAfterLast('/')
                        ?: context.getString(R.string.recent_file_unknown)

        holder.binding.tvFilePath.text = uri.toString()
        holder.binding.containerInfo.setOnClickListener { onClick(uri) }
        holder.binding.btnDeleteContainer.setOnClickListener { onDeleteClick(uri) }
    }

    override fun getItemCount() = uris.size

    fun updateData(newUris: List<Uri>) {
        uris = newUris
        notifyDataSetChanged()
    }
}