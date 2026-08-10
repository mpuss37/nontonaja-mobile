package com.example.nonton_aja.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.nonton_aja.R
import com.example.nonton_aja.data.SearchItem

class FilmAdapter(
    private val onClick: (SearchItem) -> Unit
) : ListAdapter<SearchItem, FilmAdapter.FilmViewHolder>(FilmDiffCallback()) {

    class FilmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.filmImage)
        val title: TextView = view.findViewById(R.id.filmTitle)
        val year: TextView = view.findViewById(R.id.filmYear)
        val source: TextView = view.findViewById(R.id.filmSource)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_film, parent, false)
        return FilmViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilmViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.year.text = item.year
        holder.source.text = item.source.uppercase()
        holder.image.load(item.image) {
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class FilmDiffCallback : DiffUtil.ItemCallback<SearchItem>() {
        override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem) =
            "${oldItem.source}_${oldItem.id}" == "${newItem.source}_${newItem.id}"

        override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem) =
            oldItem == newItem
    }
}
