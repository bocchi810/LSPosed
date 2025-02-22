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

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.HorizontalScrollView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textview.MaterialTextView
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.databinding.FragmentPagerBinding
import org.lsposed.manager.databinding.ItemLogTextviewBinding
import org.lsposed.manager.databinding.SwiperefreshRecyclerviewBinding
import org.lsposed.manager.receivers.LSPManagerServiceHolder
import org.lsposed.manager.ui.widget.EmptyStateRecyclerView
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.util.*
import java.util.stream.Collectors
import androidx.core.content.edit

class LogsFragment : BaseFragment(), MenuProvider {
    private var binding: FragmentPagerBinding? = null
    private var adapter: LogPageAdapter? = null
    private var wordWrap: MenuItem? = null
    private var optionsItemSelectListener: OptionsItemSelectListener? = null

    interface OptionsItemSelectListener {
        fun onOptionsItemSelected(item: MenuItem): Boolean
    }

    private val saveLogsLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runAsync {
            val context = requireContext()
            val cr = context.contentResolver
            try {
                cr.openFileDescriptor(uri, "wt")?.use { zipFd ->
                    showHint(context.getString(R.string.logs_saving), false)
                    LSPManagerServiceHolder.service?.getLogs(zipFd)
                    showHint(context.getString(R.string.logs_saved), true)
                }
            } catch (e: Throwable) {
                val cause = e.cause
                val message = cause?.message ?: e.message
                val text = context.getString(R.string.logs_save_failed2, message)
                showHint(text, false)
                Log.w(App.TAG, "save log", e)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPagerBinding.inflate(inflater, container, false)
        binding?.appBar?.setLiftable(true)
        setupToolbar(binding?.toolbar!!, binding?.clickView!!, R.string.Logs, R.menu.menu_logs)
        binding?.toolbar?.setNavigationIcon(null)
        binding?.toolbar?.subtitle = if (ConfigManager.isVerboseLogEnabled) {
            getString(R.string.enabled_verbose_log) // 将资源 ID 转换为字符串
        } else {
            getString(R.string.disabled_verbose_log) // 将资源 ID 转换为字符串
        }

        adapter = LogPageAdapter(this)
        binding?.viewPager?.adapter = adapter
        TabLayoutMediator(binding?.tabLayout!!, binding?.viewPager!!) { tab, position ->
            tab.setText(getString(adapter?.getItemId(position)?.toInt() ?: 0))
        }.attach()

        binding?.tabLayout?.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val vg = binding?.tabLayout?.getChildAt(0) as? ViewGroup
            val tabLayoutWidth = vg?.let {
                binding?.tabLayout?.tabCount?.let { it1 -> (0 until it1).sumOf { i -> it.getChildAt(i).width } }
            } ?: 0
            binding?.root?.width?.let {
                if (tabLayoutWidth <= it) {
                    binding?.tabLayout?.tabMode = TabLayout.MODE_FIXED
                    binding?.tabLayout?.tabGravity = TabLayout.GRAVITY_FILL
                }
            }
        }

        return binding?.root
    }

