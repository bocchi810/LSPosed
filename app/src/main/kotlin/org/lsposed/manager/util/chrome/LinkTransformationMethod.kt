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
package org.lsposed.manager.util.chrome

import android.app.Activity
import android.graphics.Rect
import android.text.Spannable
import android.text.Spanned
import android.text.method.TransformationMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView

class LinkTransformationMethod(private val activity: Activity) : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence? {
        if (view is TextView) {
            val textView = view
            if (textView.getText() == null || textView.getText() !is Spannable) {
                return source
            }
            val text = textView.getText() as Spannable
            val spans = text.getSpans<URLSpan?>(0, textView.length(), URLSpan::class.java)
            for (i in spans.indices.reversed()) {
                val oldSpan = spans[i]
                val start = text.getSpanStart(oldSpan)
                val end = text.getSpanEnd(oldSpan)
                val url = oldSpan.getURL()
                text.removeSpan(oldSpan)
                text.setSpan(
                    CustomTabsURLSpan(activity, url),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return text
        }
        return source
    }

    override fun onFocusChanged(
        view: View?,
        sourceText: CharSequence?,
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?
    ) {
    }
}
