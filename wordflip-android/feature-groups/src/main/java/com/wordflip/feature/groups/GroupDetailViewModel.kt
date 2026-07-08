package com.wordflip.feature.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.fake.MockWordMediaStore
import com.wordflip.core.model.group.GroupWordItem
import com.wordflip.core.model.media.StainMode
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.network.groups.GroupsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 分组详情 ViewModel；GET /groups/{id} + 词表分页；掌握度 Chip 只读；污渍模式仍 Mock（P3）。
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupsRepository: GroupsRepository,
) : ViewModel() {

    private val groupId: Int = checkNotNull(savedStateHandle["groupId"])
    private val initialStainMode: Boolean = savedStateHandle.get<Boolean>("stainMode") ?: false

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
            try {
                val (group, words) = coroutineScope {
                    val groupDeferred = async { groupsRepository.loadGroupDetail(groupId) }
                    val wordsDeferred = async { groupsRepository.loadGroupWordsAll(groupId) }
                    groupDeferred.await().getOrThrow() to wordsDeferred.await().getOrThrow()
                }
                baseStainCards = words.map { it.toWordCard() }
                _uiState.value = GroupDetailUiState.Content(
                    group = group,
                    words = words,
                    stainMode = stainMode,
                    stainCards = mergeStainCards(),
                    selectedStainTypes = StainType.entries.toSet(),
                )
            } catch (error: Exception) {
                _uiState.value = GroupDetailUiState.Error(error.message ?: "加载分组详情失败")
            }
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

    /** 一键生成组内污渍（Mock，P3-A10） */
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

    private fun GroupWordItem.toWordCard(): WordCard = WordCard(
        wordKey = summary.wordKey,
        en = summary.en,
        cn = summary.cn,
        pos = summary.pos,
        ph = summary.ph,
        mastery = mastery,
    )

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
}
