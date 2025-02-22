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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView
import rikka.widget.borderview.BorderRecyclerView

class ScrollWebView : WebView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            val viewParent: ViewParent? = findViewParentIfNeeds(this)
            if (viewParent != null) viewParent.requestDisallowInterceptTouchEvent(true)
        }
        return super.onTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        if (clampedX) {
            val viewParent: ViewParent? = findViewParentIfNeeds(this)
            if (viewParent != null) viewParent.requestDisallowInterceptTouchEvent(false)
        }
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }

    companion object {
        private fun findViewParentIfNeeds(v: View): ViewParent? {
            val parent = v.getParent()
            if (parent == null) return null
            if (parent is RecyclerView && parent !is BorderRecyclerView) {
                return parent
            } else if (parent is View) {
                return findViewParentIfNeeds(parent as View)
            } else {
                return parent
            }
        }
    }
}
