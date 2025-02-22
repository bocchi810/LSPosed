/*
 * <!--This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors-->
 */
package org.lsposed.manager.ui.fragment

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.MenuProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.databinding.FragmentRepoBinding
import org.lsposed.manager.databinding.ItemOnlinemoduleBinding
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.RepoLoader.RepoListener
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.ui.widget.EmptyStateRecyclerView.EmptyStateAdapter
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule
import org.lsposed.manager.util.ModuleUtil.ModuleListener
import rikka.core.util.LabelComparator
import rikka.core.util.ResourceUtils
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderView.OnBorderVisibilityChangedListener
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import java.util.stream.Collectors
import androidx.core.content.edit

class RepoFragment : BaseFragment(), RepoListener, ModuleListener, MenuProvider {
    protected var binding: FragmentRepoBinding? = null
    protected var searchView: SearchView? = null
    private var mSearchListener: SearchView.OnQueryTextListener? = null
    private val mHandler = Handler(Looper.getMainLooper())
    private var preLoadWebview = true

    private val repoLoader: RepoLoader? = RepoLoader.instance
    private val moduleUtil: ModuleUtil? = ModuleUtil.instance
    private var adapter: RepoAdapter? = null
    private val observer: AdapterDataObserver = object : AdapterDataObserver() {
        override fun onChanged() {
            binding!!.swipeRefreshLayout.setRefreshing(!adapter!!.isDataLoaded())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mSearchListener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                adapter!!.getFilter().filter(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter!!.getFilter().filter(newText)
                return false
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRepoBinding.inflate(getLayoutInflater(), container, false)
        binding!!.appBar.setLiftable(true)
        binding!!.recyclerView.getBorderViewDelegate()
            .setBorderVisibilityChangedListener(OnBorderVisibilityChangedListener { top: Boolean, oldTop: Boolean, bottom: Boolean, oldBottom: Boolean ->
                binding!!.appBar.setLifted(!top)
            })
        setupToolbar(binding!!.toolbar, binding!!.clickView, R.string.module_repo, R.menu.menu_repo)
        binding!!.toolbar.setNavigationIcon(null)
        adapter = RepoAdapter()
        adapter!!.setHasStableIds(true)
        adapter!!.registerAdapterDataObserver(observer)
        binding!!.recyclerView.setAdapter(adapter)
        binding!!.recyclerView.setHasFixedSize(true)
        binding!!.recyclerView.setLayoutManager(LinearLayoutManager(requireActivity()))
        binding!!.recyclerView.fixEdgeEffect(false, true)
        binding!!.swipeRefreshLayout.setOnRefreshListener(OnRefreshListener { adapter!!.fullRefresh() })
        binding!!.swipeRefreshLayout.setProgressViewEndTarget(
            true,
            binding!!.swipeRefreshLayout.getProgressViewEndOffset()
        )
        val l = View.OnClickListener { v: View? ->
            if (searchView!!.isIconified()) {
                binding!!.recyclerView.smoothScrollToPosition(0)
                binding!!.appBar.setExpanded(true, true)
            }
        }
        binding!!.toolbar.setOnClickListener(l)
        binding!!.clickView.setOnClickListener(l)
        repoLoader?.addListener(this)
        moduleUtil?.addListener(this)
        onRepoLoaded()
        return binding!!.getRoot()
    }

    private fun updateRepoSummary() {
        val count = intArrayOf(0)
        val processedModules = HashSet<String?>()
        val modules = moduleUtil?.modules
        if (modules != null && repoLoader?.isRepoLoaded == true) {
            modules.forEach { (k: kotlin.Pair<String?, Int?>?, v: InstalledModule?) ->
                if (!processedModules.contains(
                        k!!.first
                    )
                ) {
                    val ver = repoLoader.getModuleLatestVersion(k.first)
                    if (ver != null && ver.upgradable(v!!.versionCode, v.versionName.toString())) {
                        ++count[0]
                    }
                    processedModules.add(k.first)
                }
            }
        } else {
            count[0] = -1
        }
        runOnUiThread(Runnable {
            if (binding != null) {
                if (count[0] > 0) {
                    binding!!.toolbar.setSubtitle(
                        getResources().getQuantityString(
                            R.plurals.module_repo_upgradable,
                            count[0],
                            count[0]
                        )
                    )
                } else if (count[0] == 0) {
                    binding!!.toolbar.setSubtitle(getResources().getString(R.string.module_repo_up_to_date))
                } else {
                    binding!!.toolbar.setSubtitle(getResources().getString(R.string.loading))
                }
                binding!!.toolbarLayout.setSubtitle(binding!!.toolbar.getSubtitle())
            }
        })
    }

    override fun onPrepareMenu(menu: Menu) {
        searchView = menu.findItem(R.id.menu_search).getActionView() as SearchView?
        if (searchView != null) {
            searchView!!.setOnQueryTextListener(mSearchListener)
            searchView!!.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(arg0: View) {
                    binding!!.appBar.setExpanded(false, true)
                    binding!!.recyclerView.setNestedScrollingEnabled(false)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    binding!!.recyclerView.setNestedScrollingEnabled(true)
                }
            })
            searchView!!.findViewById<View?>(androidx.appcompat.R.id.search_edit_frame)
                .setLayoutDirection(
                    View.LAYOUT_DIRECTION_INHERIT
                )
        }
        val sort = App.preferences.getInt("repo_sort", 0)
        if (sort == 0) {
            menu.findItem(R.id.item_sort_by_name).setChecked(true)
        } else if (sort == 1) {
            menu.findItem(R.id.item_sort_by_update_time).setChecked(true)
        }
        menu.findItem(R.id.item_upgradable_first)
            .setChecked(App.preferences.getBoolean("upgradable_first", true))
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mHandler.removeCallbacksAndMessages(null)
        repoLoader?.removeListener(this)
        moduleUtil?.removeListener(this)
        adapter!!.unregisterAdapterDataObserver(observer)
        binding = null
    }