    fun setOptionsItemSelectListener(listener: OptionsItemSelectListener) {
        this.optionsItemSelectListener = listener
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_save -> {
                save()
                true
            }
            R.id.menu_word_wrap -> {
                item.isChecked = !item.isChecked
                App.preferences.edit { putBoolean("enable_word_wrap", item.isChecked) }
                binding?.viewPager?.isUserInputEnabled = item.isChecked
                adapter?.refresh()
                true
            }
            else -> optionsItemSelectListener?.onOptionsItemSelected(item) ?: false
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        wordWrap = menu.findItem(R.id.menu_word_wrap)
        wordWrap?.isChecked = App.preferences.getBoolean("enable_word_wrap", false)
        binding?.viewPager?.isUserInputEnabled = wordWrap?.isChecked ?: false
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun save() {
        val now = LocalDateTime.now()
        val filename = String.format(Locale.getDefault(), "LSPosed_%s.zip", now.toString())
        try {
            saveLogsLauncher.launch(filename)
        } catch (e: ActivityNotFoundException) {
            showHint(R.string.enable_documentui, true)
        }
    }

    open class LogFragment : BaseFragment() {
        companion object {
            const val SCROLL_THRESHOLD = 500
        }

        protected var verbose = false
        protected var binding: SwiperefreshRecyclerviewBinding? = null
        protected var adaptor: LogAdaptor? = null
        protected var layoutManager: LinearLayoutManager? = null

        open inner class LogAdaptor : EmptyStateRecyclerView.EmptyStateAdapter<LogAdaptor.ViewHolder>() {
            private var log: List<CharSequence> = emptyList()
            override var isLoaded = false

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                return ViewHolder(ItemLogTextviewBinding.inflate(layoutInflater, parent, false))
            }

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                holder.item.text = log[position]
            }

            override fun getItemCount(): Int {
                return log.size
            }

            @SuppressLint("NotifyDataSetChanged")
            fun refresh(log: List<CharSequence>) {
                runOnUiThread {
                    isLoaded = true
                    this.log = log
                    notifyDataSetChanged()
                }
            }

            fun fullRefresh() {
                runAsync {
                    isLoaded = false
                    val tmp = try {
                        ConfigManager.getLog(verbose)?.use { parcelFileDescriptor ->
                            BufferedReader(InputStreamReader(FileInputStream(parcelFileDescriptor.fileDescriptor))).use { br ->
                                br.lines().parallel().collect(Collectors.toList())
                            }
                        } ?: emptyList()
                    } catch (e: Throwable) {
                        Log.getStackTraceString(e).split("\n")
                    }
                    refresh(tmp)
                }
            }

            fun isDataLoaded(): Boolean {
                return isLoaded
            }

            inner class ViewHolder(binding: ItemLogTextviewBinding) : RecyclerView.ViewHolder(binding.root) {
                val item: MaterialTextView = binding.logItem
            }
        }

