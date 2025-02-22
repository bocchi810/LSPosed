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

import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import org.lsposed.manager.App
import org.lsposed.manager.R
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

abstract class BaseFragment : Fragment() {
    fun navigateUp() {
        this.navController.navigateUp()
    }

    val navController: NavController
        get() = NavHostFragment.findNavController(this)

    fun safeNavigate(@IdRes resId: Int): Boolean {
        try {
            this.navController.navigate(resId)
            return true
        } catch (ignored: IllegalArgumentException) {
            return false
        }
    }

    fun safeNavigate(direction: NavDirections): Boolean {
        try {
            this.navController.navigate(direction)
            return true
        } catch (ignored: IllegalArgumentException) {
            return false
        }
    }

    fun setupToolbar(toolbar: MaterialToolbar?, tipsView: View?, title: Int) {
        setupToolbar(toolbar, tipsView, getString(title), -1)
    }

    fun setupToolbar(toolbar: Toolbar, tipsView: View?, title: Int, menu: Int) {
        setupToolbar(toolbar as MaterialToolbar?, tipsView, getString(title), menu, null)
    }

    @JvmOverloads
    fun setupToolbar(
        toolbar: MaterialToolbar?,
        tipsView: View?,
        title: String?,
        menu: Int,
        navigationOnClickListener: View.OnClickListener? = null
    ) {
        toolbar?.setNavigationOnClickListener(if (navigationOnClickListener == null) (View.OnClickListener { v: View? -> navigateUp() }) else navigationOnClickListener)
        toolbar?.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        toolbar?.setTitle(title)
        toolbar?.setTooltipText(title)
        if (tipsView != null) tipsView.setTooltipText(title)
        if (menu != -1) {
            toolbar?.inflateMenu(menu)
            if (this is MenuProvider) {
                toolbar?.setOnMenuItemClickListener(Toolbar.OnMenuItemClickListener { menuItem: MenuItem? ->
                    menuItem?.let {
                        onMenuItemSelected(
                            it
                        )
                    } == true
                })
                toolbar?.getMenu()?.let { onPrepareMenu(it) }
            }
        }
    }

    fun runAsync(runnable: Runnable?) {
        App.executorService?.submit(runnable)
    }

    fun <T> runAsync(callable: Callable<T?>?): Future<T?>? {
        return App.executorService?.submit<T?>(callable)
    }

    fun runOnUiThread(runnable: Runnable) {
        App.mainHandler.post(runnable)
    }

    fun <T> runOnUiThread(callable: Callable<T?>?): Future<T?> {
        val task = FutureTask<T?>(callable)
        runOnUiThread(task)
        return task
    }

    fun showHint(
        @StringRes res: Int,
        lengthShort: Boolean,
        @StringRes actionRes: Int,
        action: View.OnClickListener?
    ) {
        showHint(
            App.instance?.getString(res).toString(),
            lengthShort,
            App.instance?.getString(actionRes),
            action
        )
    }

    fun showHint(@StringRes res: Int, lengthShort: Boolean) {
        showHint(App.instance?.getString(res).toString(), lengthShort, null, null)
    }

    @JvmOverloads
    fun showHint(
        str: CharSequence,
        lengthShort: Boolean,
        actionStr: CharSequence? = null,
        action: View.OnClickListener? = null
    ) {
        val container = getView()
        if (isResumed() && container != null) {
            val snackbar = Snackbar.make(
                container,
                str,
                if (lengthShort) Snackbar.LENGTH_SHORT else Snackbar.LENGTH_LONG
            )
            val fab = container.findViewById<View?>(R.id.fab)
            if (fab is FloatingActionButton && fab.isOrWillBeShown()) snackbar.setAnchorView(fab)
            if (actionStr != null && action != null) snackbar.setAction(actionStr, action)
            snackbar.show()
            return
        }
        runOnUiThread(Runnable {
            try {
                Toast.makeText(
                    App.instance,
                    str,
                    if (lengthShort) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                ).show()
            } catch (ignored: Throwable) {
            }
        })
    }
}