    override fun onResume() {
        super.onResume()
        adapter!!.refresh()
        if (preLoadWebview) {
            mHandler.postDelayed(Runnable { WebView(requireContext()) }, 500)
            preLoadWebview = false
        }
    }

    override fun onRepoLoaded() {
        if (adapter != null) {
            adapter!!.refresh()
        }
        updateRepoSummary()
    }

    override fun onThrowable(t: Throwable?) {
        showHint(getString(R.string.repo_load_failed, t?.getLocalizedMessage()), true)
        updateRepoSummary()
    }

    override fun onModulesReloaded() {
        updateRepoSummary()
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        val itemId = item.getItemId()
        if (itemId == R.id.item_sort_by_name) {
            item.setChecked(true)
            App.preferences.edit { putInt("repo_sort", 0) }
            adapter!!.refresh()
        } else if (itemId == R.id.item_sort_by_update_time) {
            item.setChecked(true)
            App.preferences.edit { putInt("repo_sort", 1) }
            adapter!!.refresh()
        } else if (itemId == R.id.item_upgradable_first) {
            item.setChecked(!item.isChecked())
            App.preferences.edit { putBoolean("upgradable_first", item.isChecked()) }
            adapter!!.refresh()
        } else {
            return false
        }
        return true
    }

    private inner class RepoAdapter : EmptyStateAdapter<RepoAdapter.ViewHolder?>(), Filterable {
        private var fullList: MutableList<OnlineModule>?
        private var showList: MutableList<OnlineModule>?
        private val labelComparator = LabelComparator()
        override var isLoaded = false
        private val resources: Resources? = App.instance?.getResources()
        private val channels: Array<out String?>? =
            resources?.getStringArray(R.array.update_channel_values)
        private var channel: String? = null
        private val repoLoader: RepoLoader? = RepoLoader.instance

        init {
            showList = mutableListOf<OnlineModule?>() as MutableList<OnlineModule>?
            fullList = showList
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemOnlinemoduleBinding.inflate(getLayoutInflater(), parent, false))
        }

