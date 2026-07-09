package com.wordflip.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.settings.HeatDisplayMode
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.label
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

private val THEME_OPTIONS = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
private val HEAT_OPTIONS = HeatDisplayMode.entries
private val LAUNCH_OPTIONS = QuizLaunchMode.entries

/**
 * 设置页（REQ-SETTINGS-1~7）；规划项 Toast 占位，退出登录 REQ-AUTH-5。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: SettingsPreferences,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.Toast -> toast.show(event.message)
                SettingsUiEvent.Logout -> onLogout()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { WordFlipTopBar(title = "设置") },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
    ) { innerPadding ->
        when (val state = uiState) {
            SettingsUiState.Loading -> {
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
            is SettingsUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SettingsSectionTitle("学习体验")
                        SettingsCard {
                            SettingsToggleRow(
                                title = "翻转时自动发音",
                                subtitle = "点击翻转卡片时播放单词读音",
                                icon = { Icon(Icons.Outlined.VolumeUp, contentDescription = null) },
                                checked = state.content.autoSpeak,
                                onCheckedChange = viewModel::toggleAutoSpeak,
                            )
                            HorizontalDivider()
                            SettingsLinkRow(
                                title = "艾宾浩斯间隔",
                                subtitle = "标准方案 · 1-2-4-7-15-30 天",
                                icon = { Icon(Icons.Outlined.Psychology, contentDescription = null) },
                                onClick = { viewModel.onPlaceholderClick("艾宾浩斯间隔") },
                            )
                            HorizontalDivider()
                            SettingsLinkRow(
                                title = "默认学习方向",
                                subtitle = "看英文回忆中文",
                                icon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                                onClick = { viewModel.onPlaceholderClick("默认学习方向") },
                            )
                        }
                    }
                    item {
                        SettingsSectionTitle("外观")
                        SettingsCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Outlined.Palette, contentDescription = null)
                                    Text(
                                        text = "主题模式",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    THEME_OPTIONS.forEach { mode ->
                                        FilterChip(
                                            selected = state.content.themeMode == mode,
                                            onClick = { viewModel.setThemeMode(mode) },
                                            label = { Text(mode.label()) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item {
                        SettingsSectionTitle("测验与热力")
                        SettingsCard {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Outlined.Quiz, contentDescription = null)
                                    Text(
                                        text = "热力展示",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    HEAT_OPTIONS.forEach { mode ->
                                        FilterChip(
                                            selected = state.content.heatDisplayMode == mode,
                                            onClick = { viewModel.setHeatDisplayMode(mode) },
                                            label = { Text(mode.label()) },
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Text(
                                    text = "开测模式",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    LAUNCH_OPTIONS.forEach { mode ->
                                        FilterChip(
                                            selected = state.content.quizLaunchMode == mode,
                                            onClick = { viewModel.setQuizLaunchMode(mode) },
                                            label = { Text(mode.label()) },
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Text(
                                    text = "默认题数：${state.content.defaultQuestionLimit}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                var pendingLimit by remember {
                                    mutableFloatStateOf(state.content.defaultQuestionLimit.toFloat())
                                }
                                LaunchedEffect(state.content.defaultQuestionLimit) {
                                    pendingLimit = state.content.defaultQuestionLimit.toFloat()
                                }
                                Slider(
                                    value = pendingLimit,
                                    onValueChange = { pendingLimit = it },
                                    onValueChangeFinished = {
                                        viewModel.setDefaultQuestionLimit(pendingLimit.toInt())
                                    },
                                    valueRange = 5f..50f,
                                    steps = 8,
                                )
                            }
                        }
                    }
                    item {
                        SettingsSectionTitle("提醒")
                        SettingsCard {
                            SettingsToggleRow(
                                title = "每日提醒",
                                subtitle = "21:00",
                                icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                                checked = false,
                                onCheckedChange = {
                                    viewModel.onPlaceholderClick("每日提醒")
                                },
                            )
                            HorizontalDivider()
                            SettingsToggleRow(
                                title = "复习到期提醒",
                                subtitle = "有到期单词时额外通知",
                                icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                                checked = false,
                                onCheckedChange = {
                                    viewModel.onPlaceholderClick("复习到期提醒")
                                },
                            )
                        }
                    }
                    item {
                        SettingsSectionTitle("数据")
                        SettingsCard {
                            SettingsLinkRow(
                                title = "云端备份",
                                subtitle = "上次备份：Mock 占位",
                                icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                                onClick = { viewModel.onPlaceholderClick("云端备份") },
                            )
                            HorizontalDivider()
                            SettingsLinkRow(
                                title = "导出单词本",
                                subtitle = "CSV / Anki / Quizlet 格式",
                                icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                                onClick = { viewModel.onPlaceholderClick("导出单词本") },
                            )
                        }
                    }
                    item {
                        Button(
                            onClick = viewModel::logout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                            Text(
                                text = "退出登录",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsLinkRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = "›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
