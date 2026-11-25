package com.elanticristo616.browser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.elanticristo616.browser.databinding.ItemSiteBinding

/** Rejilla de sitios con su favicon; filtra por nombre al escribir. */
class SitesAdapter(
    private val scope: LifecycleCoroutineScope,
    private val onSiteClick: (SitesRepository.Site) -> Unit
) : RecyclerView.Adapter<SitesAdapter.SiteViewHolder>() {

    private var allSites: List<SitesRepository.Site> = emptyList()
    private var shown: List<SitesRepository.Site> = emptyList()

    fun setSites(sites: List<SitesRepository.Site>) {
        allSites = sites
        shown = sites
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        shown = if (q.isBlank()) {
            allSites
        } else {
            allSites.filter {
                it.displayName.lowercase().contains(q) ||
                    it.domain.lowercase().contains(q)
            }
        }
        notifyDataSetChanged()
    }

    val shownCount: Int get() = shown.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val binding = ItemSiteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SiteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        holder.bind(shown[position])
    }

    override fun getItemCount(): Int = shown.size

    inner class SiteViewHolder(private val binding: ItemSiteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(site: SitesRepository.Site) {
            binding.tvSiteName.text = site.displayName
            binding.ivSiteIcon.setImageResource(android.R.drawable.ic_menu_gallery)
            if (site.domain.isNotBlank()) {
                SitesRepository.loadFavicon(scope, site.domain, binding.ivSiteIcon)
            }
            binding.root.setOnClickListener { onSiteClick(site) }
        }
    }
}
