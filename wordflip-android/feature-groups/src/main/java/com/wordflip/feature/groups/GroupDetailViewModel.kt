package com.wordflip.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.FakeGroupsData
import com.wordflip.core.model.fake.FakeStudyData
import com.wordflip.core.model.fake.MockWordMediaStore
import com.wordflip.core.model.media.StainMode
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.study.WordCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分组详情 ViewModel；掌握度 Chip 只读展示；污渍模式 P3-A10 Mock。
 */
class GroupDetailViewModel(
    private val groupId: Int,
    initialStainMode: Boolean,
) : ViewModel() {

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroupDetailUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<GroupDetailUiEvent> = _events.asSharedFlow()

    private var baseStainCards: List<WordCard> = emptyList()

    init {
        loadDetail(initialStainMode)
        viewModelScope.launch {
            MockWordMediaStore.revision.collect {
                refreshStainCards()
            }
        }
    }

    fun loadDetail(stainMode: Boolean = (_uiState.value as? GroupDetailUiState.Content)?.stainMode ?: false) {
        viewModelScope.launch {
            _uiState.value = GroupDetailUiState.Loading
            delay(200)
            val group = FakeGroupsData.findById(groupId)
            if (group == null) {
                _uiState.value = GroupDetailUiState.Error("未找到分组")
                return@launch
            }
            val payload = FakeStudyData.forGroup(groupId)
            baseStainCards = payload?.words.orEmpty()
            _uiState.value = GroupDetailUiState.Content(
                group = group,
                words = FakeStudyData.wordsForGroupDetail(groupId),
                stainMode = stainMode,
                stainCards = mergeStainCards(),
                selectedStainTypes = StainType.entries.toSet(),
            )
        }
    }

    fun reload() = loadDetail()

    fun toggleStainMode() {
        updateContent { it.copy(stainMode = !it.stainMode, stainCards = mergeStainCards()) }
    }

    fun toggleStainType(type: StainType) {
        updateContent { content ->
            val next = if (type in content.selectedStainTypes) {
                content.selectedStainTypes - type
            } else {
                content.selectedStainTypes + type
            }
            content.copy(selectedStainTypes = next.ifEmpty { setOf(StainType.COFFEE) })
        }
    }

    /** 一键生成组内污渍 */
    fun batchGenerateStains() {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        val keys = baseStainCards.map { it.wordKey }
        val types = content.selectedStainTypes.toList()
        MockWordMediaStore.batchRegenerateStains(keys, StainMode.MULTI, types)
        viewModelScope.launch {
            _events.emit(GroupDetailUiEvent.Toast("已为全组生成污渍"))
        }
    }

    fun regenerateStain(wordKey: String) {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        MockWordMediaStore.regenerateStain(
            wordKey,
            StainMode.RANDOM,
            content.selectedStainTypes.toList(),
        )
        viewModelScope.launch {
            _events.emit(GroupDetailUiEvent.Toast("已更换污渍"))
        }
    }

    fun toggleStainsHidden() {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        val keys = baseStainCards.map { it.wordKey }
        val nextHidden = !content.stainsHidden
        MockWordMediaStore.batchSetStainHidden(keys, nextHidden)
        updateContent { it.copy(stainsHidden = nextHidden) }
        viewModelScope.launch {
            _events.emit(
                GroupDetailUiEvent.Toast(if (nextHidden) "已隐藏全组污渍" else "已显示全组污渍"),
            )
        }
    }

    fun resolveStudyNavigation(): StudyNavigation? {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return null
        return StudyNavigation(
            groupId = content.group.id,
            groupName = content.group.name,
            wordCount = content.group.stats.total,
        )
    }

    private fun mergeStainCards(): List<WordCard> =
        MockWordMediaStore.applyToWords(baseStainCards)

    private fun refreshStainCards() {
        updateContent { it.copy(stainCards = mergeStainCards()) }
    }

    private inline fun updateContent(block: (GroupDetailUiState.Content) -> GroupDetailUiState.Content) {
        val current = _uiState.value
        if (current is GroupDetailUiState.Content) {
            _uiState.value = block(current)
        }
    }

    class Factory(
        private val groupId: Int,
        private val initialStainMode: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupDetailViewModel(groupId, initialStainMode) as T
        }
    }
}
