package com.wordflip.core.network.groups

import com.wordflip.core.model.group.CreateCustomGroupRequest
import com.wordflip.core.model.group.GroupDetail
import com.wordflip.core.model.group.GroupWordItem
import com.wordflip.core.model.group.UnassignedWordsResponse
import com.wordflip.core.network.ApiErrorParser
import com.wordflip.core.network.api.GroupsApi

/**
 * 分组业务编排：GET /groups、详情+词表分页、未入组词池、POST /groups/custom。
 */
class GroupsRepository(
    private val groupsApi: GroupsApi,
    private val apiErrorParser: ApiErrorParser,
) {

    suspend fun loadGroups(): Result<List<GroupDetail>> = apiCall {
        groupsApi.listGroups().groups
    }

    suspend fun loadGroupDetail(groupId: Int): Result<GroupDetail> = apiCall {
        groupsApi.getGroup(groupId)
    }

    /** 拉取分组内全部单词（按 totalPages 顺序分页，REQ-GROUP 只读掌握度） */
    suspend fun loadGroupWordsAll(groupId: Int): Result<List<GroupWordItem>> = apiCall {
        val firstPage = groupsApi.listGroupCards(groupId = groupId, page = 1, size = 100)
        val dtos = firstPage.words.toMutableList()
        if (firstPage.totalPages > 1) {
            for (page in 2..firstPage.totalPages) {
                dtos += groupsApi.listGroupCards(groupId = groupId, page = page, size = firstPage.size).words
            }
        }
        dtos.map { it.toGroupWordItem() }
    }

    /** CustomGroup 全量未入组词池（all=true） */
    suspend fun loadUnassignedAll(): Result<UnassignedWordsResponse> = apiCall {
        groupsApi.listUnassignedCards(all = true)
    }

    suspend fun createCustomGroup(cardIds: List<Long>, name: String? = null): Result<GroupDetail> =
        apiCall {
            groupsApi.createCustomGroup(
                CreateCustomGroupRequest(cardIds = cardIds, name = name),
            )
        }

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (throwable: Throwable) {
        Result.failure(Exception(apiErrorParser.parseMessage(throwable), throwable))
    }
}
