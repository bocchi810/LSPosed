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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.google.android.material.textview.MaterialTextView
import org.lsposed.manager.R

class ExpandableTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : MaterialTextView(context, attrs, defStyle) {
    private var text: CharSequence? = null
    private var nextLines = 0
    private val maxLines: Int
    private val collapse: SpannableString
    private val expand: SpannableString
    private val sb = SpannableStringBuilder()
    private var lineCount = 0

    init {
        maxLines = getMaxLines()
        collapse = SpannableString(context.getString(R.string.collapse))
        val span: ClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                TransitionManager.beginDelayedTransition(getParent() as ViewGroup?)
                setMaxLines(nextLines)
                super@ExpandableTextView.setText(text)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.setTypeface(Typeface.DEFAULT_BOLD)
            }
        }
        collapse.setSpan(span, 0, collapse.length, 0)
        expand = SpannableString(context.getString(R.string.expand))
        expand.setSpan(span, 0, expand.length, 0)
        setMovementMethod(LinkMovementMethod.getInstance())
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        this.text = text
        super.setText(text, type)
    }

    override fun onPreDraw(): Boolean {
        this.getViewTreeObserver().removeOnPreDrawListener(this)
        if (lineCount == 0) {
            lineCount = getLayout().getLineCount()
        }
        if (lineCount > maxLines) {
            val hintTextOffsetEnd: Int
            if (maxLines == getMaxLines()) {
                nextLines = lineCount + 1
                hintTextOffsetEnd = getLayout().getLineStart(getMaxLines() - 1)
                setTextWithSpan(text, hintTextOffsetEnd - 1, expand)
            } else if (nextLines == getMaxLines()) {
                nextLines = maxLines
                hintTextOffsetEnd = getLayout().getLineStart(getMaxLines() - 1)
                setTextWithSpan(text, hintTextOffsetEnd, collapse)
            }
        }
        return super.onPreDraw()
    }

    private fun setTextWithSpan(
        text: CharSequence?, textOffsetEnd: Int,
        sbStr: SpannableString?
    ) {
        sb.clearSpans()
        sb.clear()
        sb.append(text, 0, textOffsetEnd)
        sb.append("\n")
        sb.append(sbStr)
        super.setText(sb, BufferType.NORMAL)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (getLayout() != null) {
            lineCount = getLayout().getLineCount()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val layout = this.getLayout()
        if (layout != null) {
            val line = layout.getLineForVertical(event.getY().toInt())
            val offset = layout.getOffsetForHorizontal(line, event.getX())

            if (getText() is Spanned) {
                val spanned = getText() as Spanned

                val links =
                    spanned.getSpans<ClickableSpan?>(offset, offset, ClickableSpan::class.java)

                if (links.size == 0) {
                    return false
                } else {
                    return super.onTouchEvent(event)
                }
            }
        }

        return false
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable("superState", super.onSaveInstanceState())
        bundle.putInt("maxLines", getMaxLines())
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var state = state
        if (state is Bundle) {
            val bundle = state
            setMaxLines(bundle.getInt("maxLines"))
            state = bundle.getParcelable<Parcelable?>("superState")
        }
        super.onRestoreInstanceState(state)
    }
}
