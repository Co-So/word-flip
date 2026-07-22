package com.wordflip.feature.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.study.WordSummary
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePageTitle
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress
import com.wordflip.core.ui.component.EmptyStateView
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 手动添加分组页（REQ-CG-1~5）：只展示既有状态中的未入组卡，并保留原保存校验。
 */
@Composable
fun CustomGroupScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomGroupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomGroupUiEvent.Toast -> toast.show(event.message)
                CustomGroupUiEvent.Saved -> onSaved()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppleUi.colors.canvas,
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        topBar = {
            WordFlipTopBar(
                title = "",
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            val content = uiState as? CustomGroupUiState.Content
            if (content != null && !content.isEmpty) {
                Surface(
                    color = AppleUi.colors.glass,
                    shadowElevation = 12.dp,
                    tonalElevation = 0.dp,
                ) {
                    // 0 选时仍调用原保存事件，由 ViewModel 保持既有提示与业务校验。
                    ApplePrimaryAction(
                        text = if (content.selectedCount == 0) {
                            "保存分组"
                        } else {
                            "保存分组 · 已选 ${content.selectedCount} 个"
                        },
                        onClick = viewModel::saveCustomGroup,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            CustomGroupUiState.Loading -> CustomGroupLoading(
                modifier = Modifier.padding(innerPadding),
            )
            is CustomGroupUiState.Error -> CustomGroupError(
                message = state.message,
                onRetry = viewModel::loadUnassigned,
                modifier = Modifier.padding(innerPadding),
            )
            is CustomGroupUiState.Content -> CustomGroupContent(
                state = state,
                onToggleCard = viewModel::toggleCard,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun CustomGroupLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CustomGroupHeader()
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppleUi.colors.accent)
        }
    }
}

@Composable
private fun CustomGroupError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CustomGroupHeader()
        NetworkErrorView(
            message = message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 列表逐项映射 state.words，不补充或过滤客户端词池。 */
@Composable
private fun CustomGroupContent(
    state: CustomGroupUiState.Content,
    onToggleCard: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title") {
            CustomGroupHeader()
        }
        item(key = "selection_summary") {
            AppleGroupedSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    text = "已选 ${state.selectedCount} 个",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.selectedCount > 0) AppleUi.colors.accent else AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "点按单词即可加入或移出当前选择",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                )
            }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    EmptyStateView(
                        title = "暂无未入组单词",
                        message = "请先勾选词书并保存设置，或导入词书",
                    )
                }
            }
        } else {
            item(key = "words_title") {
                Text(
                    text = "可选单词",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.words, key = { it.cardId }) { word ->
                val selected = word.cardId in state.selectedCardIds
                SelectableWordRow(
                    word = word,
                    selected = selected,
                    onClick = { onToggleCard(word.cardId) },
                )
            }
        }
    }
}

@Composable
private fun CustomGroupHeader() {
    ApplePageTitle(
        title = "手动分组",
        subtitle = "仅显示尚未入组的单词",
    )
}

/** 整行提供 48dp 以上触控区域，并以图标和语义色同步选中状态。 */
@Composable
private fun SelectableWordRow(
    word: WordSummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactions = remember(word.cardId) { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .applePress(interactions)
            .clickable(
                role = Role.Checkbox,
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AppleUi.colors.accent.copy(alpha = 0.12f) else AppleUi.colors.groupedSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) AppleUi.colors.accent.copy(alpha = 0.14f) else AppleUi.colors.separator,
                tonalElevation = 0.dp,
            ) {
                Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (selected) "已选择" else "未选择",
                        tint = if (selected) AppleUi.colors.accent else AppleUi.colors.secondaryText,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = word.en,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleUi.colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = word.displayMeaning().ifBlank { "暂无释义" },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
