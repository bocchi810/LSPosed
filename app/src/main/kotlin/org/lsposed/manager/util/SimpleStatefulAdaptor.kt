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
 * Copyright (C) 2022 LSPosed Contributors
 */
package org.lsposed.manager.util

import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.StatefulAdapter

abstract class SimpleStatefulAdaptor<T : RecyclerView.ViewHolder?> : RecyclerView.Adapter<T?>(),
    StatefulAdapter {
    var states: HashMap<Long?, SparseArray<Parcelable?>?> =
        HashMap<Long?, SparseArray<Parcelable?>?>()
    protected var rv: RecyclerView? = null

    init {
        setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY)
    }

    @CallSuper
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        rv = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun onViewRecycled(holder: T & Any) {
        saveStateOf(holder!!)
        super.onViewRecycled(holder)
    }

    @CallSuper
    override fun onBindViewHolder(holder: T & Any, position: Int) {
        val state = states.remove(holder!!.getItemId())
        if (state != null) {
            holder.itemView.restoreHierarchyState(state)
        }
        onBindViewHolder(holder, position)
    }

    private fun saveStateOf(holder: RecyclerView.ViewHolder) {
        val state = SparseArray<Parcelable?>()
        holder.itemView.saveHierarchyState(state)
        states.put(holder.getItemId(), state)
    }

    override fun saveState(): Parcelable {
        val childCount = rv!!.getChildCount()
        var i = 0
        while (i < childCount) {
            saveStateOf(rv!!.getChildViewHolder(rv!!.getChildAt(i)))
            ++i
        }

        val out = Bundle()
        for (state in states.entries) {
            val item = Bundle()
            for (i in 0..<state.value!!.size()) {
                item.putParcelable(state.value!!.keyAt(i).toString(), state.value!!.valueAt(i))
            }
            out.putParcelable(state.key.toString(), item)
        }
        return out
    }

    override fun restoreState(savedState: Parcelable) {
        if (savedState is Bundle) {
            for (stateKey in savedState.keySet()) {
                val array = SparseArray<Parcelable?>()
                val state = savedState.getParcelable<Parcelable?>(stateKey)
                if (state is Bundle) {
                    for (itemKey in state.keySet()) {
                        val item = state.getParcelable<Parcelable?>(itemKey)
                        array.put(itemKey.toInt(), item)
                    }
                }
                states.put(stateKey.toLong(), array)
            }
        }
    }
}
