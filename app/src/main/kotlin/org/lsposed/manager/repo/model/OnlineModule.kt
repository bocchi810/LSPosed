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

class OnlineModule {
    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("description")
    @Expose
    var description: String? = null

    @SerializedName("url")
    @Expose
    var url: String? = null

    @SerializedName("homepageUrl")
    @Expose
    var homepageUrl: String? = null

    @SerializedName("collaborators")
    @Expose
    var collaborators: MutableList<Collaborator?>? = ArrayList<Collaborator?>()

    @SerializedName("latestRelease")
    @Expose
    var latestRelease: String? = null

    @SerializedName("latestReleaseTime")
    @Expose
    val latestReleaseTime: String? = null

    @SerializedName("latestBetaRelease")
    @Expose
    val latestBetaRelease: String? = null

    @SerializedName("latestBetaReleaseTime")
    @Expose
    val latestBetaReleaseTime: String? = null

    @SerializedName("latestSnapshotRelease")
    @Expose
    val latestSnapshotRelease: String? = null

    @SerializedName("latestSnapshotReleaseTime")
    @Expose
    val latestSnapshotReleaseTime: String? = null

    @SerializedName("releases")
    @Expose
    var releases: MutableList<Release?>? = ArrayList<Release?>()

    @SerializedName("betaReleases")
    @Expose
    val betaReleases: MutableList<Release?> = ArrayList<Release?>()

    @SerializedName("snapshotReleases")
    @Expose
    val snapshotReleases: MutableList<Release?> = ArrayList<Release?>()

    @SerializedName("readme")
    @Expose
    var readme: String? = null

    @SerializedName("readmeHTML")
    @Expose
    var readmeHTML: String? = null

    @SerializedName("summary")
    @Expose
    var summary: String? = null

    @SerializedName("scope")
    @Expose
    var scope: MutableList<String?>? = ArrayList<String?>()

    @SerializedName("sourceUrl")
    @Expose
    var sourceUrl: String? = null

    @SerializedName("hide")
    @Expose
    var isHide: Boolean? = null

    @SerializedName("additionalAuthors")
    @Expose
    var additionalAuthors: MutableList<Any?>? = null

    @SerializedName("updatedAt")
    @Expose
    var updatedAt: String? = null

    @SerializedName("createdAt")
    @Expose
    var createdAt: String? = null

    @SerializedName("stargazerCount")
    @Expose
    var stargazerCount: Int? = null
    var releasesLoaded: Boolean = false
}
