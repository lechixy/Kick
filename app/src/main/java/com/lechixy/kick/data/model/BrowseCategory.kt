package com.lechixy.kick.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrowseCategory(val id: Long = 0, val name: String, val slug: String,
    val tags: List<String> = emptyList(), val viewers: Int = 0, val banner: CategoryBanner? = null)
@Serializable
data class CategoryBanner(val url: String? = null, val responsive: String? = null)
@Serializable
data class CategoriesResponse(val data: List<BrowseCategory> = emptyList(),
    @SerialName("current_page") val currentPage: Int = 1,
    @SerialName("last_page") val lastPage: Int = 1)
