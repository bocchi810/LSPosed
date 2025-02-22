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
package org.lsposed.manager.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import androidx.recyclerview.widget.ConcatAdapter
import org.lsposed.manager.R
import org.lsposed.manager.util.SimpleStatefulAdaptor
import rikka.core.util.ResourceUtils
import androidx.core.graphics.withTranslation

class EmptyStateRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : StatefulRecyclerView(context, attrs, defStyle) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val emptyText: String

    init {
        val dm = context.getResources().getDisplayMetrics()

        paint.setColor(
            ResourceUtils.resolveColor(
                context.getTheme(),
                android.R.attr.textColorSecondary
            )
        )
        paint.setTextSize(16f * dm.scaledDensity)

        emptyText = context.getString(R.string.list_empty)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        var adapter = getAdapter()
        if (adapter is ConcatAdapter) {
            for (a in adapter.getAdapters()) {
                if (a is EmptyStateAdapter<*>) {
                    adapter = a
                    break
                }
            }
        }
        if (adapter is EmptyStateAdapter<*> && adapter.isLoaded && adapter.getItemCount() == 0) {
            val width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight()
            val height = getMeasuredHeight() - getPaddingTop() - getPaddingBottom()

            val textLayout = StaticLayout(
                emptyText,
                paint,
                width,
                Layout.Alignment.ALIGN_CENTER,
                1.0f,
                0.0f,
                false
            )

            canvas.withTranslation(
                getPaddingLeft().toFloat(),
                ((height shr 1) + getPaddingTop() - (textLayout.getHeight() shr 1)).toFloat()
            ) {
                textLayout.draw(this)

            }
        }
    }

    abstract class EmptyStateAdapter<T : ViewHolder?> : SimpleStatefulAdaptor<T?>() {
        abstract val isLoaded: Boolean
    }
}
