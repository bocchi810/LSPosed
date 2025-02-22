/*
 * This file is part of LSPosed.
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
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.fragment

import android.os.Bundle
import android.view.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.ui.adapters.AppHelper
import org.lsposed.manager.ui.adapters.ScopeAdapter
import org.lsposed.manager.databinding.FragmentAppListBinding
import org.lsposed.manager.util.BackupUtils
import org.lsposed.manager.util.ModuleUtil
import rikka.material.app.LocaleDelegate

class AppListFragment : BaseFragment(), MenuProvider {

    var searchView: SearchView? = null
    private var scopeAdapter: ScopeAdapter? = null
    private var module: ModuleUtil.InstalledModule? = null

    private var searchListener: SearchView.OnQueryTextListener? = null
    var binding: FragmentAppListBinding? = null
    var backupLauncher: ActivityResultLauncher<String>? = null
    var restoreLauncher: ActivityResultLauncher<Array<String>>? = null

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() {
            if (binding != null && scopeAdapter != null) {
                binding!!.swipeRefreshLayout.isRefreshing = !scopeAdapter!!.isLoaded
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentAppListBinding.inflate(layoutInflater, container, false)
        if (module == null) {
            return binding?.root
        }
        binding?.appBar?.setLiftable(true)
        val title = if (module!!.userId != 0) {
            String.format(LocaleDelegate.defaultLocale, "%s (%d)", module!!.appName, module!!.userId)
        } else {
            module!!.appName
        }
        binding?.toolbar?.subtitle = module!!.packageName

        scopeAdapter = ScopeAdapter(this, module!!)
        scopeAdapter?.setHasStableIds(true)
        scopeAdapter?.registerAdapterDataObserver(observer)
        val concatAdapter = ConcatAdapter()
        concatAdapter.addAdapter(scopeAdapter!!.switchAdaptor)
        concatAdapter.addAdapter(scopeAdapter!!)
        binding?.recyclerView?.adapter = concatAdapter
        binding?.recyclerView?.setHasFixedSize(true)
        binding?.recyclerView?.layoutManager = LinearLayoutManager(requireActivity())
        binding?.recyclerView?.borderViewDelegate?.setBorderVisibilityChangedListener { top, _, _, _ ->
            binding?.appBar?.isLifted = !top
        }
        binding?.recyclerView?.overScrollMode = View.OVER_SCROLL_NEVER // 禁用过度滚动效果
        binding?.recyclerView?.edgeEffectFactory = RecyclerView.EdgeEffectFactory() // 使用默认的边缘效果
        binding?.swipeRefreshLayout?.setOnRefreshListener { scopeAdapter?.refresh(true) }
        binding?.swipeRefreshLayout?.setProgressViewEndTarget(true, binding?.swipeRefreshLayout?.progressViewEndOffset!!)
        val intent = AppHelper.getSettingsIntent(module!!.packageName, module!!.userId)
        if (intent == null) {
            binding?.fab?.visibility = View.GONE
        } else {
            binding?.fab?.visibility = View.VISIBLE
            binding?.fab?.setOnClickListener {
                ConfigManager.startActivityAsUserWithFeature(intent, module!!.userId)
            }
        }

        setupToolbar(binding?.toolbar!!, binding?.clickView!!, title, R.menu.menu_app_list) {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        val l = View.OnClickListener {
            if (searchView?.isIconified == true) {
                binding?.recyclerView?.smoothScrollToPosition(0)
                binding?.appBar?.setExpanded(true, true)
            }
        }
        binding?.toolbar?.setOnClickListener(l)
        binding?.clickView?.setOnClickListener(l)

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (module == null) {
            if (!safeNavigate(R.id.action_app_list_fragment_to_modules_fragment)) {
                safeNavigate(R.id.modules_nav)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = AppListFragmentArgs.fromBundle(requireArguments())
        val modulePackageName = args.modulePackageName
        val moduleUserId = args.moduleUserId

        module = ModuleUtil.instance?.getModule(modulePackageName, moduleUserId)
        if (module == null) {
            if (!safeNavigate(R.id.action_app_list_fragment_to_modules_fragment)) {
                safeNavigate(R.id.modules_nav)
            }
        }

        backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
            if (uri == null) return@registerForActivityResult
            runAsync {
                try {
                    BackupUtils.backup(uri, modulePackageName)
                } catch (e: Exception) {
                    val text = App.instance?.getString(R.string.settings_backup_failed2, e.message)
                    showHint(text.toString(), false)
                }
            }
        }
        restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runAsync {
                try {
                    BackupUtils.restore(uri, modulePackageName)
                } catch (e: Exception) {
                    val text = App.instance?.getString(R.string.settings_restore_failed2, e.message)
                    showHint(text.toString(), false)
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                scopeAdapter?.onBackPressed()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        scopeAdapter?.refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scopeAdapter?.unregisterAdapterDataObserver(observer)
        binding = null
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return onOptionsItemSelected(menuItem) ?: false
    }

    override fun onPrepareMenu(menu: Menu) {
        searchView = menu.findItem(R.id.menu_search).actionView as SearchView
        searchView?.setOnQueryTextListener(searchListener)
        searchView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(arg0: View) {
                binding?.appBar?.setExpanded(false, true)
                binding?.recyclerView?.isNestedScrollingEnabled = false
            }

            override fun onViewDetachedFromWindow(v: View) {
                binding?.recyclerView?.isNestedScrollingEnabled = true
            }
        })
        searchView?.findViewById<View>(androidx.appcompat.R.id.search_edit_frame)?.layoutDirection = View.LAYOUT_DIRECTION_INHERIT
        onPrepareOptionsMenu(menu)
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (onContextItemSelected(item) == true) {
            return true
        }
        return super.onContextItemSelected(item)
    }
}