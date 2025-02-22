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
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Request
import org.lsposed.manager.App
import org.lsposed.manager.R
import org.lsposed.manager.databinding.FragmentPagerBinding
import org.lsposed.manager.databinding.ItemRepoLoadmoreBinding
import org.lsposed.manager.databinding.ItemRepoReadmeBinding
import org.lsposed.manager.databinding.ItemRepoRecyclerviewBinding
import org.lsposed.manager.databinding.ItemRepoReleaseBinding
import org.lsposed.manager.databinding.ItemRepoTitleDescriptionBinding
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.RepoLoader.RepoListener
import org.lsposed.manager.repo.model.Collaborator
import org.lsposed.manager.repo.model.OnlineModule
import org.lsposed.manager.repo.model.Release
import org.lsposed.manager.repo.model.ReleaseAsset
import org.lsposed.manager.ui.dialog.BlurBehindDialogBuilder
import org.lsposed.manager.ui.widget.EmptyStateRecyclerView.EmptyStateAdapter
import org.lsposed.manager.ui.widget.LinkifyTextView
import org.lsposed.manager.util.NavUtil
import org.lsposed.manager.util.SimpleStatefulAdaptor
import org.lsposed.manager.util.chrome.CustomTabsURLSpan
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderView
import rikka.widget.borderview.BorderView.OnBorderVisibilityChangedListener
import java.io.ByteArrayInputStream
import java.lang.Boolean
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.function.IntUnaryOperator
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.CharSequence
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Throwable
import kotlin.arrayOfNulls
import kotlin.intArrayOf
import androidx.core.view.isGone

class RepoItemFragment : BaseFragment(), RepoListener, MenuProvider {
    var binding: FragmentPagerBinding? = null
    var module: OnlineModule? = null
    private var releaseAdapter: ReleaseAdapter? = null
    private var informationAdapter: InformationAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPagerBinding.inflate(getLayoutInflater(), container, false)
        if (module == null) return binding!!.getRoot()
        val modulePackageName = module!!.name
        val moduleName = module!!.description
        binding!!.appBar.setLiftable(true)
        setupToolbar(binding!!.toolbar, binding!!.clickView, moduleName, R.menu.menu_repo_item)
        binding!!.clickView.setTooltipText(moduleName)
        binding!!.toolbar.setSubtitle(modulePackageName)
        binding!!.viewPager.setAdapter(PagerAdapter(this))
        val titles = intArrayOf(
            R.string.module_readme,
            R.string.module_releases,
            R.string.module_information
        )
        TabLayoutMediator(
            binding!!.tabLayout,
            binding!!.viewPager,
            TabConfigurationStrategy { tab: TabLayout.Tab?, position: Int -> tab!!.setText(titles[position]) }).attach()

        binding!!.tabLayout.addOnLayoutChangeListener(OnLayoutChangeListener { view: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int ->
            val vg = binding!!.tabLayout.getChildAt(0) as ViewGroup
            val tabLayoutWidth = IntStream.range(0, binding!!.tabLayout.getTabCount()).map(
                IntUnaryOperator { i: Int -> vg.getChildAt(i).getWidth() }).sum()
            if (tabLayoutWidth <= binding!!.getRoot().getWidth()) {
                binding!!.tabLayout.setTabMode(TabLayout.MODE_FIXED)
                binding!!.tabLayout.setTabGravity(TabLayout.GRAVITY_FILL)
            }
        })
        binding!!.toolbar.setOnClickListener(View.OnClickListener { v: View? ->
            binding!!.appBar.setExpanded(
                true,
                true
            )
        })
        releaseAdapter = ReleaseAdapter(isLoaded = true)
        informationAdapter = InformationAdapter()
        RepoLoader.instance?.addListener(this)
        return binding!!.getRoot()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        RepoLoader.instance?.addListener(this)
        super.onCreate(savedInstanceState)

