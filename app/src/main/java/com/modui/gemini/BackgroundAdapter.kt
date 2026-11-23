package com.modui.gemini

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.request.CachePolicy
import com.modui.gemini.databinding.ItemBgAddBinding
import com.modui.gemini.databinding.ItemBgPresetBinding
import java.io.File

sealed class BgOptionItem {
    data class AddTile(
        val label: String,
        val subLabel: String,
        val isAnimatedTab: Boolean
    ) : BgOptionItem()

    data class MediaTile(
        val id: String,
        val title: String,
        val type: BgType,
        val source: BgSource,
        val presetAssetUri: String? = null,
        val customFile: File? = null,
        val cacheKeySuffix: Long = 0L,
        val isSelected: Boolean = false
    ) : BgOptionItem()
}

class BackgroundAdapter(
    private val onAddClicked: (isAnimatedTab: Boolean) -> Unit,
    private val onMediaSelected: (BgOptionItem.MediaTile) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<BgOptionItem>()

    fun submitList(newItems: List<BgOptionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is BgOptionItem.AddTile -> VIEW_TYPE_ADD
        is BgOptionItem.MediaTile -> VIEW_TYPE_MEDIA
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_ADD -> AddViewHolder(ItemBgAddBinding.inflate(inflater, parent, false))
            else -> MediaViewHolder(ItemBgPresetBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is BgOptionItem.AddTile -> (holder as AddViewHolder).bind(item)
            is BgOptionItem.MediaTile -> (holder as MediaViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class AddViewHolder(
        private val binding: ItemBgAddBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BgOptionItem.AddTile) {
            binding.tvAddLabel.text = item.label
            binding.tvAddSubLabel.text = item.subLabel
            binding.cardAddMedia.setOnClickListener {
                onAddClicked(item.isAnimatedTab)
            }
        }
    }

    inner class MediaViewHolder(
        private val binding: ItemBgPresetBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BgOptionItem.MediaTile) {
            val ctx = binding.root.context
            binding.tvPresetTitle.text = item.title

            val isAnimated = item.type == BgType.GIF || item.type == BgType.VIDEO
            binding.badgePlay.visibility = if (isAnimated) View.VISIBLE else View.GONE
            binding.ivSelectedCheck.visibility = if (item.isSelected) View.VISIBLE else View.GONE

            val strokeColorRes = if (item.isSelected) R.color.accent_primary else R.color.surface_stroke
            binding.cardPreset.strokeColor = ContextCompat.getColor(ctx, strokeColorRes)
            binding.cardPreset.strokeWidth = if (item.isSelected) {
                (2.5f * ctx.resources.displayMetrics.density).toInt()
            } else {
                (1.5f * ctx.resources.displayMetrics.density).toInt()
            }

            if (item.source == BgSource.CUSTOM && item.customFile != null) {
                binding.ivThumbnail.load(item.customFile) {
                    crossfade(true)
                    memoryCacheKey("${item.customFile.absolutePath}_${item.cacheKeySuffix}")
                    diskCachePolicy(CachePolicy.DISABLED)
                }
            } else if (item.presetAssetUri != null) {
                binding.ivThumbnail.load(item.presetAssetUri) {
                    crossfade(true)
                }
            }

            binding.cardPreset.setOnClickListener {
                onMediaSelected(item)
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_ADD = 0
        private const val VIEW_TYPE_MEDIA = 1
    }
}