        protected open fun createAdaptor(): LogAdaptor {
            return LogAdaptor()
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            binding = SwiperefreshRecyclerviewBinding.inflate(layoutInflater, container, false)
            verbose = arguments?.getBoolean("verbose") ?: false
            adaptor = createAdaptor()
            binding?.recyclerView?.adapter = adaptor
            layoutManager = LinearLayoutManager(requireActivity())
            binding?.recyclerView?.layoutManager = layoutManager
            binding?.recyclerView?.layoutDirection = View.LAYOUT_DIRECTION_LTR // LTR even for RTL languages
            binding?.swipeRefreshLayout?.progressViewEndOffset?.let { binding?.swipeRefreshLayout?.setProgressViewEndTarget(true, it) }
            binding?.swipeRefreshLayout?.setOnRefreshListener { adaptor?.fullRefresh() }
            adaptor?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    binding?.swipeRefreshLayout?.isRefreshing = adaptor?.isDataLoaded() == false
                }
            })
            adaptor?.fullRefresh()
            return binding?.root
        }

        fun scrollToTop(logsFragment: LogsFragment) {
            logsFragment.binding?.appBar?.setExpanded(true, true)
            if ((layoutManager?.findFirstVisibleItemPosition() ?: 0) > SCROLL_THRESHOLD) {
                binding?.recyclerView?.scrollToPosition(0)
            } else {
                binding?.recyclerView?.smoothScrollToPosition(0)
            }
        }

        fun scrollToBottom(logsFragment: LogsFragment) {
            logsFragment.binding?.appBar?.setExpanded(false, true)
            val end = (adaptor?.itemCount ?: 1) - 1
            if ((adaptor?.itemCount ?: 0) - (layoutManager?.findLastVisibleItemPosition() ?: 0) > SCROLL_THRESHOLD) {
                binding?.recyclerView?.scrollToPosition(end)
            } else {
                binding?.recyclerView?.smoothScrollToPosition(end)
            }
        }

        fun attachListeners() {
            val parent = parentFragment
            if (parent is LogsFragment) {
                parent.binding?.appBar?.isLifted = binding?.recyclerView?.borderViewDelegate?.isShowingTopBorder() == false
                binding?.recyclerView?.borderViewDelegate?.setBorderVisibilityChangedListener { top, _, _, _ ->
                    parent.binding?.appBar?.isLifted = !top
                }
                parent.setOptionsItemSelectListener(object : LogsFragment.OptionsItemSelectListener {
                    override fun onOptionsItemSelected(item: MenuItem): Boolean {
                        return when (item.itemId) {
                            R.id.menu_scroll_top -> {
                                scrollToTop(parent)
                                true
                            }
                            R.id.menu_scroll_down -> {
                                scrollToBottom(parent)
                                true
                            }
                            R.id.menu_clear -> {
                                if (ConfigManager.clearLogs(verbose)) {
                                    parent.showHint(R.string.logs_cleared, true)
                                    adaptor?.fullRefresh()
                                } else {
                                    parent.showHint(R.string.logs_clear_failed_2, true)
                                }
                                true
                            }
                            else -> false
                        }
                    }
                })
                val l = View.OnClickListener { scrollToTop(parent) }
                parent.binding?.clickView?.setOnClickListener(l)
                parent.binding?.toolbar?.setOnClickListener(l)
            }
        }

        fun detachListeners() {
            binding?.recyclerView?.borderViewDelegate?.setBorderVisibilityChangedListener(null)
        }

        override fun onStart() {
            super.onStart()
            attachListeners()
        }

        override fun onResume() {
            super.onResume()
            attachListeners()
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

    class UnwrapLogFragment : LogFragment() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val root = super.onCreateView(inflater, container, savedInstanceState)
            binding?.swipeRefreshLayout?.removeView(binding?.recyclerView)
            val horizontalScrollView = HorizontalScrollView(requireContext())
            horizontalScrollView.isFillViewport = true
            horizontalScrollView.isHorizontalScrollBarEnabled = false
            horizontalScrollView.layoutDirection = View.LAYOUT_DIRECTION_LTR
            binding?.swipeRefreshLayout?.addView(horizontalScrollView)
            horizontalScrollView.addView(binding?.recyclerView)
            binding?.recyclerView?.layoutParams?.width = ViewGroup.LayoutParams.WRAP_CONTENT
            return root
        }

        override fun createAdaptor(): LogAdaptor {
            return object : LogAdaptor() {
                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    super.onBindViewHolder(holder, position)
                    val view = holder.item
                    view.measure(0, 0)
                    val desiredWidth = view.measuredWidth
                    val layoutParams = view.layoutParams
                    layoutParams.width = desiredWidth
                    if ((binding?.recyclerView?.width ?: 0) < desiredWidth) {
                        binding?.recyclerView?.requestLayout()
                    }
                }
            }
        }
    }

    inner class LogPageAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            val bundle = Bundle()
            bundle.putBoolean("verbose", verbose(position))
            val f = if (getItemViewType(position) == 0) LogFragment() else UnwrapLogFragment()
            f.arguments = bundle
            return f
        }

        override fun getItemCount(): Int {
            return 2
        }

        override fun getItemId(position: Int): Long {
            return if (verbose(position)) R.string.nav_item_logs_verbose.toLong() else R.string.nav_item_logs_module.toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return itemId == R.string.nav_item_logs_verbose.toLong() || itemId == R.string.nav_item_logs_module.toLong()
        }

        fun verbose(position: Int): Boolean {
            return position != 0
        }

        override fun getItemViewType(position: Int): Int {
            return if (wordWrap?.isChecked == true) 0 else 1
        }

        @SuppressLint("NotifyDataSetChanged")
        fun refresh() {
            runOnUiThread { notifyDataSetChanged() }
        }
    }
}