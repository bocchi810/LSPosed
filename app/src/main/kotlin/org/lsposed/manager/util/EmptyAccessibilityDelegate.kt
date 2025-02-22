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
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

class EmptyAccessibilityDelegate : View.AccessibilityDelegate() {
    override fun sendAccessibilityEvent(host: View, eventType: Int) {
    }

    override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
        return true
    }

    override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) {
    }

    override fun dispatchPopulateAccessibilityEvent(
        host: View,
        event: AccessibilityEvent
    ): Boolean {
        return true
    }

    override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) {
    }

    override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) {
    }

    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
    }

    override fun addExtraDataToAccessibilityNodeInfo(
        host: View,
        info: AccessibilityNodeInfo,
        extraDataKey: String,
        arguments: Bundle?
    ) {
    }

    override fun onRequestSendAccessibilityEvent(
        host: ViewGroup,
        child: View,
        event: AccessibilityEvent
    ): Boolean {
        return true
    }

    override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? {
        return null
    }
}
