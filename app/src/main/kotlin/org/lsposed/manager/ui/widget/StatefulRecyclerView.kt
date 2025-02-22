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
package org.lsposed.manager.ui.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import androidx.viewpager2.adapter.StatefulAdapter
import rikka.widget.borderview.BorderRecyclerView

open class StatefulRecyclerView : BorderRecyclerView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )


    public override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("superState", super.onSaveInstanceState())
        val adapter = getAdapter()
        if (adapter is StatefulAdapter) {
            bundle.putParcelable("adaptor", (adapter as StatefulAdapter).saveState())
        }
        return bundle
    }

    public override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val bundle = state
            super.onRestoreInstanceState(bundle.getParcelable<Parcelable?>("superState"))
            val adapter = getAdapter()
            if (adapter is StatefulAdapter) {
                (adapter as StatefulAdapter).restoreState(bundle.getParcelable<Parcelable?>("adaptor")!!)
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }
}
