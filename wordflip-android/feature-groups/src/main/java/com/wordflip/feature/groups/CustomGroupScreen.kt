package com.wordflip.feature.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.ui.component.EmptyStateView
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

/**
 * 手动添加分组页（REQ-CG-1~5）：从未入组词池多选 chips 创建 custom 分组。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomGroupScreen(
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomGroupViewModel = viewModel(),
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
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        topBar = {
            WordFlipTopBar(
                title = "手动添加分组",
                onNavigateBack = onNavigateBack,
            )
        },
        bottomBar = {
            val content = uiState as? CustomGroupUiState.Content
            if (content != null && !content.isEmpty) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = viewModel::saveCustomGroup,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text("保存分组")
                    }
                }
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            CustomGroupUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is CustomGroupUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::loadUnassigned,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is CustomGroupUiState.Content -> {
                if (state.isEmpty) {
                    EmptyStateView(
                        title = "暂无未入组单词",
                        message = "无未入组单词；请先勾选词书并保存设置，或导入词书",
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "从尚未入组的单词中点选，组成自定义分组",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "已选 ${state.selectedCount} 个",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            state.words.forEach { word ->
                                val selected = word.wordKey in state.selectedKeys
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.toggleWord(word.wordKey) },
                                    label = {
                                        Text(
                                            text = if (selected) {
                                                "✓ ${word.en}"
                                            } else {
                                                "○ ${word.en}"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
