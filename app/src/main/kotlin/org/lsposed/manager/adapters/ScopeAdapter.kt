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
 * Copyright (C) 2020 EdXposed Contributors
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.checkbox.MaterialCheckBox
import org.lsposed.lspd.models.Application
import org.lsposed.manager.App
import org.lsposed.manager.BuildConfig
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.databinding.ItemMasterSwitchBinding
import org.lsposed.manager.databinding.ItemModuleBinding
import org.lsposed.manager.ui.dialog.BlurBehindDialogBuilder
import org.lsposed.manager.ui.fragment.AppListFragment
import org.lsposed.manager.ui.widget.EmptyStateRecyclerView
import org.lsposed.manager.util.ModuleUtil
import rikka.core.util.ResourceUtils
import rikka.widget.mainswitchbar.MainSwitchBar
import rikka.widget.mainswitchbar.OnMainSwitchChangeListener
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class ScopeAdapter(
    private val fragment: AppListFragment,
    private val module: ModuleUtil.InstalledModule
) : EmptyStateRecyclerView.EmptyStateAdapter<ScopeAdapter.ViewHolder>(), Filterable {

    private val activity: Activity = fragment.requireActivity()
    private val pm: PackageManager = activity.packageManager
    private val preferences: SharedPreferences = App.preferences
    private val moduleUtil: ModuleUtil? = ModuleUtil.instance

    private var recommendedList = HashSet<ApplicationWithEquals>()
    private var checkedList = HashSet<ApplicationWithEquals>()
    private var searchList = ArrayList<AppInfo>()
    private var showList = ArrayList<AppInfo>()
    private var denyList = ArrayList<String>()

    private var selectedApplicationInfo: ApplicationInfo? = null
    override var isLoaded = false
    private var enabled = true

    internal val switchAdaptor = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return object : RecyclerView.ViewHolder(
                ItemMasterSwitchBinding.inflate(
                    activity.layoutInflater,
                    parent,
                    false
                ).masterSwitch
            ) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val mainSwitchBar = holder.itemView as MainSwitchBar
            mainSwitchBar.isChecked = enabled
            mainSwitchBar.addOnSwitchChangeListener(switchBarOnCheckedChangeListener)
        }

        override fun getItemCount() = 1
    }

    private val switchBarOnCheckedChangeListener =
        OnMainSwitchChangeListener { view: Switch, isChecked: Boolean ->
            enabled = isChecked
            if (moduleUtil?.setModuleEnabled(module.packageName, isChecked) == true) {
                view.isChecked = !isChecked
                enabled = !isChecked
            }
            val tmpChkList = HashSet(checkedList)
            if (isChecked && tmpChkList.isNotEmpty() && !ConfigManager.setModuleScope(
                    module.packageName,
                    module.legacy,
                    tmpChkList
                )
            ) {
                view.isChecked = false
                enabled = false
            }
            fragment.runOnUiThread { notifyDataSetChanged() }
        }

    init {
        refresh()
    }

    inner class ViewHolder(binding: ItemModuleBinding) : RecyclerView.ViewHolder(binding.root) {
        val root: ConstraintLayout = binding.itemRoot
        val appIcon: ImageView = binding.appIcon
        val appName: TextView = binding.appName
        val appPackageName: TextView = binding.appPackageName
        val appVersionName: TextView = binding.appVersionName
        val hint: TextView = binding.hint
        val checkbox: MaterialCheckBox = binding.checkbox

        init {
            checkbox.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemModuleBinding.inflate(activity.layoutInflater, parent, false))
    }

    private fun shouldHideApp(
        info: PackageInfo,
        app: ApplicationWithEquals,
        tmpChkList: HashSet<ApplicationWithEquals>
    ): Boolean {
        if (info.packageName == "system") return false
        if (tmpChkList.contains(app)) return false
        if (preferences.getBoolean("filter_denylist", false) && denyList.contains(info.packageName)) {
            return true
        }
        if (preferences.getBoolean("filter_modules", true) && info.applicationInfo?.uid?.div(App.PER_USER_RANGE)?.let {
                ModuleUtil.instance
                    ?.getModule(info.packageName, it) != null
            } == true
        ) {
            return true
        }
        if (preferences.getBoolean("filter_games", true)) {
            if (info.applicationInfo?.category == ApplicationInfo.CATEGORY_GAME) return true
            if (info.applicationInfo?.flags?.and(ApplicationInfo.FLAG_IS_GAME) != 0) return true
        }
        return preferences.getBoolean("filter_system_apps", true) &&
                (info.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0)
    }

    private fun sortApps(x: AppInfo, y: AppInfo): Int {
        val comparator = AppHelper.getAppListComparator(preferences.getInt("list_sort", 0), pm)
        val frameworkComparator = Comparator<AppInfo> { a, b ->
            when {
                a.packageName == "system" && b.packageName == "system" -> comparator?.compare(
                    a.packageInfo,
                    b.packageInfo
                )

                a.packageName == "system" -> -1
                else -> 1
            }!!
        }
        val recommendedComparator = Comparator<AppInfo> { a, b ->
            val aRecommended = recommendedList.isNotEmpty() && recommendedList.contains(a.application)
            val bRecommended = recommendedList.isNotEmpty() && recommendedList.contains(b.application)
            when {
                aRecommended == bRecommended -> frameworkComparator.compare(a, b)
                aRecommended -> -1
                else -> 1
            }
        }
        val aChecked = checkedList.contains(x.application)
        val bChecked = checkedList.contains(y.application)
        return when {
            aChecked == bChecked -> recommendedComparator.compare(x, y)
            aChecked -> -1
            else -> 1
        }
    }

    private fun checkRecommended() {
        if (!enabled) {
            fragment.showHint(R.string.module_is_not_activated_yet, false)
            return
        }
        fragment.runAsync {
            val tmpChkList = HashSet(checkedList).apply {
                removeIf { it.userId == module.userId }
                addAll(recommendedList)
            }
            ConfigManager.setModuleScope(module.packageName, module.legacy, tmpChkList)
            checkedList = tmpChkList
            fragment.runOnUiThread { notifyDataSetChanged() }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setLoaded(list: List<AppInfo>?, loaded: Boolean) {
        fragment.runOnUiThread {
            list?.let { showList = it as ArrayList<AppInfo> }
            isLoaded = loaded
            notifyDataSetChanged()
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        super.onBindViewHolder(holder, position)
        val appInfo = showList[position]
        val deny = denyList.contains(appInfo.packageName)
        holder.root.alpha = if (!deny && enabled) 1.0f else 0.5f
        val system = appInfo.packageName == "system"
        val userId = appInfo.applicationInfo.uid / App.PER_USER_RANGE
        val appName = if (system) activity.getString(R.string.android_framework) else appInfo.label
        holder.appName.text = appName
        Glide.with(holder.appIcon).load(appInfo.packageInfo).into(object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                holder.appIcon.setImageDrawable(resource)
            }

            override fun onLoadCleared(placeholder: Drawable?) {}
            override fun onLoadFailed(errorDrawable: Drawable?) {
                holder.appIcon.setImageDrawable(pm.defaultActivityIcon)
            }
        })
        if (system) {
            holder.appPackageName.text = "system"
            holder.appVersionName.visibility = View.GONE
        } else {
            holder.appVersionName.visibility = View.VISIBLE
            holder.appPackageName.text = appInfo.packageName
        }
        holder.appPackageName.visibility = View.VISIBLE
        holder.appVersionName.text = activity.getString(R.string.app_version, appInfo.packageInfo.versionName)
        val sb = SpannableStringBuilder()
        if (recommendedList.isNotEmpty() && recommendedList.contains(appInfo.application)) {
            val recommended = activity.getString(R.string.requested_by_module)
            sb.append(recommended)
            val foregroundColorSpan = ForegroundColorSpan(
                ResourceUtils.resolveColor(
                    activity.theme,
                    com.google.android.material.R.attr.colorPrimary
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val typefaceSpan = TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                sb.setSpan(typefaceSpan, sb.length - recommended.length, sb.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            } else {
                val styleSpan = StyleSpan(Typeface.BOLD)
                sb.setSpan(styleSpan, sb.length - recommended.length, sb.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            }
            sb.setSpan(
                foregroundColorSpan,
                sb.length - recommended.length,
                sb.length,
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        if (deny) {
            if (sb.isNotEmpty()) sb.append("\n")
            val denylist = activity.getString(R.string.deny_list_info)
            sb.append(denylist)
            val foregroundColorSpan = ForegroundColorSpan(
                ResourceUtils.resolveColor(
                    activity.theme,
                    com.google.android.material.R.attr.colorError
                )
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val typefaceSpan = TypefaceSpan(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                sb.setSpan(typefaceSpan, sb.length - denylist.length, sb.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            } else {
                val styleSpan = StyleSpan(Typeface.BOLD)
                sb.setSpan(styleSpan, sb.length - denylist.length, sb.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            }
            sb.setSpan(foregroundColorSpan, sb.length - denylist.length, sb.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
        holder.hint.visibility = if (sb.isEmpty()) View.GONE else View.VISIBLE
        holder.hint.text = sb

        holder.itemView.setOnCreateContextMenuListener { menu, _, _ ->
            activity.menuInflater.inflate(R.menu.menu_app_item, menu)
            menu.setHeaderTitle(appName)
            val launchIntent = AppHelper.getLaunchIntentForPackage(appInfo.packageName, userId)
            if (launchIntent == null) menu.removeItem(R.id.menu_launch)
            if (system) {
                menu.findItem(R.id.menu_force_stop).title = activity.getString(R.string.reboot)
                menu.removeItem(R.id.menu_compile_speed)
                menu.removeItem(R.id.menu_other_app)
                menu.removeItem(R.id.menu_app_info)
            }
        }

        holder.checkbox.isChecked = checkedList.contains(appInfo.application)
        holder.checkbox.setOnCheckedChangeListener { v, isChecked -> onCheckedChange(v, isChecked, appInfo) }
        holder.itemView.setOnClickListener { if (enabled) holder.checkbox.toggle() }
        holder.itemView.setOnLongClickListener {
            fragment.searchView?.clearFocus()
            selectedApplicationInfo = appInfo.applicationInfo
            false
        }
    }

    override fun getItemId(position: Int): Long {
        val info = showList[position].packageInfo
        return "${info.packageName}!${info.applicationInfo?.uid?.div(App.PER_USER_RANGE)}".hashCode().toLong()
    }

    override fun getFilter() = ApplicationFilter()

    override fun getItemCount() = showList.size

    fun refresh() = refresh(false)

    @SuppressLint("NewApi")
    fun refresh(force: Boolean) {
        setLoaded(null, false)
        enabled = moduleUtil?.isModuleEnabled(module.packageName) == true
        fragment.runAsync {
            val appList = AppHelper.getAppList(force)
            denyList = AppHelper.getDenyList(force) as ArrayList<String>
            val tmpRecList = HashSet<ApplicationWithEquals>()
            val tmpChkList = HashSet(ConfigManager.getModuleScope(module.packageName))
            val tmpList = ArrayList<AppInfo>()
            val installedList = HashSet<ApplicationWithEquals>()
            val scopeList = module.scopeList
            val emptyCheckedList = tmpChkList.isEmpty()

            appList?.parallelStream()?.forEach { info ->
                val userId = info.applicationInfo?.uid?.div(App.PER_USER_RANGE)
                val packageName = info.packageName
                if ((packageName == "system" && userId != 0) || packageName == module.packageName || packageName == BuildConfig.APPLICATION_ID) {
                    return@forEach
                }

                val application = ApplicationWithEquals(packageName, userId)
                synchronized(installedList) { installedList.add(application) }

                if (userId != module.userId) return@forEach

                if (scopeList != null && scopeList.contains(packageName)) {
                    synchronized(tmpRecList) { tmpRecList.add(application) }
                    if (emptyCheckedList) synchronized(tmpChkList) { tmpChkList.add(application) }
                } else if (shouldHideApp(info, application,
                        tmpChkList as HashSet<ApplicationWithEquals>
                    )) {
                    return@forEach
                }

                val appInfo = AppInfo().apply {
                    packageInfo = info
                    label = AppHelper.getAppLabel(info, pm)
                    this.application = application
                    this.packageName = info.packageName
                    applicationInfo = info.applicationInfo!!
                }
                synchronized(tmpList) { tmpList.add(appInfo) }
            }

            tmpChkList.retainAll(installedList)
            checkedList = tmpChkList as HashSet<ApplicationWithEquals>
            recommendedList = tmpRecList
            searchList = tmpList.stream().sorted(this::sortApps).collect(Collectors.toList()) as ArrayList<AppInfo>

            val queryStr = fragment.searchView?.query?.toString() ?: ""
            fragment.runOnUiThread { filter.filter(queryStr) }
        }
    }

    private fun onCheckedChange(buttonView: CompoundButton, isChecked: Boolean, appInfo: AppInfo) {
        val tmpChkList = HashSet(checkedList).apply {
            if (isChecked) add(appInfo.application) else remove(appInfo.application)
        }
        if (!ConfigManager.setModuleScope(module.packageName, module.legacy, tmpChkList)) {
            fragment.showHint(R.string.failed_to_save_scope_list, true)
            buttonView.isChecked = !isChecked
        } else {
            if (appInfo.packageName == "system") {
                fragment.showHint(R.string.reboot_required, true, R.string.reboot) { ConfigManager.reboot() }
            } else if (denyList.contains(appInfo.packageName)) {
                fragment.showHint(activity.getString(R.string.deny_list, appInfo.label), true)
            }
            checkedList = tmpChkList
        }
    }

    inner class ApplicationFilter : Filter() {
        private fun lowercaseContains(s: String?, filter: String) =
            !TextUtils.isEmpty(s) && s!!.lowercase(Locale.getDefault()).contains(filter)

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filter = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
            return FilterResults().apply {
                values = searchList.filter {
                    lowercaseContains(it.label?.toString(), filter) || lowercaseContains(it.packageName, filter)
                }
                count = (values as List<*>).size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            setLoaded((results?.values as? List<AppInfo>) ?: emptyList(), true)
        }
    }

    fun getSearchListener() = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?) = true
        override fun onQueryTextChange(query: String?) = true
    }

    fun onBackPressed() {
        fragment.searchView?.clearFocus()
        if (isLoaded && enabled && checkedList.isEmpty()) {
            BlurBehindDialogBuilder(activity, R.style.ThemeOverlay_MaterialAlertDialog_Centered_FullWidthButtons)
                .apply {
                    setMessage(
                        if (recommendedList.isNotEmpty()) R.string.no_scope_selected_has_recommended
                        else R.string.no_scope_selected
                    )
                    if (recommendedList.isNotEmpty()) {
                        setPositiveButton(android.R.string.ok) { _, _ -> checkRecommended() }
                    } else {
                        setPositiveButton(android.R.string.cancel, null)
                    }
                    setNegativeButton(if (recommendedList.isNotEmpty()) android.R.string.cancel else android.R.string.ok)
                    { _, _ ->
                        moduleUtil?.setModuleEnabled(module.packageName, false)
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.module_disabled_no_selection, module.appName),
                            Toast.LENGTH_LONG
                        ).show()
                        fragment.navigateUp()
                    }
                }.show()
        } else {
            fragment.navigateUp()
        }
    }

    class AppInfo {
        lateinit var packageInfo: PackageInfo
        lateinit var application: ApplicationWithEquals
        lateinit var applicationInfo: ApplicationInfo
        lateinit var packageName: String
        var label: CharSequence? = null
    }

    class ApplicationWithEquals(
        packageName: String,
        userId: Int?
    ) : Application() {  // 假设父类有默认无参构造函数
        init {
            this.packageName = packageName
            if (userId != null) {
                this.userId = userId
            }
        }

        constructor(application: Application) : this(
            application.packageName,
            application.userId
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Application
            return packageName == other.packageName && userId == other.userId
        }

        override fun hashCode() = Objects.hash(packageName, userId)
    }
}
