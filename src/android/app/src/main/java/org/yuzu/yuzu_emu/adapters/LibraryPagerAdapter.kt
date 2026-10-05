// SPDX-FileCopyrightText: 2026 Moonwitch Project
// SPDX-License-Identifier: GPL-3.0-or-later
package org.yuzu.yuzu_emu.adapters

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.Disposable
import coil.request.ImageRequest
import org.yuzu.yuzu_emu.R
import org.yuzu.yuzu_emu.YuzuApplication
import org.yuzu.yuzu_emu.databinding.MwGamePageBinding
import org.yuzu.yuzu_emu.model.Game
import org.yuzu.yuzu_emu.utils.DirectoryInitialization
import org.yuzu.yuzu_emu.utils.GameIconFetcher
import org.yuzu.yuzu_emu.utils.GameIconKeyer
import java.io.File

class LibraryPagerAdapter(
    private val owner: LifecycleOwner,
    private val open: (Game, Boolean) -> Unit,
    private val favorite: (Game) -> Unit,
    private val step: (Int) -> Unit
) : ListAdapter<Game, LibraryPagerAdapter.Page>(object : DiffUtil.ItemCallback<Game>() {
    override fun areItemsTheSame(old: Game, new: Game) = old.path == new.path && old.programId == new.programId
    override fun areContentsTheSame(old: Game, new: Game) = old == new
}) {
    private val context = YuzuApplication.appContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val loader = ImageLoader.Builder(context)
        .components { add(GameIconKeyer()); add(GameIconFetcher.Factory()) }
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.06).build() }
        .build()
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Page(
        MwGamePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Page, position: Int) = holder.bind(getItem(position), position)
    override fun onViewRecycled(holder: Page) { holder.clear(); super.onViewRecycled(holder) }
    override fun onCurrentListChanged(previousList: MutableList<Game>, currentList: MutableList<Game>) {
        // Position labels and edge buttons depend on the entire filtered list.
        notifyItemRangeChanged(0, itemCount)
    }
    fun refreshFavorites() = notifyItemRangeChanged(0, itemCount)
    fun refreshArtwork() = notifyItemRangeChanged(0, itemCount)
    fun close() = loader.shutdown()

    inner class Page(private val binding: MwGamePageBinding) : RecyclerView.ViewHolder(binding.root) {
        private var artworkRequest: Disposable? = null
        private var iconRequest: Disposable? = null
        fun clear() {
            artworkRequest?.dispose()
            iconRequest?.dispose()
            binding.artwork.setImageDrawable(null)
            binding.gameIcon.setImageDrawable(null)
        }
        fun bind(game: Game, position: Int) {
            clear()
            binding.gameTitle.text = game.title.replace(Regex("[\\t\\n\\r]+"), " ")
            binding.gameMeta.text = listOf(game.developer, game.version).filter(String::isNotBlank).joinToString(" · ")
            binding.gameMeta.isVisible = binding.gameMeta.text.isNotBlank()
            binding.pageCount.text = context.getString(R.string.mw_reform_count, position + 1, itemCount)
            binding.play.setOnClickListener { open(game, true) }
            binding.details.setOnClickListener { open(game, false) }
            val isFavorite = preferences.getBoolean(game.keyFavorite, false)
            binding.favorite.setImageResource(if (isFavorite) R.drawable.ic_mw_star_filled else R.drawable.ic_mw_star)
            binding.favorite.isSelected = isFavorite
            binding.favorite.setOnClickListener { favorite(game) }
            binding.previous.isEnabled = position > 0
            binding.previous.alpha = if (position > 0) 1f else 0.3f
            binding.next.isEnabled = position < itemCount - 1
            binding.next.alpha = if (position < itemCount - 1) 1f else 0.3f
            binding.previous.setOnClickListener { step(-1) }
            binding.next.setOnClickListener { step(1) }
            val artwork = findArtwork(game)
            binding.gameIcon.isVisible = artwork == null
            binding.artwork.alpha = if (artwork != null) 1f else 0.35f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                binding.artwork.setRenderEffect(if (artwork == null) RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP) else null)
            }
            val data: Any = artwork ?: game
            artworkRequest = loader.enqueue(ImageRequest.Builder(context)
                .data(data).lifecycle(owner).size(1280, 1280)
                .memoryCacheKey(if (artwork != null) "${artwork.path}:${artwork.lastModified()}" else "hero:${game.path}:${game.version}")
                .target(binding.artwork).error(R.drawable.ic_moonwitch_mark).build())
            if (artwork == null) {
                iconRequest = loader.enqueue(ImageRequest.Builder(context).data(game).lifecycle(owner)
                    .size(256, 256).target(binding.gameIcon).error(R.drawable.ic_moonwitch_mark).build())
            }
        }
    }
    private fun findArtwork(game: Game): File? = runCatching {
        val dir = File(DirectoryInitialization.userDirectory, "moonwitch/metadata/${game.settingsName}")
        listOf("hero", "background", "cover", "poster", "boxart").asSequence()
            .flatMap { name -> sequenceOf("jpg", "jpeg", "png", "webp").map { File(dir, "$name.$it") } }
            .firstOrNull { it.isFile }
    }.getOrNull()
}
