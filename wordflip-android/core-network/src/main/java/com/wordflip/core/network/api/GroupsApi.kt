package com.wordflip.core.network.api

import com.wordflip.core.model.group.CreateCustomGroupRequest
import com.wordflip.core.model.group.GroupDetail
import com.wordflip.core.model.group.GroupListResponse
import com.wordflip.core.model.group.GroupWordsResponse
import com.wordflip.core.model.group.UnassignedWordsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 分组 API：列表/详情/词表、未入组词池、创建自定义分组。
 */
interface GroupsApi {

    @GET("groups")
    suspend fun listGroups(
        @Query("source") source: String? = null,
        @Query("sort") sort: String? = null,
    ): GroupListResponse

    @GET("groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: Int): GroupDetail

    @GET("groups/{groupId}/words")
    suspend fun listGroupWords(
        @Path("groupId") groupId: Int,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
    ): GroupWordsResponse

    /** `all=true` 返回全量未入组词（REQ-CG-1，上限 5000） */
    @GET("words/unassigned")
    suspend fun listUnassignedWords(
        @Query("all") all: Boolean = false,
        @Query("q") q: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): UnassignedWordsResponse

    @POST("groups/custom")
    suspend fun createCustomGroup(@Body request: CreateCustomGroupRequest): GroupDetail
}
