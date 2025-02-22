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
package org.lsposed.manager.receivers

import android.os.IBinder
import android.os.Process
import android.os.RemoteException
import android.system.Os
import org.lsposed.lspd.ILSPManagerService

class LSPManagerServiceHolder private constructor(binder: IBinder) : IBinder.DeathRecipient {
    init {
        linkToDeath(binder)
        service = ILSPManagerService.Stub.asInterface(binder)
    }

    private fun linkToDeath(binder: IBinder) {
        try {
            binder.linkToDeath(this, 0)
        } catch (e: RemoteException) {
            binderDied()
        }
    }

    override fun binderDied() {
        System.exit(0)
        Process.killProcess(Os.getpid())
    }

    companion object {
        private var holder: LSPManagerServiceHolder? = null
        @JvmStatic
        var service: ILSPManagerService? = null
            private set

        @JvmStatic
        fun init(binder: IBinder) {
            if (holder == null) {
                holder = LSPManagerServiceHolder(binder)
            }
        }
    }
}
