package com.drivest.navigation.trafficsigns

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivest.navigation.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class TrafficSignsGridAdapter(
    private val scope: CoroutineScope,
    private val imageLoader: TrafficSignsBitmapLoader,
    private val categoryNamesById: () -> Map<String, String>,
    private val onSignClick: (TrafficSign) -> Unit
) : ListAdapter<TrafficSign, TrafficSignsGridAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_traffic_sign_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.trafficSignItemImage)
        private val captionView: TextView = itemView.findViewById(R.id.trafficSignItemCaption)
        private val metaView: TextView = itemView.findViewById(R.id.trafficSignItemMeta)

        fun bind(sign: TrafficSign) {
            captionView.text = sign.caption.ifBlank { itemView.context.getString(R.string.traffic_signs_unknown_caption) }
            val categoryName = categoryNamesById()[sign.primaryCategoryId] ?: sign.officialCategory
            val codePart = sign.code.takeIf { it.isNotBlank() }?.let {
                itemView.context.getString(R.string.traffic_signs_sign_code_value, it)
            }.orEmpty()
            metaView.text = listOf(codePart, categoryName).filter { it.isNotBlank() }.joinToString(" • ")

            itemView.setOnClickListener { onSignClick(sign) }
            imageView.setImageDrawable(null)
            imageView.tag = sign.imageAssetPath
            val density = itemView.resources.displayMetrics.density
            val targetPx = (120f * density).roundToInt()
            scope.launch {
                val bitmap = imageLoader.load(sign.imageAssetPath, targetPx, targetPx)
                if (imageView.tag == sign.imageAssetPath) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TrafficSign>() {
        override fun areItemsTheSame(oldItem: TrafficSign, newItem: TrafficSign): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TrafficSign, newItem: TrafficSign): Boolean {
            return oldItem == newItem
        }
    }
}
