package com.wordflip.feature.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wordflip.core.model.group.GroupWordItem
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.model.study.WordStainPayload
import com.wordflip.core.network.groups.GroupsRepository
import com.wordflip.core.network.media.WordMediaRepository
import com.wordflip.core.network.study.StudyRepository
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
 * 分组详情 ViewModel；掌握度 Chip 只读；污渍模式走 Stains API（P3）。
 */
@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupsRepository: GroupsRepository,
    private val studyRepository: StudyRepository,
    private val wordMediaRepository: WordMediaRepository,
) : ViewModel() {

    private val groupId: Int = checkNotNull(savedStateHandle["groupId"])
    private val initialStainMode: Boolean = savedStateHandle.get<Boolean>("stainMode") ?: false

    private val _uiState = MutableStateFlow<GroupDetailUiState>(GroupDetailUiState.Loading)
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroupDetailUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<GroupDetailUiEvent> = _events.asSharedFlow()

    init {
        loadDetail(initialStainMode)
    }

    fun loadDetail(stainMode: Boolean = (_uiState.value as? GroupDetailUiState.Content)?.stainMode ?: false) {
        viewModelScope.launch {
            val previous = _uiState.value as? GroupDetailUiState.Content
            // 已有内容时静默刷新，避免从测验返回时全屏 Loading 闪烁
            if (previous == null) {
                _uiState.value = GroupDetailUiState.Loading
            }
            try {
                val (group, words) = coroutineScope {
                    val groupDeferred = async { groupsRepository.loadGroupDetail(groupId) }
                    val wordsDeferred = async { groupsRepository.loadGroupWordsAll(groupId) }
                    groupDeferred.await().getOrThrow() to wordsDeferred.await().getOrThrow()
                }
                // 污渍预览优先用 Study 聚合（含默认 seed / 已落库 config）
                val stainCards = if (stainMode) {
                    studyRepository.loadStudyGroup(groupId).getOrNull()?.words
                        ?: words.map { it.toWordCard() }
                } else {
                    words.map { it.toWordCard() }
                }
                _uiState.value = GroupDetailUiState.Content(
                    group = group,
                    words = words,
                    stainMode = stainMode,
                    stainCards = stainCards,
                    selectedStainTypes = previous?.selectedStainTypes ?: StainType.entries.toSet(),
                    stainsHidden = stainCards.all { it.stain.hidden } && stainCards.isNotEmpty(),
                )
            } catch (error: Exception) {
                if (previous != null) {
                    _events.emit(GroupDetailUiEvent.Toast(error.message ?: "刷新分组详情失败"))
                } else {
                    _uiState.value = GroupDetailUiState.Error(error.message ?: "加载分组详情失败")
                }
            }
        }
    }

    fun reload() = loadDetail()

    fun toggleStainMode() {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        val next = !content.stainMode
        if (next) {
            viewModelScope.launch {
                studyRepository.loadStudyGroup(groupId)
                    .onSuccess { payload ->
                        updateContent {
                            it.copy(
                                stainMode = true,
                                stainCards = payload.words,
                                stainsHidden = payload.words.all { w -> w.stain.hidden } && payload.words.isNotEmpty(),
                            )
                        }
                    }
                    .onFailure {
                        updateContent { it.copy(stainMode = true) }
                        _events.emit(GroupDetailUiEvent.Toast(it.message ?: "加载污渍失败"))
                    }
            }
        } else {
            updateContent { it.copy(stainMode = false) }
        }
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

    /** 一键为全组 regenerate（POST /groups/{id}/stains/batch） */
    fun batchGenerateStains() {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        val types = content.selectedStainTypes.toList()
        viewModelScope.launch {
            wordMediaRepository.batchRegenerateStains(groupId, types)
                .onSuccess {
                    studyRepository.loadStudyGroup(groupId)
                        .onSuccess { payload ->
                            updateContent { state ->
                                state.copy(
                                    stainCards = payload.words,
                                    stainsHidden = false,
                                )
                            }
                            _events.emit(GroupDetailUiEvent.Toast("已为全组生成污渍"))
                        }
                        .onFailure {
                            _events.emit(GroupDetailUiEvent.Toast("已生成，刷新失败：${it.message}"))
                        }
                }
                .onFailure { _events.emit(GroupDetailUiEvent.Toast(it.message ?: "批量生成失败")) }
        }
    }

    fun regenerateStain(wordKey: String) {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        val cardId = content.stainCards.firstOrNull { it.wordKey == wordKey }?.cardId ?: return
        viewModelScope.launch {
            wordMediaRepository.regenerateStain(cardId, content.selectedStainTypes.toList())
                .onSuccess { stain ->
                    applyStainToCard(wordKey, stain)
                    _events.emit(GroupDetailUiEvent.Toast("已更换污渍"))
                }
                .onFailure { _events.emit(GroupDetailUiEvent.Toast(it.message ?: "更换失败")) }
        }
    }

    fun toggleStainsHidden() {
        val content = _uiState.value as? GroupDetailUiState.Content ?: return
        val nextHidden = !content.stainsHidden
        val cards = content.stainCards.map { it.wordKey to it.cardId }
        viewModelScope.launch {
            var failed = 0
            cards.forEach { (key, cardId) ->
                wordMediaRepository.setStainHidden(cardId, nextHidden)
                    .onSuccess { applyStainToCard(key, it) }
                    .onFailure { failed++ }
            }
            updateContent { it.copy(stainsHidden = nextHidden) }
            _events.emit(
                GroupDetailUiEvent.Toast(
                    when {
                        failed > 0 -> "部分更新失败（$failed）"
                        nextHidden -> "已隐藏全组污渍"
                        else -> "已显示全组污渍"
                    },
                ),
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

    private fun applyStainToCard(wordKey: String, stain: WordStainPayload) {
        updateContent { content ->
            content.copy(
                stainCards = content.stainCards.map { card ->
                    if (card.wordKey == wordKey) card.copy(stain = stain) else card
                },
            )
        }
    }

    private fun GroupWordItem.toWordCard(): WordCard = WordCard(
        wordKey = summary.wordKey,
        en = summary.en,
        cn = summary.cn,
        pos = summary.pos,
        ph = summary.ph,
        enGloss = summary.enGloss,
        senses = summary.senses,
        cardId = summary.cardId,
        lexemeId = summary.lexemeId,
        bookId = summary.bookId,
        version = summary.version,
        progress = progress,
    )

    private inline fun updateContent(block: (GroupDetailUiState.Content) -> GroupDetailUiState.Content) {
        val current = _uiState.value
        if (current is GroupDetailUiState.Content) {
            _uiState.value = block(current)
        }
    }
}
