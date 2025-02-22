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
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.SparseArray
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.View.OnCreateContextMenuListener
import android.view.View.OnLayoutChangeListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.util.Pair
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.tabs.TabLayoutMediator.TabConfigurationStrategy
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.ui.adapters.AppHelper
import org.lsposed.manager.databinding.FragmentPagerBinding
import org.lsposed.manager.databinding.ItemModuleBinding
import org.lsposed.manager.databinding.SwiperefreshRecyclerviewBinding
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.RepoLoader.RepoListener
import org.lsposed.manager.ui.dialog.BlurBehindDialogBuilder
import org.lsposed.manager.ui.fragment.CompileDialogFragment.Companion.speed
import org.lsposed.manager.ui.widget.EmptyStateRecyclerView.EmptyStateAdapter
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule
import org.lsposed.manager.util.ModuleUtil.ModuleListener
import rikka.core.util.ResourceUtils
import rikka.material.app.LocaleDelegate
import rikka.recyclerview.fixEdgeEffect
import rikka.widget.borderview.BorderView.OnBorderVisibilityChangedListener
import java.lang.String.format
import java.util.Locale
import java.util.function.Consumer
import java.util.function.IntUnaryOperator
import java.util.stream.IntStream
import androidx.core.util.size
import com.bumptech.glide.Glide

class ModulesFragment : BaseFragment(), ModuleListener, RepoListener, MenuProvider {
    protected var binding: FragmentPagerBinding? = null
    protected var searchView: SearchView? = null
    private var searchListener: SearchView.OnQueryTextListener? = null

    var adapters: SparseArray<ModuleAdapter> = SparseArray<ModuleAdapter>()
    var pagerAdapter: PagerAdapter? = null

