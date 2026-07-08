package com.wordflip.feature.groups

import com.wordflip.core.model.group.GroupDetail
import com.wordflip.core.model.group.GroupSource
import com.wordflip.core.model.group.GroupStatus

/** 分组列表页 UI 状态 */
sealed interface GroupsUiState {
    data object Loading : GroupsUiState

    data class Content(
        val groups: List<GroupDetail>,
        val statusFilter: GroupStatusFilter = GroupStatusFilter.ALL,
        val sourceFilter: GroupSourceFilter = GroupSourceFilter.ALL,
        val searchQuery: String = "",
    ) : GroupsUiState {
        /** 按状态、来源与搜索词过滤后的列表 */
        val filteredGroups: List<GroupDetail>
            get() = groups.filter { group ->
                matchesStatus(group) && matchesSource(group) && matchesSearch(group)
            }

        val statusCounts: Map<GroupStatusFilter, Int>
            get() = mapOf(
                GroupStatusFilter.ALL to groups.size,
                GroupStatusFilter.NOT_STARTED to groups.count { it.status == GroupStatus.NOT_STARTED },
                GroupStatusFilter.LEARNING to groups.count { it.status == GroupStatus.LEARNING },
                GroupStatusFilter.COMPLETED to groups.count { it.status == GroupStatus.COMPLETED },
            )

        val hasCustomGroups: Boolean
            get() = groups.any { it.source == GroupSource.CUSTOM }

        private fun matchesStatus(group: GroupDetail): Boolean = when (statusFilter) {
            GroupStatusFilter.ALL -> true
            GroupStatusFilter.NOT_STARTED -> group.status == GroupStatus.NOT_STARTED
            GroupStatusFilter.LEARNING -> group.status == GroupStatus.LEARNING
            GroupStatusFilter.COMPLETED -> group.status == GroupStatus.COMPLETED
        }

        private fun matchesSource(group: GroupDetail): Boolean = when (sourceFilter) {
            GroupSourceFilter.ALL -> true
            GroupSourceFilter.AUTO -> group.source == GroupSource.AUTO
            GroupSourceFilter.CUSTOM -> group.source == GroupSource.CUSTOM
        }

        private fun matchesSearch(group: GroupDetail): Boolean {
            val query = searchQuery.trim()
            if (query.isEmpty()) return true
            return group.name.contains(query, ignoreCase = true)
        }
    }

    data class Error(val message: String) : GroupsUiState
}

/** 学习状态筛选（REQ-GROUP-1） */
enum class GroupStatusFilter(val label: String) {
    ALL("全部"),
    NOT_STARTED("未开始"),
    LEARNING("学习中"),
    COMPLETED("已完成"),
}

/** 分组来源筛选 */
enum class GroupSourceFilter(val label: String) {
    ALL("全部来源"),
    AUTO("自动分组"),
    CUSTOM("自定义"),
}

sealed interface GroupsUiEvent {
    data class Toast(val message: String) : GroupsUiEvent

    data class NavigateToSnapshot(val groupId: Int, val groupName: String) : GroupsUiEvent

    data class NavigateToStainMode(val groupId: Int, val groupName: String) : GroupsUiEvent
}