        fun getUpgradableVer(module: OnlineModule): RepoLoader.ModuleVersion? {
            val installedModule = moduleUtil?.getModule(module.name)
            if (installedModule != null) {
                val ver = repoLoader?.getModuleLatestVersion(installedModule.packageName)
                if (ver != null && ver.upgradable(
                        installedModule.versionCode,
                        installedModule.versionName.toString()
                    )
                ) return ver
            }
            return null
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val module = showList!!.get(position)
            holder.appName.setText(module.description)
            holder.appPackageName.setText(module.name)
            val instant: Instant?
            channel = App.preferences.getString("update_channel", channels?.get(0))
            val latestReleaseTime = repoLoader?.getLatestReleaseTime(module.name,
                channel.toString()
            )
            instant =
                Instant.parse(if (latestReleaseTime != null) latestReleaseTime else module.latestReleaseTime)
            val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                .withLocale(App.getLocale(tag)).withZone(ZoneId.systemDefault())
            holder.publishedTime.setText(
                String.format(
                    getString(R.string.module_repo_updated_time),
                    formatter.format(instant)
                )
            )
            var sb = SpannableStringBuilder()

            val summary = module.summary
            if (summary != null) {
                sb.append(summary)
            }
            holder.appDescription.setVisibility(View.VISIBLE)
            holder.appDescription.setText(sb)
            sb = SpannableStringBuilder()
            val upgradableVer = getUpgradableVer(module)
            if (upgradableVer != null) {
                val hint = getString(R.string.update_available, upgradableVer.versionName)
                sb.append(hint)
                val foregroundColorSpan = ForegroundColorSpan(
                    ResourceUtils.resolveColor(
                        requireActivity().getTheme(),
                        com.google.android.material.R.attr.colorPrimary
                    )
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val typefaceSpan =
                        TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                    sb.setSpan(
                        typefaceSpan,
                        sb.length - hint.length,
                        sb.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                } else {
                    val styleSpan = StyleSpan(Typeface.BOLD)
                    sb.setSpan(
                        styleSpan,
                        sb.length - hint.length,
                        sb.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
                sb.setSpan(
                    foregroundColorSpan,
                    sb.length - hint.length,
                    sb.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
            } else if (moduleUtil?.getModule(module.name) != null) {
                val installed = getString(R.string.installed)
                sb.append(installed)
                val styleSpan = StyleSpan(Typeface.ITALIC)
                sb.setSpan(
                    styleSpan,
                    sb.length - installed.length,
                    sb.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
                val foregroundColorSpan = ForegroundColorSpan(
                    ResourceUtils.resolveColor(
                        requireActivity().getTheme(),
                        com.google.android.material.R.attr.colorSecondary
                    )
                )
                sb.setSpan(
                    foregroundColorSpan,
                    sb.length - installed.length,
                    sb.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
            if (sb.length > 0) {
                holder.hint.setVisibility(View.VISIBLE)
                holder.hint.setText(sb)
            } else {
                holder.hint.setVisibility(View.GONE)
            }

            holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                searchView!!.clearFocus()
                safeNavigate(RepoFragmentDirections.actionRepoFragmentToRepoItemFragment(module.name as String))
            })
            holder.itemView.setTooltipText(module.description)
        }

        override fun getItemCount(): Int {
            return showList!!.size
        }

        @SuppressLint("NotifyDataSetChanged")
        fun setLoaded(list: MutableList<OnlineModule>?, isLoaded: Boolean) {
            runOnUiThread(Runnable {
                if (list != null) showList = list
                this.isLoaded = isLoaded
                notifyDataSetChanged()
            })
        }

        fun setData(modules: MutableCollection<OnlineModule?>?) {
            if (modules == null) return
            setLoaded(null, false)
            channel = App.preferences.getString("update_channel", channels?.get(0))
            val sort = App.preferences.getInt("repo_sort", 0)
            val upgradableFirst = App.preferences.getBoolean("upgradable_first", true)
            val upgradable = ConcurrentHashMap<String?, Boolean>()
            fullList = modules.parallelStream().filter((Predicate { onlineModule: OnlineModule? ->
                onlineModule!!.isHide == true && !(repoLoader?.getReleases(
                    onlineModule.name
                ) != null && repoLoader.getReleases(onlineModule.name)!!
                    .isEmpty())
            }))
                .sorted { a: OnlineModule?, b: OnlineModule? ->
                    if (upgradableFirst) {
                        val aUpgrade = upgradable.computeIfAbsent(a!!.name!!) { n: String? ->
                            getUpgradableVer(
                                a
                            ) != null
                        }
                        val bUpgrade = upgradable.computeIfAbsent(b!!.name!!) { n: String? ->
                            getUpgradableVer(
                                b
                            ) != null
                        }
                        if (aUpgrade && !bUpgrade) return@sorted -1
                        else if (!aUpgrade && bUpgrade) return@sorted 1
                    }
                    if (sort == 0) {
                        return@sorted labelComparator.compare(
                            a!!.description,
                            b!!.description
                        )
                    } else {
                        return@sorted Instant.parse(
                            repoLoader?.getLatestReleaseTime(
                                b!!.name,
                                channel.toString()
                            )
                        ).compareTo(
                            Instant.parse(repoLoader?.getLatestReleaseTime(a!!.name,
                                channel.toString()
                            ))
                        )
                    }
                }.collect(Collectors.toList())
            val queryStr = if (searchView != null) searchView!!.getQuery().toString() else ""
            runOnUiThread(Runnable { getFilter().filter(queryStr) })
        }

        fun fullRefresh() {
            runAsync(Runnable {
                setLoaded(null, false)
                repoLoader?.loadRemoteData()
                refresh()
            })
        }

        fun refresh() {
            runAsync(Runnable { adapter!!.setData(repoLoader?.getOnlineModules()) })
        }

        override fun getItemId(position: Int): Long {
            return showList!!.get(position).name.hashCode().toLong()
        }

        override fun getFilter(): Filter {
            return ModuleFilter()
        }

        fun isDataLoaded(): Boolean {
            return isLoaded && repoLoader?.isRepoLoaded == true
        }

        inner class ViewHolder(binding: ItemOnlinemoduleBinding) :
            RecyclerView.ViewHolder(binding.getRoot()) {
            var root: ConstraintLayout?
            var appName: TextView
            var appPackageName: TextView
            var appDescription: TextView
            var hint: TextView
            var publishedTime: TextView

            init {
                root = binding.itemRoot
                appName = binding.appName
                appPackageName = binding.appPackageName
                appDescription = binding.description
                hint = binding.hint
                publishedTime = binding.publishedTime
            }
        }

        inner class ModuleFilter : Filter() {
            private fun lowercaseContains(s: String?, filter: String): Boolean {
                return !TextUtils.isEmpty(s) && s!!.lowercase(Locale.getDefault()).contains(filter)
            }

            override fun performFiltering(constraint: CharSequence): FilterResults {
                val filterResults = FilterResults()
                val filtered = ArrayList<OnlineModule?>()
                val filter = constraint.toString().lowercase(Locale.getDefault())
                for (info in fullList!!) {
                    if (lowercaseContains(info.description, filter) ||
                        lowercaseContains(info.name, filter) ||
                        lowercaseContains(info.summary, filter)
                    ) {
                        filtered.add(info)
                    }
                }
                filterResults.values = filtered
                filterResults.count = filtered.size
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                setLoaded(results.values as MutableList<OnlineModule>?, true)
            }
        }
    }
}
