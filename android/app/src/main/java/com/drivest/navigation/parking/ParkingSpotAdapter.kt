package com.drivest.navigation.parking

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivest.navigation.R
import com.drivest.navigation.databinding.ItemParkingSpotBinding

class ParkingSpotAdapter(
    private val onSpotSelected: (ParkingSpot) -> Unit
) : ListAdapter<ParkingSpot, ParkingSpotAdapter.ParkingSpotViewHolder>(DiffCallback) {

    var selectedSpotId: String? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingSpotViewHolder {
        val binding = ItemParkingSpotBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ParkingSpotViewHolder(binding, onSpotSelected)
    }

    override fun onBindViewHolder(holder: ParkingSpotViewHolder, position: Int) {
        holder.bind(getItem(position), selectedSpotId)
    }

    class ParkingSpotViewHolder(
        private val binding: ItemParkingSpotBinding,
        private val onSpotSelected: (ParkingSpot) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(spot: ParkingSpot, selectedId: String?) {
            binding.parkingTitle.text = spot.title
            binding.parkingDistance.text = binding.root.context.getString(
                R.string.parking_distance_value,
                spot.distanceMetersToDestination
            )
            binding.parkingRulesSummary.text = spot.rulesSummary

            renderFeeBadge(spot)
            renderConfidenceBadge(spot)
            renderSelection(spot.id == selectedId)

            binding.root.setOnClickListener {
                onSpotSelected(spot)
            }
        }

        private fun renderFeeBadge(spot: ParkingSpot) {
            val context = binding.root.context
            val (label, bg, text) = when (spot.feeFlag) {
                ParkingFeeFlag.LIKELY_FREE ->
                    Triple(R.string.parking_fee_free, R.color.parking_fee_free_bg, R.color.parking_fee_free_text)
                ParkingFeeFlag.LIKELY_PAID ->
                    Triple(R.string.parking_fee_paid, R.color.parking_fee_paid_bg, R.color.parking_fee_paid_text)
                ParkingFeeFlag.UNKNOWN ->
                    Triple(R.string.parking_fee_unknown, R.color.parking_fee_unknown_bg, R.color.parking_fee_unknown_text)
            }
            binding.parkingFeeBadge.text = context.getString(label)
            binding.parkingFeeBadge.backgroundTintList = ContextCompat.getColorStateList(context, bg)
            binding.parkingFeeBadge.setTextColor(ContextCompat.getColor(context, text))
        }

        private fun renderConfidenceBadge(spot: ParkingSpot) {
            val context = binding.root.context
            val (label, bg, text) = when (confidenceLabel(spot.confidenceScore)) {
                ConfidenceLabel.LOW ->
                    Triple(
                        R.string.parking_confidence_low,
                        R.color.parking_confidence_low_bg,
                        R.color.parking_confidence_low_text
                    )
                ConfidenceLabel.MEDIUM ->
                    Triple(
                        R.string.parking_confidence_medium,
                        R.color.parking_confidence_medium_bg,
                        R.color.parking_confidence_medium_text
                    )
                ConfidenceLabel.HIGH ->
                    Triple(
                        R.string.parking_confidence_high,
                        R.color.parking_confidence_high_bg,
                        R.color.parking_confidence_high_text
                    )
            }
            binding.parkingConfidenceBadge.text = context.getString(label)
            binding.parkingConfidenceBadge.backgroundTintList = ContextCompat.getColorStateList(context, bg)
            binding.parkingConfidenceBadge.setTextColor(ContextCompat.getColor(context, text))
        }

        private fun confidenceLabel(score: Int): ConfidenceLabel {
            return when {
                score >= 75 -> ConfidenceLabel.HIGH
                score >= 50 -> ConfidenceLabel.MEDIUM
                else -> ConfidenceLabel.LOW
            }
        }

        private fun renderSelection(isSelected: Boolean) {
            val context = binding.root.context
            val strokeColor = if (isSelected) {
                ContextCompat.getColor(context, R.color.ui_hex_F37121)
            } else {
                ContextCompat.getColor(context, R.color.app_card_stroke)
            }
            binding.parkingCard.strokeColor = strokeColor
        }
    }

    private enum class ConfidenceLabel {
        LOW,
        MEDIUM,
        HIGH
    }

    private object DiffCallback : DiffUtil.ItemCallback<ParkingSpot>() {
        override fun areItemsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParkingSpot, newItem: ParkingSpot): Boolean {
            return oldItem == newItem
        }
    }
}
