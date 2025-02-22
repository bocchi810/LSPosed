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
package org.lsposed.manager.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.util.Pair
import androidx.navigation.NavOptions
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.google.android.material.navigation.NavigationBarView
import org.lsposed.manager.App
import org.lsposed.manager.ConfigManager
import org.lsposed.manager.R
import org.lsposed.manager.databinding.ActivityMainBinding
import org.lsposed.manager.repo.RepoLoader
import org.lsposed.manager.repo.RepoLoader.RepoListener
import org.lsposed.manager.ui.activity.base.BaseActivity
import org.lsposed.manager.util.ModuleUtil
import org.lsposed.manager.util.ModuleUtil.InstalledModule
import org.lsposed.manager.util.ModuleUtil.ModuleListener
import org.lsposed.manager.util.ShortcutUtil
import org.lsposed.manager.util.UpdateUtil
import rikka.core.util.ResourceUtils

class MainActivity : BaseActivity(), RepoListener, ModuleListener {
    private var restarting = false
    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        var savedInstanceState = savedInstanceState
        if (savedInstanceState == null) {
            savedInstanceState = getIntent().getBundleExtra(EXTRA_SAVED_INSTANCE_STATE)
        }
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(getLayoutInflater())
        setContentView(binding!!.getRoot())

        repoLoader?.addListener(this)
        moduleUtil?.addListener(this)

        onModulesReloaded()

        val navHostFragment =
            getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        if (navHostFragment == null) {
            return
        }

        val navController = navHostFragment.navController
        val nav = binding!!.nav as NavigationBarView
        setupWithNavController(nav, navController)

