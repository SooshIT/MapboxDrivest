package com.drivest.navigation

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.drivest.navigation.data.TestCentre
import com.drivest.navigation.databinding.ItemTestCentreBinding

class CentreAdapter(
    private val onCentreClick: (TestCentre) -> Unit,
    private val onOfflineDownloadClick: (TestCentre) -> Unit
) : ListAdapter<TestCentre, CentreAdapter.CentreViewHolder>(DiffCallback) {

    private var offlineCentreIds: Set<String> = emptySet()
    private var downloadingCentreIds: Set<String> = emptySet()

    fun setOfflineCentreIds(centreIds: Set<String>) {
        offlineCentreIds = centreIds
        notifyDataSetChanged()
    }

    fun setDownloadingCentreIds(centreIds: Set<String>) {
        downloadingCentreIds = centreIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CentreViewHolder {
        val binding = ItemTestCentreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CentreViewHolder(binding, onCentreClick, onOfflineDownloadClick)
    }

    override fun onBindViewHolder(holder: CentreViewHolder, position: Int) {
        val centre = getItem(position)
        holder.bind(
            centre = centre,
            isOfflineAvailable = offlineCentreIds.contains(centre.id),
            isDownloading = downloadingCentreIds.contains(centre.id)
        )
    }

    class CentreViewHolder(
        private val binding: ItemTestCentreBinding,
        private val onCentreClick: (TestCentre) -> Unit,
        private val onOfflineDownloadClick: (TestCentre) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            centre: TestCentre,
            isOfflineAvailable: Boolean,
            isDownloading: Boolean
        ) {
            binding.centreNameText.text = centre.name
            binding.centreAddressText.text = centre.address
            binding.centreOfflineBadge.visibility = if (isOfflineAvailable) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            binding.centreDownloadButton.text = if (isDownloading) {
                binding.root.context.getString(R.string.centre_picker_downloading)
            } else {
                binding.root.context.getString(R.string.centre_picker_download_offline)
            }
            binding.centreDownloadButton.isEnabled = !isDownloading
            binding.centreDownloadButton.setOnClickListener {
                onOfflineDownloadClick(centre)
            }
            binding.root.setOnClickListener {
                onCentreClick(centre)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TestCentre>() {
        override fun areItemsTheSame(oldItem: TestCentre, newItem: TestCentre): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TestCentre, newItem: TestCentre): Boolean {
            return oldItem == newItem
        }
    }
}
