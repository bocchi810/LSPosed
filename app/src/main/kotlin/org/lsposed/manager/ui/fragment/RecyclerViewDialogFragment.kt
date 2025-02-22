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

import android.app.Dialog
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import org.lsposed.lspd.models.UserInfo
import org.lsposed.manager.R
import org.lsposed.manager.databinding.DialogTitleBinding
import org.lsposed.manager.databinding.SwiperefreshRecyclerviewBinding
import org.lsposed.manager.ui.dialog.BlurBehindDialogBuilder
import org.lsposed.manager.util.ModuleUtil.InstalledModule

class RecyclerViewDialogFragment : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val parent = getParentFragment()
        val arguments = getArguments()
        check(!(parent !is ModulesFragment || arguments == null))
        val modulesFragment = parent
        val user = arguments.getParcelable<Parcelable?>("userInfo") as UserInfo?

        val pickAdaptor = modulesFragment.createPickModuleAdapter(user!!)
        val binding = SwiperefreshRecyclerviewBinding.inflate(
            LayoutInflater.from(requireActivity()),
            null,
            false
        )

        binding.recyclerView.setAdapter(pickAdaptor)
        binding.recyclerView.setLayoutManager(LinearLayoutManager(requireActivity()))
        pickAdaptor.registerAdapterDataObserver(object : AdapterDataObserver() {
            override fun onChanged() {
                binding.swipeRefreshLayout.setRefreshing(!pickAdaptor.isDataLoaded())
            }
        })
        binding.swipeRefreshLayout.setProgressViewEndTarget(
            true,
            binding.swipeRefreshLayout.getProgressViewEndOffset()
        )
        binding.swipeRefreshLayout.setOnRefreshListener(OnRefreshListener { pickAdaptor.fullRefresh() })
        pickAdaptor.refresh()
        val title = DialogTitleBinding.inflate(getLayoutInflater()).getRoot()
        title.setText(getString(R.string.install_to_user, user.name))
        val dialog = BlurBehindDialogBuilder(
            requireActivity(),
            R.style.ThemeOverlay_MaterialAlertDialog_FullWidthButtons
        )
            .setCustomTitle(title)
            .setView(binding.getRoot())
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        title.setOnClickListener(View.OnClickListener { s: View? ->
            binding.recyclerView.smoothScrollToPosition(
                0
            )
        })
        pickAdaptor.setOnPickListener(View.OnClickListener { picked: View? ->
            val module = picked!!.getTag() as InstalledModule
            modulesFragment.installModuleToUser(module, user)
            dialog.dismiss()
        })
        onViewCreated(binding.getRoot(), savedInstanceState)
        return dialog
    }

    // prevent from overriding
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