        handleIntent(getIntent())
    }

    protected override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }
        val navHostFragment =
            getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment) as NavHostFragment?
        if (navHostFragment == null) {
            return
        }
        val navController = navHostFragment.navController
        val nav = binding!!.nav as NavigationBarView
        if (intent.getAction() != null && intent.getAction() == "android.intent.action.APPLICATION_PREFERENCES") {
            nav.setSelectedItemId(R.id.settings_fragment)
        } else if (ConfigManager.isBinderAlive) {
            if (!TextUtils.isEmpty(intent.getDataString())) {
                when (intent.getDataString()) {
                    "modules" -> nav.setSelectedItemId(R.id.modules_nav)
                    "logs" -> nav.setSelectedItemId(R.id.logs_fragment)
                    "repo" -> {
                        if (ConfigManager.isMagiskInstalled) {
                            nav.setSelectedItemId(R.id.repo_nav)
                        }
                    }

                    "settings" -> nav.setSelectedItemId(R.id.settings_fragment)
                    else -> {
                        val data = intent.getData()
                        if (data != null && data.getScheme() == "module") {
                            navController.navigate(
                                Uri.Builder().scheme("lsposed").authority("module")
                                    .appendQueryParameter("modulePackageName", data.getHost())
                                    .appendQueryParameter("moduleUserId", data.getPort().toString())
                                    .build(),
                                NavOptions.Builder().setEnterAnim(R.anim.fragment_enter)
                                    .setExitAnim(R.anim.fragment_exit)
                                    .setPopEnterAnim(R.anim.fragment_enter_pop)
                                    .setPopExitAnim(R.anim.fragment_exit_pop)
                                    .setLaunchSingleTop(true)
                                    .setPopUpTo(navController.graph.startDestinationId, false, true)
                                    .build()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(this, R.id.nav_host_fragment)
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    fun restart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || App.isParasitic) {
            recreate()
        } else {
            try {
                val savedInstanceState = Bundle()
                onSaveInstanceState(savedInstanceState)
                finish()
                startActivity(newIntent(savedInstanceState, this))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                restarting = true
            } catch (e: Throwable) {
                recreate()
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return restarting || super.dispatchKeyEvent(event)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean {
        return restarting || super.dispatchKeyShortcutEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return restarting || super.dispatchTouchEvent(event)
    }

    override fun dispatchTrackballEvent(event: MotionEvent): Boolean {
        return restarting || super.dispatchTrackballEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        return restarting || super.dispatchGenericMotionEvent(event)
    }


    override fun onRepoLoaded() {
        val count = intArrayOf(0)
        val processedModules = HashSet<String?>()
        val modules: MutableMap<Pair<String?, Int?>?, InstalledModule?>? = moduleUtil?.modules as MutableMap<Pair<String?, Int?>?, InstalledModule?>?
        if (modules == null) return
        modules.forEach { (k: Pair<String?, Int?>?, v: InstalledModule?) ->
            if (!processedModules.contains(
                    k!!.first
                )
            ) {
                val ver: RepoLoader.ModuleVersion? = repoLoader?.getModuleLatestVersion(k.first)
                if (ver != null && ver.upgradable(v!!.versionCode, v.versionName.toString())) {
                    ++count[0]
                }
                processedModules.add(k.first)
            }
        }
        runOnUiThread(Runnable {
            if (count[0] > 0 && binding != null) {
                val nav = binding!!.nav as NavigationBarView
                val badge = nav.getOrCreateBadge(R.id.repo_nav)
                badge.setVisible(true)
                badge.setNumber(count[0])
            } else {
                onThrowable(null)
            }
        })
    }

    override fun onThrowable(t: Throwable?) {
        runOnUiThread(Runnable {
            if (binding != null) {
                val nav = binding!!.nav as NavigationBarView
                val badge = nav.getOrCreateBadge(R.id.repo_nav)
                badge.setVisible(false)
            }
        })
    }

    override fun onModulesReloaded() {
        onRepoLoaded()
        setModulesSummary(moduleUtil?.enabledModulesCount)
    }

    public override fun onResume() {
        super.onResume()
        if (ConfigManager.isBinderAlive) {
            setModulesSummary(moduleUtil?.enabledModulesCount)
        } else setModulesSummary(0)
        if (binding != null) {
            val nav = binding!!.nav as NavigationBarView
            if (UpdateUtil.needUpdate()) {
                val badge = nav.getOrCreateBadge(R.id.main_fragment)
                badge.setVisible(true)
            }

            if (!ConfigManager.isBinderAlive) {
                nav.getMenu().removeItem(R.id.logs_fragment)
                nav.getMenu().removeItem(R.id.modules_nav)
                if (!ConfigManager.isMagiskInstalled) {
                    nav.getMenu().removeItem(R.id.repo_nav)
                }
            }
        }
        if (App.isParasitic) {
            val updateShortcut = ShortcutUtil.updateShortcut()
            Log.d(App.TAG, "update shortcut success = " + updateShortcut)
        }
    }

    private fun setModulesSummary(moduleCount: Int?) {
        runOnUiThread(Runnable {
            if (binding != null) {
                val nav = binding!!.nav as NavigationBarView
                val badge = nav.getOrCreateBadge(R.id.modules_nav)
                badge.setBackgroundColor(
                    ResourceUtils.resolveColor(
                        getTheme(),
                        com.google.android.material.R.attr.colorPrimary
                    )
                )
                badge.setBadgeTextColor(
                    ResourceUtils.resolveColor(
                        getTheme(),
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                )
                if (moduleCount != null) {
                    if (moduleCount > 0) {
                        badge.setVisible(true)
                        badge.setNumber(moduleCount)
                    } else {
                        badge.setVisible(false)
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        repoLoader?.removeListener(this)
        moduleUtil?.removeListener(this)
    }

    companion object {
        private val KEY_PREFIX = MainActivity::class.java.getName() + '.'
        private val EXTRA_SAVED_INSTANCE_STATE: String = KEY_PREFIX + "SAVED_INSTANCE_STATE"

        private val repoLoader: RepoLoader? = RepoLoader.instance
        private val moduleUtil: ModuleUtil? = ModuleUtil.instance

        fun newIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
        }

        private fun newIntent(savedInstanceState: Bundle, context: Context): Intent {
            return newIntent(context)
                .putExtra(EXTRA_SAVED_INSTANCE_STATE, savedInstanceState)
        }
    }
}