        val modulePackageName =
            if (getArguments() == null) null else getArguments()?.getString("modulePackageName")
        module = RepoLoader.instance?.getOnlineModule(modulePackageName)
        if (module == null) {
            if (!safeNavigate(R.id.action_repo_item_fragment_to_repo_fragment)) {
                safeNavigate(R.id.repo_nav)
            }
        }
    }

    private fun renderGithubMarkdown(view: WebView, text: String?) {
        var text = text
        try {
            view.setBackgroundColor(Color.TRANSPARENT)
            val setting = view.getSettings()
            setting.setOffscreenPreRaster(true)
            setting.setDomStorageEnabled(true)
            setting.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW)
            setting.setAllowContentAccess(false)
            setting.setAllowFileAccessFromFileURLs(true)
            setting.setAllowFileAccess(false)
            setting.setGeolocationEnabled(false)
            setting.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK)
            setting.setTextZoom(80)
            val body: String?
            val direction: String?
            if (getResources().getConfiguration()
                    .getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
            ) {
                direction = "rtl"
            } else {
                direction = "ltr"
            }
            if (text == null) {
                text = "<center>" + App.instance?.getString(R.string.list_empty) + "</center>"
            }
            if (ResourceUtils.isNightMode(getResources().getConfiguration())) {
                body =
                    App.HTML_TEMPLATE_DARK.get()?.replace("@dir@", direction)?.replace("@body@", text)
            } else {
                body = App.HTML_TEMPLATE.get()?.replace("@dir@", direction)?.replace("@body@", text)
            }
            view.setWebViewClient(object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest
                ): kotlin.Boolean {
                    NavUtil.startURL(requireActivity(), request.getUrl())
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest
                ): WebResourceResponse? {
                    if (!request.getUrl().getScheme()!!.startsWith("http")) return null
                    val client = App.okHttpClient
                    val call = client?.newCall(
                        Request.Builder()
                            .url(request.getUrl().toString())
                            .method(request.getMethod(), null)
                            .headers(request.getRequestHeaders().toHeaders())
                            .build()
                    )
                    try {
                        val reply = call?.execute()
                        val header = reply?.header("content-type", "image/*;charset=utf-8")
                        var contentTypes = arrayOfNulls<String>(0)
                        if (header != null) {
                            contentTypes =
                                header.split(";\\s*".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()
                        }
                        val mimeType = if (contentTypes.size > 0) contentTypes[0] else "image/*"
                        val charset: String? =
                            if (contentTypes.size > 1) contentTypes[1]!!.split("=\\s*".toRegex())
                                .dropLastWhile { it.isEmpty() }.toTypedArray()[1] else "utf-8"
                        val body = reply?.body
                        if (body == null) return null
                        return WebResourceResponse(
                            mimeType,
                            charset,
                            body.byteStream()
                        )
                    } catch (e: Throwable) {
                        return WebResourceResponse(
                            "text/html", "utf-8", ByteArrayInputStream(
                                Log.getStackTraceString(e).toByteArray(
                                    StandardCharsets.UTF_8
                                )
                            )
                        )
                    }
                }
            })
            view.loadDataWithBaseURL(
                "https://github.com", body.toString(), "text/html",
                StandardCharsets.UTF_8.name(), null
            )
        } catch (e: Throwable) {
            Log.e(App.TAG, "render readme", e)
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    }

    override fun onMenuItemSelected(item: MenuItem): kotlin.Boolean {
        val id = item.getItemId()
        if (id == R.id.menu_open_in_browser) {
            NavUtil.startURL(
                requireActivity(),
                "https://modules.lsposed.org/module/" + module!!.name
            )
            return true
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        RepoLoader.instance?.removeListener(this)
        binding = null
    }

    override fun onModuleReleasesLoaded(module: OnlineModule?) {
        this.module = module
        val repoLoader = RepoLoader.instance
        if (releaseAdapter != null) {
            runAsync(Runnable { releaseAdapter!!.loadItems() })
        }
        if ((if (repoLoader?.getReleases(module?.name) != null) repoLoader.getReleases(module?.name)!!.size else 1) == 1) {
            showHint(R.string.module_release_no_more, true)
        }
    }

    override fun onThrowable(t: Throwable?) {
        if (releaseAdapter != null) {
            runAsync(Runnable { releaseAdapter!!.loadItems() })
        }
        showHint(getString(R.string.repo_load_failed, t?.getLocalizedMessage()), true)
    }

    private inner class InformationAdapter :
        SimpleStatefulAdaptor<InformationAdapter.ViewHolder?>() {
        private var rowCount = 0
        private var homepageRow = -1
        private var collaboratorsRow = -1
        private var sourceUrlRow = -1

        init {
            if (!TextUtils.isEmpty(module!!.homepageUrl)) {
                homepageRow = rowCount++
            }
            if (module!!.collaborators != null && !module!!.collaborators!!.isEmpty()) {
                collaboratorsRow = rowCount++
            }
            if (!TextUtils.isEmpty(module!!.sourceUrl)) {
                sourceUrlRow = rowCount++
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemRepoTitleDescriptionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            if (position == homepageRow) {
                holder.title.setText(R.string.module_information_homepage)
                holder.description.setText(module!!.homepageUrl)
            } else if (position == collaboratorsRow) {
                val collaborators = module!!.collaborators
                if (collaborators == null) return
                holder.title.setText(R.string.module_information_collaborators)
                val sb = SpannableStringBuilder()
                val iterator: MutableListIterator<Collaborator> = collaborators.listIterator() as MutableListIterator<Collaborator>
                while (iterator.hasNext()) {
                    val collaborator = iterator.next()
                    val collaboratorLogin = collaborator.login
                    if (collaboratorLogin == null) continue
                    val name =
                        if (collaborator.name == null) collaboratorLogin else collaborator.name
                    sb.append(name)
                    val span = CustomTabsURLSpan(
                        requireActivity(),
                        String.format("https://github.com/%s", collaborator.login)
                    )
                    sb.setSpan(
                        span,
                        sb.length - name!!.length,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    if (iterator.hasNext()) {
                        sb.append(", ")
                    }
                }
                holder.description.setText(sb)
            } else if (position == sourceUrlRow) {
                holder.title.setText(R.string.module_information_source_url)
                holder.description.setText(module!!.sourceUrl)
            }
            holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                if (position == homepageRow) {
                    NavUtil.startURL(requireActivity(), module!!.homepageUrl)
                } else if (position == collaboratorsRow) {
                    val span = holder.description.currentSpan
                    holder.description.clearCurrentSpan()

                    if (span is CustomTabsURLSpan) {
                        span.onClick(v!!)
                    }
                } else if (position == sourceUrlRow) {
                    NavUtil.startURL(requireActivity(), module!!.sourceUrl)
                }
            })
        }

        override fun getItemCount(): Int {
            return rowCount
        }

        inner class ViewHolder(binding: ItemRepoTitleDescriptionBinding) :
            RecyclerView.ViewHolder(binding.getRoot()) {
            var title: TextView
            var description: LinkifyTextView

            init {
                title = binding.title
                description = binding.description
            }
        }
    }

    class DownloadDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val args = requireArguments() // Use requireArguments() for null safety
            val names = args.getCharSequenceArray("names") ?: emptyArray() // Handle null case

            return BlurBehindDialogBuilder(
                requireActivity(),
                R.style.ThemeOverlay_MaterialAlertDialog_Centered_FullWidthButtons
            )
                .setTitle(R.string.module_release_view_assets)
                .setPositiveButton(android.R.string.cancel, null)
                .setAdapter(
                    ArrayAdapter(
                        requireActivity(),
                        R.layout.dialog_item,
                        names.toList()
                    ),
                    DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                        val urls = args.getStringArrayList("urls") ?: return@OnClickListener
                        NavUtil.startURL(requireActivity(), urls[which])
                    }
                )
                .create()
        }

        companion object {
            fun create(
                activity: Activity,
                fm: FragmentManager,
                assets: MutableList<ReleaseAsset?>
            ) {
                val f = DownloadDialog()
                val bundle = Bundle()

                val displayNames = arrayOfNulls<CharSequence>(assets.size)
                for (i in assets.indices) {
                    val sb = SpannableStringBuilder(assets.get(i)!!.name)
                    val count = assets.get(i)!!.downloadCount
                    val countStr = activity.getResources().getQuantityString(
                        R.plurals.module_release_assets_download_count,
                        count,
                        count
                    )
                    val sizeStr =
                        Formatter.formatShortFileSize(activity, assets.get(i)!!.size.toLong())
                    sb.append('\n').append(sizeStr).append('/').append(countStr)
                    val foregroundColorSpan = ForegroundColorSpan(
                        ResourceUtils.resolveColor(
                            activity.getTheme(),
                            android.R.attr.textColorSecondary
                        )
                    )
                    val relativeSizeSpan = RelativeSizeSpan(0.8f)
                    sb.setSpan(
                        foregroundColorSpan,
                        sb.length - sizeStr.length - countStr.length - 1,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    sb.setSpan(
                        relativeSizeSpan,
                        sb.length - sizeStr.length - countStr.length - 1,
                        sb.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    displayNames[i] = sb
                }
                bundle.putCharSequenceArray("names", displayNames)
                bundle.putStringArrayList(
                    "urls",
                    assets.stream().map<String?> { obj: ReleaseAsset? -> obj!!.downloadUrl }
                        .collect(
                            Collectors.toCollection(Supplier { ArrayList() })
                        )
                )
                f.setArguments(bundle)
                f.show(fm, "download")
            }
        }
    }

    private inner class ReleaseAdapter(override val isLoaded: kotlin.Boolean
    ) : EmptyStateAdapter<ReleaseAdapter.ViewHolder?>() {
        private var items: MutableList<Release>? = java.util.ArrayList<Release>()
        private val resources: Resources? = App.instance?.getResources()

        init {
            runAsync(Runnable { this.loadItems() })
        }

        @SuppressLint("NotifyDataSetChanged")
        fun loadItems() {
            val channels = resources?.getStringArray(R.array.update_channel_values)
            val channel = App.preferences.getString("update_channel", channels?.get(0))
            var releases = RepoLoader.instance?.getReleases(module!!.name)
            if (releases == null) releases = module!!.releases
            val tmpList: MutableList<Release>?
            if (channel == channels?.get(0)) {
                tmpList = if (releases != null) releases.parallelStream().filter { t: Release? ->
                    if (Boolean.TRUE == t!!.isPrerelease) return@filter false
                    val name = if (t.name != null) t.name!!
                        .lowercase(LocaleDelegate.defaultLocale) else null
                    !(name != null && name.startsWith("snapshot")) && !(name != null && name.startsWith(
                        "nightly"
                    ))
                }.collect(Collectors.toList()) else null
            } else if (channel == channels?.get(1)) {
                tmpList = if (releases != null) releases.parallelStream().filter { t: Release? ->
                    val name = if (t!!.name != null) t.name!!
                        .lowercase(LocaleDelegate.defaultLocale) else null
                    !(name != null && name.startsWith("snapshot")) && !(name != null && name.startsWith(
                        "nightly"
                    ))
                }.collect(Collectors.toList()) else null
            } else tmpList = releases as MutableList<Release>?
            runOnUiThread(Runnable {
                items = tmpList
                notifyDataSetChanged()
            })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            if (viewType == 0) {
                return ReleaseViewHolder(
                    ItemRepoReleaseBinding.inflate(
                        getLayoutInflater(),
                        parent,
                        false
                    )
                )
            } else {
                return LoadmoreViewHolder(
                    ItemRepoLoadmoreBinding.inflate(
                        getLayoutInflater(),
                        parent,
                        false
                    )
                )
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            if (holder.getItemViewType() == 1) {
                holder.progress!!.setVisibility(View.GONE)
                holder.title!!.setVisibility(View.VISIBLE)
                holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                    if (holder.progress!!.isGone) {
                        holder.title!!.setVisibility(View.GONE)
                        holder.progress!!.show()
                        RepoLoader.instance?.loadRemoteReleases(module!!.name.toString())
                    }
                })
            } else {
                val release = items!!.get(position)
                holder.title!!.setText(release.name)
                val instant = Instant.parse(release.publishedAt)
                val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(App.getLocale(tag)).withZone(ZoneId.systemDefault())
                holder.publishedTime!!.setText(
                    String.format(
                        getString(R.string.module_repo_published_time),
                        formatter.format(instant)
                    )
                )
                renderGithubMarkdown(holder.description!!, release.descriptionHTML)
                holder.openInBrowser!!.setOnClickListener(View.OnClickListener { v: View? ->
                    NavUtil.startURL(
                        requireActivity(),
                        release.url
                    )
                })
                val assets = release.releaseAssets
                if (assets != null && !assets.isEmpty()) {
                    holder.viewAssets!!.setOnClickListener(View.OnClickListener { v: View? ->
                        DownloadDialog.Companion.create(
                            requireActivity(),
                            getParentFragmentManager(),
                            assets
                        )
                    })
                } else {
                    holder.viewAssets!!.setVisibility(View.GONE)
                }
            }
        }

        override fun getItemCount(): Int {
            return items!!.size + (if (module!!.releasesLoaded) 0 else 1)
        }

        override fun getItemViewType(position: Int): Int {
            return if (!module!!.releasesLoaded && position == getItemCount() - 1) 1 else 0
        }

        fun isDataLoaded(): kotlin.Boolean {
            return module!!.releasesLoaded
        }

        open inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var title: TextView? = null
            var publishedTime: TextView? = null
            var description: WebView? = null
            var openInBrowser: MaterialButton? = null
            var viewAssets: MaterialButton? = null
            var progress: CircularProgressIndicator? = null
        }

        inner class ReleaseViewHolder(binding: ItemRepoReleaseBinding) :
            ViewHolder(binding.getRoot()) {
            init {
                title = binding.title
                publishedTime = binding.publishedTime
                description = binding.description
                openInBrowser = binding.openInBrowser
                viewAssets = binding.viewAssets
            }
        }

        inner class LoadmoreViewHolder(binding: ItemRepoLoadmoreBinding) :
            ViewHolder(binding.getRoot()) {
            init {
                title = binding.title
                progress = binding.progress
            }
        }
    }

    private class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            val bundle = Bundle()
            bundle.putInt("position", position)
            val f: Fragment?
            if (position == 0) {
                f = ReadmeFragment()
            } else if (position == 1) {
                f = RecyclerviewFragment()
            } else {
                f = RecyclerviewFragment()
            }
            f.setArguments(bundle)
            return f
        }

        override fun getItemCount(): Int {
            return 3
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) 0 else 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }
    }

    abstract class BorderFragment : BaseFragment() {
        var borderView: BorderView? = null

        fun attachListeners() {
            val parent = getParentFragment()
            if (parent is RepoItemFragment) {
                val repoItemFragment = parent
                borderView!!.getBorderViewDelegate()
                    .setBorderVisibilityChangedListener(OnBorderVisibilityChangedListener { top: kotlin.Boolean, oldTop: kotlin.Boolean, bottom: kotlin.Boolean, oldBottom: kotlin.Boolean ->
                        repoItemFragment.binding!!.appBar.setLifted(!top)
                    })
                repoItemFragment.binding!!.appBar.setLifted(
                    !borderView!!.getBorderViewDelegate().isShowingTopBorder()
                )
                repoItemFragment.binding!!.toolbar.setOnClickListener(View.OnClickListener { v: View? ->
                    repoItemFragment.binding!!.appBar.setExpanded(true, true)
                    scrollToTop()
                })
            }
        }

        abstract fun scrollToTop()

        fun detachListeners() {
            borderView!!.getBorderViewDelegate().setBorderVisibilityChangedListener(null)
        }

        override fun onResume() {
            super.onResume()
            attachListeners()
        }

        override fun onStart() {
            super.onStart()
            attachListeners()
        }

        override fun onStop() {
            super.onStop()
            detachListeners()
        }

        override fun onPause() {
            super.onPause()
            detachListeners()
        }
    }

    class ReadmeFragment : BorderFragment() {
        var binding: ItemRepoReadmeBinding? = null

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val parent = getParentFragment()
            if (parent !is RepoItemFragment) {
                if (!safeNavigate(R.id.action_repo_item_fragment_to_repo_fragment)) {
                    safeNavigate(R.id.repo_nav)
                }
                return null
            }
            val repoItemFragment = parent
            binding = ItemRepoReadmeBinding.inflate(getLayoutInflater(), container, false)
            repoItemFragment.renderGithubMarkdown(
                binding!!.readme,
                repoItemFragment.module!!.readmeHTML
            )
            borderView = binding!!.scrollView
            return binding!!.getRoot()
        }

        override fun scrollToTop() {
            binding!!.scrollView.fullScroll(ScrollView.FOCUS_UP)
        }
    }

    class RecyclerviewFragment : BorderFragment() {
        var binding: ItemRepoRecyclerviewBinding? = null
        var adapter: RecyclerView.Adapter<*>? = null

        override fun scrollToTop() {
            binding!!.recyclerView.smoothScrollToPosition(0)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val arguments = getArguments()
            val parent = getParentFragment()
            if (arguments == null || parent !is RepoItemFragment) {
                if (!safeNavigate(R.id.action_repo_item_fragment_to_repo_fragment)) {
                    safeNavigate(R.id.repo_nav)
                }
                return null
            }
            val repoItemFragment = parent
            val position = arguments.getInt("position", 0)
            if (position == 1) adapter = repoItemFragment.releaseAdapter
            else if (position == 2) adapter = repoItemFragment.informationAdapter
            else return null
            binding = ItemRepoRecyclerviewBinding.inflate(getLayoutInflater(), container, false)
            binding!!.recyclerView.setAdapter(adapter)
            binding!!.recyclerView.setLayoutManager(LinearLayoutManager(requireActivity()))
            binding!!.recyclerView.fixEdgeEffect(false, true)
            borderView = binding!!.recyclerView
            return binding!!.getRoot()
        }
    }
}
