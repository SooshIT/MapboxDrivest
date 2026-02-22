package com.drivest.navigation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivest.navigation.databinding.ItemPracticeRouteBinding
import com.drivest.navigation.intelligence.RouteDifficultyLabel
import com.drivest.navigation.practice.PracticeRoute
import java.util.Locale
import kotlin.math.roundToInt

class PracticeRouteAdapter(
    private val onRouteClick: (PracticeRoute) -> Unit
) : ListAdapter<PracticeRoute, PracticeRouteAdapter.PracticeRouteViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PracticeRouteViewHolder {
        val binding = ItemPracticeRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PracticeRouteViewHolder(binding, onRouteClick)
    }

    override fun onBindViewHolder(holder: PracticeRouteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PracticeRouteViewHolder(
        private val binding: ItemPracticeRouteBinding,
        private val onRouteClick: (PracticeRoute) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(route: PracticeRoute) {
            binding.routeNameText.text = route.name
            binding.routeMetaText.text = formatRouteMeta(route)
            binding.root.setOnClickListener {
                onRouteClick(route)
            }
        }

        private fun formatRouteMeta(route: PracticeRoute): String {
            val miles = route.distanceM / METERS_IN_MILE
            val roundedMinutes = (route.durationS / 60.0).roundToInt().coerceAtLeast(1)
            val baseMeta = String.format(Locale.UK, "%.1f mi  ·  %d min", miles, roundedMinutes)
            val intelligence = route.intelligence ?: return baseMeta
            val context = binding.root.context
            val difficulty = when (intelligence.difficultyLabel) {
                RouteDifficultyLabel.EASY -> context.getString(R.string.route_difficulty_easy)
                RouteDifficultyLabel.MEDIUM -> context.getString(R.string.route_difficulty_medium)
                RouteDifficultyLabel.HARD -> context.getString(R.string.route_difficulty_hard)
            }
            return "$baseMeta  ·  $difficulty (${intelligence.complexityScore})"
        }

        private companion object {
            private const val METERS_IN_MILE = 1609.344
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PracticeRoute>() {
        override fun areItemsTheSame(oldItem: PracticeRoute, newItem: PracticeRoute): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PracticeRoute, newItem: PracticeRoute): Boolean {
            return oldItem == newItem
        }
    }
}
