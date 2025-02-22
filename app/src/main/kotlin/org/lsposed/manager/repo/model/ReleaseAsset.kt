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
package org.lsposed.manager.repo.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class ReleaseAsset {
    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("contentType")
    @Expose
    var contentType: String? = null

    @SerializedName("downloadUrl")
    @Expose
    var downloadUrl: String? = null

    @SerializedName("downloadCount")
    @Expose
    var downloadCount: Int = 0

    @SerializedName("size")
    @Expose
    var size: Int = 0
}
