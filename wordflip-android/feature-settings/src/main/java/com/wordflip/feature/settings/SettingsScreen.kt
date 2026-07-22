package com.wordflip.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.settings.HeatDisplayMode
import com.wordflip.core.model.settings.QuizLaunchMode
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.label
import com.wordflip.core.ui.apple.AppleContextActionRow
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.AppleInfoCard
import com.wordflip.core.ui.apple.AppleUi
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
        containerColor = AppleUi.colors.canvas,
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    item {
                        AppleInfoCard(
                            title = "学习体验",
                            supportingText = buildString {
                                append(if (state.content.autoSpeak) "自动朗读已开启" else "自动朗读已关闭")
                                append(" · 默认 ${state.content.defaultQuestionLimit} 题")
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Outlined.VolumeUp,
                                    contentDescription = null,
                                    tint = AppleUi.colors.accent,
                                )
                            },
                        )
                    }
                    item {
                        SettingsGroup(title = "学习卡片") {
                            SettingsToggleRow(
                                title = "自动朗读",
                                subtitle = "点击学习卡片时自动发音（正反面）",
                                icon = Icons.Outlined.VolumeUp,
                                checked = state.content.autoSpeak,
                                onCheckedChange = viewModel::toggleAutoSpeak,
                            )
                        }
                    }
                    item {
                        SettingsGroup(title = "外观") {
                            SettingsChoiceRow(
                                title = "主题模式",
                                subtitle = "跟随当前阅读环境",
                                currentValue = state.content.themeMode.label(),
                                icon = Icons.Outlined.Palette,
                            ) {
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
                    item {
                        SettingsGroup(title = "测验与热力") {
                            SettingsChoiceRow(
                                title = "热力展示",
                                subtitle = "选择组详情的掌握度视角",
                                currentValue = state.content.heatDisplayMode.label(),
                                icon = Icons.Outlined.Psychology,
                            ) {
                                HEAT_OPTIONS.forEach { mode ->
                                    FilterChip(
                                        selected = state.content.heatDisplayMode == mode,
                                        onClick = { viewModel.setHeatDisplayMode(mode) },
                                        label = { Text(mode.label()) },
                                    )
                                }
                            }
                            SettingsDivider()
                            SettingsChoiceRow(
                                title = "开测模式",
                                subtitle = "决定开始测验前是否选题",
                                currentValue = state.content.quizLaunchMode.label(),
                                icon = Icons.Outlined.Quiz,
                            ) {
                                LAUNCH_OPTIONS.forEach { mode ->
                                    FilterChip(
                                        selected = state.content.quizLaunchMode == mode,
                                        onClick = { viewModel.setQuizLaunchMode(mode) },
                                        label = { Text(mode.label()) },
                                    )
                                }
                            }
                            SettingsDivider()
                            DefaultQuestionLimitRow(
                                value = state.content.defaultQuestionLimit,
                                onValueChangeFinished = viewModel::setDefaultQuestionLimit,
                            )
                        }
                    }
                    item {
                        SettingsGroup(title = "即将推出") {
                            ComingSoonRow(
                                title = "智能复习调度",
                                subtitle = "由服务端 FSRS 根据答题结果安排",
                                icon = Icons.Outlined.Psychology,
                                onClick = { viewModel.onPlaceholderClick("艾宾浩斯间隔") },
                            )
                            SettingsDivider()
                            ComingSoonRow(
                                title = "默认学习方向",
                                subtitle = "将可选择卡片正反面顺序",
                                icon = Icons.Outlined.Repeat,
                                onClick = { viewModel.onPlaceholderClick("默认学习方向") },
                            )
                            SettingsDivider()
                            ComingSoonRow(
                                title = "每日提醒",
                                subtitle = "将可设置固定时间学习提醒",
                                icon = Icons.Outlined.Notifications,
                                onClick = { viewModel.onPlaceholderClick("每日提醒") },
                            )
                            SettingsDivider()
                            ComingSoonRow(
                                title = "复习到期提醒",
                                subtitle = "有到期单词时额外通知",
                                icon = Icons.Outlined.Notifications,
                                onClick = { viewModel.onPlaceholderClick("复习到期提醒") },
                            )
                            SettingsDivider()
                            ComingSoonRow(
                                title = "云端备份",
                                subtitle = "将支持学习数据跨设备同步",
                                icon = Icons.Outlined.CloudUpload,
                                onClick = { viewModel.onPlaceholderClick("云端备份") },
                            )
                            SettingsDivider()
                            ComingSoonRow(
                                title = "导出单词本",
                                subtitle = "CSV / Anki / Quizlet 格式",
                                icon = Icons.Outlined.CloudUpload,
                                onClick = { viewModel.onPlaceholderClick("导出单词本") },
                            )
                        }
                    }
                    item {
                        SettingsGroup(title = "账户") {
                            AppleContextActionRow(
                                label = "退出登录",
                                supportingText = "移除当前设备的登录状态",
                                destructive = true,
                                onClick = viewModel::logout,
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                                        contentDescription = null,
                                        tint = AppleUi.colors.destructive,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 用 inset-grouped 表面组织一组设置项。 */
@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(title)
        AppleGroupedSurface(contentPadding = PaddingValues(0.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = AppleUi.colors.secondaryText,
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = AppleUi.colors.accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = AppleUi.colors.primaryText,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = AppleUi.colors.secondaryText,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsChoiceRow(
    title: String,
    subtitle: String,
    currentValue: String,
    icon: ImageVector,
    options: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = AppleUi.colors.accent)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppleUi.colors.primaryText,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                )
            }
            Text(
                text = currentValue,
                style = MaterialTheme.typography.labelMedium,
                color = AppleUi.colors.accent,
            )
        }
        FlowRow(
            modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options()
        }
    }
}

@Composable
private fun DefaultQuestionLimitRow(
    value: Int,
    onValueChangeFinished: (Int) -> Unit,
) {
    var pendingLimit by remember { mutableFloatStateOf(value.toFloat()) }

    LaunchedEffect(value) {
        pendingLimit = value.toFloat()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Quiz,
                contentDescription = null,
                tint = AppleUi.colors.accent,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "默认题数",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppleUi.colors.primaryText,
                )
                Text(
                    text = "拖动时仅预览，松手后保存",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleUi.colors.secondaryText,
                )
            }
            Text(
                text = "${pendingLimit.toInt()} 题",
                style = MaterialTheme.typography.labelMedium,
                color = AppleUi.colors.accent,
            )
        }
        Slider(
            value = pendingLimit,
            onValueChange = { pendingLimit = it },
            onValueChangeFinished = {
                // 仅在完成拖动时调用既有回调，保持单次持久化语义。
                onValueChangeFinished(pendingLimit.toInt())
            },
            modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 8.dp),
            valueRange = 5f..50f,
            steps = 8,
        )
    }
}

@Composable
private fun ComingSoonRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AppleContextActionRow(
        label = title,
        supportingText = subtitle,
        onClick = onClick,
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null, tint = AppleUi.colors.accent)
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "即将推出",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppleUi.colors.secondaryText,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = AppleUi.colors.secondaryText,
                )
            }
        },
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = AppleUi.colors.separator,
    )
}