    private var selectedModule: InstalledModule? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchListener = object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                forEachAdaptor(Consumer { adapter: ModuleAdapter? ->
                    adapter!!.getFilter()?.filter(query)
                })
                return false
            }

            override fun onQueryTextChange(query: String?): Boolean {
                forEachAdaptor(Consumer { adapter: ModuleAdapter? ->
                    adapter!!.getFilter()?.filter(query)
                })
                return false
            }
        }
    }

    private fun forEachAdaptor(action: Consumer<in ModuleAdapter?>) {
        val snapshot = adapters
        for (i in 0..<snapshot.size) {
            action.accept(snapshot.valueAt(i))
        }
    }

    private fun showFab() {
        val layoutParams = binding!!.fab.getLayoutParams()
        if (layoutParams is CoordinatorLayout.LayoutParams) {
            val coordinatorLayoutBehavior =
                layoutParams.getBehavior()
            if (coordinatorLayoutBehavior is HideBottomViewOnScrollBehavior<*>) {
                (coordinatorLayoutBehavior as HideBottomViewOnScrollBehavior<FloatingActionButton?>).slideUp(
                    binding!!.fab
                )
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPagerBinding.inflate(inflater, container, false)
        binding!!.appBar.setLiftable(true)
        setupToolbar(binding!!.toolbar, binding!!.clickView, R.string.Modules, R.menu.menu_modules)
        binding!!.toolbar.setNavigationIcon(null)

        // Pass 'this' (the outer class instance) to the PagerAdapter constructor
        pagerAdapter = PagerAdapter(this)

        binding!!.viewPager.setAdapter(pagerAdapter)
        binding!!.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                showFab()
            }
        })

        TabLayoutMediator(
            binding!!.tabLayout,
            binding!!.viewPager,
            TabConfigurationStrategy { tab: TabLayout.Tab?, position: Int ->
                if (position < adapters.size) {
                    tab!!.setText(adapters.valueAt(position).user.name)
                }
            }).attach()

        binding!!.tabLayout.addOnLayoutChangeListener(OnLayoutChangeListener { view: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int ->
            val vg = binding!!.tabLayout.getChildAt(0) as ViewGroup
            val tabLayoutWidth = IntStream.range(0, binding!!.tabLayout.getTabCount()).map(
                IntUnaryOperator { i: Int -> vg.getChildAt(i).getWidth() }).sum()
            if (tabLayoutWidth <= binding!!.getRoot().getWidth()) {
                binding!!.tabLayout.setTabMode(TabLayout.MODE_FIXED)
                binding!!.tabLayout.setTabGravity(TabLayout.GRAVITY_FILL)
            }
        })

        binding!!.fab.setOnClickListener(View.OnClickListener { v: View? ->
            val bundle = Bundle()
            val user = adapters.valueAt(binding!!.viewPager.getCurrentItem()).user
            bundle.putParcelable("userInfo", user)
            val f = RecyclerViewDialogFragment()
            f.setArguments(bundle)
            f.show(getChildFragmentManager(), "install_to_user" + user.id)
        })

        moduleUtil?.addListener(this)
        repoLoader?.addListener(this)
        onModulesReloaded()

        return binding!!.getRoot()
    }

    override fun onPrepareMenu(menu: Menu) {
        searchView = menu.findItem(R.id.menu_search).getActionView() as SearchView?
        if (searchView != null) {
            searchView!!.setOnQueryTextListener(searchListener)
            searchView!!.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(arg0: View) {
                    binding!!.appBar.setExpanded(false, true)
                }

                override fun onViewDetachedFromWindow(v: View) {
                }
            })
            searchView!!.findViewById<View?>(androidx.appcompat.R.id.search_edit_frame)
                .setLayoutDirection(
                    View.LAYOUT_DIRECTION_INHERIT
                )
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return false
    }

    override fun onResume() {
        super.onResume()
        forEachAdaptor(Consumer { obj: ModuleAdapter? -> obj!!.refresh() })
    }

    override fun onSingleModuleReloaded(module: InstalledModule?) {
        forEachAdaptor(Consumer { obj: ModuleAdapter? -> obj!!.refresh() })
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onModulesReloaded() {
        val users: MutableList<UserInfo>? = moduleUtil?.getUsers() as MutableList<UserInfo>?
        if (users == null) return

        if (users.size != 1) {
            binding!!.viewPager.setUserInputEnabled(true)
            binding!!.tabLayout.setVisibility(View.VISIBLE)
            binding!!.fab.show()
        } else {
            binding!!.viewPager.setUserInputEnabled(false)
            binding!!.tabLayout.setVisibility(View.GONE)
        }

        val tmp = SparseArray<ModuleAdapter>(users.size)
        val snapshot = adapters
        for (user in users) {
            if (snapshot.indexOfKey(user.id) >= 0) {
                tmp.put(user.id, snapshot.get(user.id))
            } else {
                val adapter = ModuleAdapter(user)
                adapter.setHasStableIds(true)
                tmp.put(user.id, adapter)
            }
        }
        adapters = tmp
        forEachAdaptor(Consumer { obj: ModuleAdapter? -> obj!!.refresh() })
        runOnUiThread(Runnable { pagerAdapter!!.notifyDataSetChanged() })
        updateModuleSummary()
    }

    override fun onRepoLoaded() {
        forEachAdaptor(Consumer { obj: ModuleAdapter? -> obj!!.refresh() })
    }

    private fun updateModuleSummary() {
        val moduleCount: Int? = moduleUtil?.enabledModulesCount
        runOnUiThread(Runnable {
            if (binding != null) {
                binding!!.toolbar.setSubtitle(
                    if (moduleCount == -1) getString(R.string.loading) else moduleCount?.let {
                        getResources().getQuantityString(
                            R.plurals.modules_enabled_count,
                            it,
                            moduleCount
                        )
                    }
                )
                binding!!.toolbarLayout.setSubtitle(binding!!.toolbar.getSubtitle())
            }
        })
    }

    fun installModuleToUser(module: InstalledModule, user: UserInfo) {
        BlurBehindDialogBuilder(
            requireActivity(),
            R.style.ThemeOverlay_MaterialAlertDialog_Centered_FullWidthButtons
        )
            .setTitle(getString(R.string.install_to_user, user.name))
            .setMessage(getString(R.string.install_to_user_message, module.appName, user.name))
            .setPositiveButton(
                android.R.string.ok,
                DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                    runAsync(
                        Runnable {
                            val success = ConfigManager.installExistingPackageAsUser(
                                module.packageName,
                                user.id
                            )
                            val text = if (success) getString(
                                R.string.module_installed,
                                module.appName,
                                user.name
                            ) else getString(R.string.module_install_failed)
                            showHint(text, false)
                            if (success) moduleUtil?.reloadSingleModule(module.packageName, user.id)
                        })
                })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("WrongConstant")
    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (selectedModule == null) {
            return false
        }
        val itemId = item.getItemId()
        if (itemId == R.id.menu_launch) {
            val packageName = selectedModule!!.packageName
            if (packageName == null) {
                return false
            }
            val intent = AppHelper.getSettingsIntent(packageName, selectedModule!!.userId)
            if (intent != null) {
                ConfigManager.startActivityAsUserWithFeature(intent, selectedModule!!.userId)
            }
            return true
        } else if (itemId == R.id.menu_other_app) {
            val intent = Intent(Intent.ACTION_SHOW_APP_INFO)
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, selectedModule!!.packageName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ConfigManager.startActivityAsUserWithFeature(intent, selectedModule!!.userId)
            return true
        } else if (itemId == R.id.menu_app_info) {
            ConfigManager.startActivityAsUserWithFeature(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", selectedModule!!.packageName, null)
                ), selectedModule!!.userId
            )
            return true
        } else if (itemId == R.id.menu_uninstall) {
            BlurBehindDialogBuilder(
                requireActivity(),
                R.style.ThemeOverlay_MaterialAlertDialog_FullWidthButtons
            )
                .setIcon(selectedModule!!.app?.loadIcon(pm))
                .setTitle(selectedModule!!.appName)
                .setMessage(R.string.module_uninstall_message)
                .setPositiveButton(
                    android.R.string.ok,
                    DialogInterface.OnClickListener { dialog: DialogInterface?, which: Int ->
                        runAsync(
                            Runnable {
                                val success = ConfigManager.uninstallPackage(
                                    selectedModule!!.packageName,
                                    selectedModule!!.userId
                                )
                                val text = if (success) getString(
                                    R.string.module_uninstalled,
                                    selectedModule!!.appName
                                ) else getString(R.string.module_uninstall_failed)
                                showHint(text, false)
                                if (success) moduleUtil?.reloadSingleModule(
                                    selectedModule!!.packageName,
                                    selectedModule!!.userId
                                )
                            })
                    })
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return true
        } else if (itemId == R.id.menu_repo) {
            val navController = navController
            navController.navigate(
                Uri.Builder().scheme("lsposed").authority("repo")
                    .appendQueryParameter("modulePackageName", selectedModule!!.packageName)
                    .build(),
                NavOptions.Builder().setEnterAnim(R.anim.fragment_enter)
                    .setExitAnim(R.anim.fragment_exit).setPopEnterAnim(R.anim.fragment_enter_pop)
                    .setPopExitAnim(R.anim.fragment_exit_pop).setLaunchSingleTop(true)
                    .setPopUpTo(navController.graph.startDestinationId, false, true).build()
            )
            return true
        } else if (itemId == R.id.menu_compile_speed) {
            speed(getChildFragmentManager(), selectedModule!!.packageInfo.applicationInfo)
        }
        return super.onContextItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        moduleUtil?.removeListener(this)
        repoLoader?.removeListener(this)
        binding = null
    }

    class ModuleListFragment : Fragment() {
        var binding: SwiperefreshRecyclerviewBinding? = null
        private var adapter: ModuleAdapter? = null
        private val observer: AdapterDataObserver = object : AdapterDataObserver() {
            override fun onChanged() {
                binding!!.swipeRefreshLayout.setRefreshing(!adapter!!.isDataLoaded())
            }
        }

        private val searchViewLocker: OnAttachStateChangeListener =
            object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    binding!!.recyclerView.setNestedScrollingEnabled(false)
                }

                override fun onViewDetachedFromWindow(v: View) {
                    binding!!.recyclerView.setNestedScrollingEnabled(true)
                }
            }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val fragment = getParentFragment() as ModulesFragment?
            val arguments = getArguments()
            if (fragment == null || arguments == null) {
                return null
            }
            val userId = arguments.getInt("user_id")
            binding = SwiperefreshRecyclerviewBinding.inflate(getLayoutInflater(), container, false)
            adapter = fragment.adapters.get(userId)
            binding!!.recyclerView.setAdapter(adapter)
            binding!!.recyclerView.setLayoutManager(LinearLayoutManager(requireActivity()))
            binding!!.swipeRefreshLayout.setOnRefreshListener(OnRefreshListener { adapter!!.fullRefresh() })
            binding!!.swipeRefreshLayout.setProgressViewEndTarget(
                true,
                binding!!.swipeRefreshLayout.getProgressViewEndOffset()
            )
            binding!!.recyclerView.fixEdgeEffect(false, true)
            adapter!!.registerAdapterDataObserver(observer)
            return binding!!.getRoot()
        }

        fun attachListeners() {
            val parent = getParentFragment()
            if (parent is ModulesFragment) {
                binding!!.recyclerView.getBorderViewDelegate()
                    .setBorderVisibilityChangedListener(OnBorderVisibilityChangedListener { top: Boolean, oldTop: Boolean, bottom: Boolean, oldBottom: Boolean ->
                        parent.binding!!.appBar.setLifted(!top)
                    })
                parent.binding!!.appBar.setLifted(
                    !binding!!.recyclerView.getBorderViewDelegate().isShowingTopBorder()
                )
                parent.searchView!!.addOnAttachStateChangeListener(searchViewLocker)
                binding!!.recyclerView.setNestedScrollingEnabled(parent.searchView!!.isIconified())
                val l = View.OnClickListener { v: View? ->
                    if (parent.searchView!!.isIconified()) {
                        binding!!.recyclerView.smoothScrollToPosition(0)
                        parent.binding!!.appBar.setExpanded(true, true)
                    }
                }
                parent.binding!!.clickView.setOnClickListener(l)
                parent.binding!!.toolbar.setOnClickListener(l)
            }
        }

        fun detachListeners() {
            binding!!.recyclerView.getBorderViewDelegate().setBorderVisibilityChangedListener(null)
            val parent = getParentFragment()
            if (parent is ModulesFragment) {
                parent.searchView!!.removeOnAttachStateChangeListener(searchViewLocker)
                binding!!.recyclerView.setNestedScrollingEnabled(true)
            }
        }

        override fun onStart() {
            super.onStart()
            attachListeners()
        }

        override fun onResume() {
            super.onResume()
            attachListeners()
        }

        override fun onDestroyView() {
            adapter!!.unregisterAdapterDataObserver(observer)
            super.onDestroyView()
        }

        override fun onPause() {
            super.onPause()
            detachListeners()
        }

        override fun onStop() {
            super.onStop()
            detachListeners()
        }
    }

    inner class PagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            val bundle = Bundle()
            bundle.putInt("user_id", adapters.keyAt(position))
            val fragment: Fragment = ModuleListFragment()
            fragment.setArguments(bundle)
            return fragment
        }

        override fun getItemCount(): Int {
            return adapters.size
        }

        override fun getItemId(position: Int): Long {
            return adapters.keyAt(position).toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return adapters.indexOfKey(itemId.toInt()) >= 0
        }
    }

    fun createPickModuleAdapter(userInfo: UserInfo): ModuleAdapter {
        return ModuleAdapter(userInfo, true)
    }

    inner class ModuleAdapter @JvmOverloads constructor(
        val user: UserInfo,
        val isPick: Boolean = false
    ) : EmptyStateAdapter<ModuleAdapter.ViewHolder?>(), Filterable {
        private var searchList: MutableList<InstalledModule> = ArrayList<InstalledModule>()
        private var showList: MutableList<InstalledModule>? = ArrayList<InstalledModule>()
        override var isLoaded = false
        private var onPickListener: View.OnClickListener? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemModuleBinding.inflate(getLayoutInflater(), parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            super.onBindViewHolder(holder, position)
            val item = showList!!.get(position)
            val appName: String?
            if (item.userId != 0) {
                appName = format(
                    LocaleDelegate.defaultLocale,
                    "%s (%d)",
                    item.appName,
                    item.userId
                )
            } else {
                appName = item.appName
            }
            holder.appName.setText(appName)
            Glide.with(holder.appIcon)
                .load(item.packageName)
                .into(object : CustomTarget<Drawable?>() {
                    override fun onResourceReady(
                        resource: Drawable,
                        transition: Transition<in Drawable?>?
                    ) {
                        holder.appIcon.setImageDrawable(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                    }
                })
            var sb = SpannableStringBuilder()
            if (item.description?.isEmpty() == true) {
                sb.append(item.description)
            } else {
                sb.append(getString(R.string.module_empty_description))
            }
            holder.appDescription.setText(sb)
            holder.appDescription.setVisibility(View.VISIBLE)
            sb = SpannableStringBuilder()

            val installXposedVersion = ConfigManager.xposedApiVersion
            var warningText: String? = null
            if (item.minVersion == 0) {
                warningText = getString(R.string.no_min_version_specified)
            } else if (installXposedVersion > 0 && item.minVersion > installXposedVersion) {
                warningText = getString(R.string.warning_xposed_min_version, item.minVersion)
            } else if (item.targetVersion > installXposedVersion) {
                warningText = getString(R.string.warning_target_version_higher, item.targetVersion)
            } else if (item.minVersion < ModuleUtil.MIN_MODULE_VERSION) {
                warningText = getString(
                    R.string.warning_min_version_too_low,
                    item.minVersion,
                    ModuleUtil.MIN_MODULE_VERSION
                )
            } else if (item.isInstalledOnExternalStorage) {
                warningText = getString(R.string.warning_installed_on_external_storage)
            }
            if (warningText != null) {
                sb.append(warningText)
                val foregroundColorSpan = ForegroundColorSpan(
                    ResourceUtils.resolveColor(
                        requireActivity().getTheme(),
                        com.google.android.material.R.attr.colorError
                    )
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val typefaceSpan =
                        TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                    sb.setSpan(
                        typefaceSpan,
                        sb.length - warningText.length,
                        sb.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                } else {
                    val styleSpan = StyleSpan(Typeface.BOLD)
                    sb.setSpan(
                        styleSpan,
                        sb.length - warningText.length,
                        sb.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
                sb.setSpan(
                    foregroundColorSpan,
                    sb.length - warningText.length,
                    sb.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
            val ver: RepoLoader.ModuleVersion? = repoLoader?.getModuleLatestVersion(item.packageName)
            if (ver != null && ver.upgradable(item.versionCode, item.versionName.toString())) {
                if (warningText != null) sb.append("\n")
                val recommended = getString(R.string.update_available, ver.versionName)
                sb.append(recommended)
                val foregroundColorSpan = ForegroundColorSpan(
                    ResourceUtils.resolveColor(
                        requireActivity().getTheme(),
                        androidx.appcompat.R.attr.colorPrimary
                    )
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val typefaceSpan =
                        TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                    sb.setSpan(
                        typefaceSpan,
                        sb.length - recommended.length,
                        sb.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                } else {
                    val styleSpan = StyleSpan(Typeface.BOLD)
                    sb.setSpan(
                        styleSpan,
                        sb.length - recommended.length,
                        sb.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                }
                sb.setSpan(
                    foregroundColorSpan,
                    sb.length - recommended.length,
                    sb.length,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
            if (sb.length == 0) {
                holder.hint.setVisibility(View.GONE)
            } else {
                holder.hint.setVisibility(View.VISIBLE)
                holder.hint.setText(sb)
            }

            if (!isPick) {
                holder.root.setAlpha(if (moduleUtil?.isModuleEnabled(item.packageName) == true) 1.0f else .5f)
                holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                    searchView!!.clearFocus()
                    if (isDataLoaded()) {
                        safeNavigate(
                            ModulesFragmentDirections.actionModulesFragmentToAppListFragment(
                                item.packageName,
                                item.userId
                            )
                        )
                    }
                })
                holder.itemView.setOnLongClickListener(OnLongClickListener { v: View? ->
                    searchView!!.clearFocus()
                    selectedModule = item
                    false
                })
                holder.itemView.setOnCreateContextMenuListener(OnCreateContextMenuListener { menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo? ->
                    requireActivity().getMenuInflater().inflate(R.menu.context_menu_modules, menu)
                    menu!!.setHeaderTitle(item.appName)
                    val intent = AppHelper.getSettingsIntent(item.packageName, item.userId)
                    if (intent == null) {
                        menu.removeItem(R.id.menu_launch)
                    }
                    if (repoLoader?.getOnlineModule(item.packageName) == null) {
                        menu.removeItem(R.id.menu_repo)
                    }
                    if (item.userId == 0) {
                        val users = ConfigManager.users
                        if (users != null) {
                            for (user in users) {
                                user?.id?.let {
                                    if (moduleUtil?.getModule(item.packageName, it) == null) {
                                        user.id.let {
                                            menu.add(
                                                1,
                                                it,
                                                0,
                                                getString(R.string.install_to_user, user.name)
                                            )
                                        }?.setOnMenuItemClickListener(
                                            MenuItem.OnMenuItemClickListener { i: MenuItem? ->
                                                installModuleToUser(selectedModule!!, user)
                                                true
                                            })
                                    }
                                }
                            }
                        }
                    }
                })
                holder.appVersion.setVisibility(View.VISIBLE)
                holder.appVersion.setText(item.versionName)
                holder.appVersion.setSelected(true)
            } else {
                holder.itemView.setTag(item)
                holder.itemView.setOnClickListener(View.OnClickListener { v: View? ->
                    if (onPickListener != null) onPickListener!!.onClick(v)
                })
            }
        }

        override fun onViewRecycled(holder: ViewHolder) {
            holder.itemView.setTag(null)
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int {
            return showList!!.size
        }

        override fun getItemId(position: Int): Long {
            val module = showList!!.get(position)
            return (module.packageName + "!" + module.userId).hashCode().toLong()
        }

        override fun getFilter(): Filter? {
            return ApplicationFilter() // Create an instance of ApplicationFilter
        }

        fun setOnPickListener(onPickListener: View.OnClickListener?) {
            this.onPickListener = onPickListener
        }

        fun refresh() {
            runAsync(reloadModules)
        }

        fun fullRefresh() {
            runAsync(Runnable {
                setLoaded(null, false)
                moduleUtil?.reloadInstalledModules()
                refresh()
            })
        }

        private val reloadModules = Runnable {
            val modules: MutableMap<Pair<String?, Int?>?, InstalledModule?>? =
                moduleUtil?.modules as MutableMap<Pair<String?, Int?>?, InstalledModule?>?
            if (modules == null) return@Runnable
            val cmp = AppHelper.getAppListComparator(0, pm)
            setLoaded(null, false)
            val tmpList = ArrayList<InstalledModule>()
            modules.values.parallelStream()
                .sorted { a: InstalledModule?, b: InstalledModule? ->
                    val aChecked: Boolean = moduleUtil?.isModuleEnabled(
                        a!!.packageName
                    ) == true
                    val bChecked: Boolean = moduleUtil?.isModuleEnabled(b!!.packageName) == true
                    if (aChecked == bChecked) {
                        val c = cmp?.compare(a?.packageInfo, b?.packageInfo)
                        if (c == 0) {
                            if (a?.userId == this.user.id) return@sorted -1
                            if (b?.userId == this.user.id) return@sorted 1
                            else return@sorted Integer.compare(a?.userId!!, b?.userId!!)
                        }
                        return@sorted c!!
                    } else if (aChecked) {
                        return@sorted -1
                    } else {
                        return@sorted 1
                    }
                }.forEachOrdered(object : Consumer<InstalledModule?> {
                    private val uniquer = HashSet<String?>()

                    override fun accept(module: InstalledModule?) {
                        if (isPick) {
                            if (!uniquer.contains(module?.packageName)) {
                                uniquer.add(module?.packageName)
                                if (module?.userId != user.id) module?.let { tmpList.add(it) }
                            }
                        } else if (module?.userId == user.id) {
                            tmpList.add(module)
                        }
                    }
                })
            val queryStr = if (searchView != null) searchView!!.getQuery().toString() else ""
            searchList = tmpList
            runOnUiThread(Runnable { getFilter()?.filter(queryStr) })
        }

        @SuppressLint("NotifyDataSetChanged")
        private fun setLoaded(list: MutableList<InstalledModule>?, loaded: Boolean) {
            runOnUiThread(Runnable {
                if (list != null) showList = list
                isLoaded = loaded
                notifyDataSetChanged()
            })
        }

        fun isDataLoaded(): Boolean {
            return isLoaded && moduleUtil?.isModulesLoaded == true
        }

        inner class ViewHolder(binding: ItemModuleBinding) :
            RecyclerView.ViewHolder(binding.getRoot()) {
            var root: ConstraintLayout
            var appIcon: ImageView
            var appName: TextView
            var appDescription: TextView
            var appVersion: TextView
            var hint: TextView
            var checkBox: MaterialCheckBox?

            init {
                root = binding.itemRoot
                appIcon = binding.appIcon
                appName = binding.appName
                appDescription = binding.description
                appVersion = binding.versionName
                hint = binding.hint
                checkBox = binding.checkbox
            }
        }

        internal inner class ApplicationFilter : Filter() {
            private fun lowercaseContains(s: String?, filter: String): Boolean {
                return !TextUtils.isEmpty(s) && s!!.lowercase(Locale.getDefault()).contains(filter)
            }

            override fun performFiltering(constraint: CharSequence): FilterResults {
                val filterResults = FilterResults()
                val filtered: MutableList<InstalledModule?> = ArrayList<InstalledModule?>()
                val filter = constraint.toString().lowercase(Locale.getDefault())
                for (info in searchList) {
                    if (lowercaseContains(info.appName, filter) ||
                        lowercaseContains(info.packageName, filter) ||
                        lowercaseContains(info.description, filter)
                    ) {
                        filtered.add(info)
                    }
                }
                filterResults.values = filtered
                filterResults.count = filtered.size
                return filterResults
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults) {
                setLoaded(results.values as MutableList<InstalledModule>?, true)
            }
        }
    }

    companion object {
        private val pm: PackageManager? = App.instance?.getPackageManager()
        private val moduleUtil: ModuleUtil? = ModuleUtil.instance
        private val repoLoader: RepoLoader? = RepoLoader.instance
    }
}
