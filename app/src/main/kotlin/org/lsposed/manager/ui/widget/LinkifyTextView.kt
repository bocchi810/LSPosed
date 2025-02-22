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
package org.lsposed.manager.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spanned
import android.text.style.ClickableSpan
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView

class LinkifyTextView : AppCompatTextView {
    var currentSpan: ClickableSpan? = null
        private set

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun clearCurrentSpan() {
        this.currentSpan = null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Let the parent or grandparent of TextView to handles click action.
        // Otherwise click effect like ripple will not work, and if touch area
        // do not contain a url, the TextView will still get MotionEvent.
        // onTouchEven must be called with MotionEvent.ACTION_DOWN for each touch
        // action on it, so we analyze touched url here.
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            this.currentSpan = null

            if (getText() is Spanned) {
                // Get this code from android.text.method.LinkMovementMethod.
                // Work fine !
                var x = event.getX().toInt()
                var y = event.getY().toInt()

                x -= getTotalPaddingLeft()
                y -= getTotalPaddingTop()

                x += getScrollX()
                y += getScrollY()

                val layout = getLayout()
                if (null != layout) {
                    val line = layout.getLineForVertical(y)
                    val off = layout.getOffsetForHorizontal(line, x.toFloat())

                    val spans = (getText() as Spanned).getSpans<ClickableSpan?>(
                        off,
                        off,
                        ClickableSpan::class.java
                    )

                    if (spans.size > 0) {
                        this.currentSpan = spans[0]
                    }
                }
            }
        }

        return super.onTouchEvent(event)
    }
}
