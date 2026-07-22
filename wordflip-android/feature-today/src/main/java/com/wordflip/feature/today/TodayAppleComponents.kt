package com.wordflip.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.navigation.StudyNavigation
import com.wordflip.core.model.today.RecentGroup
import com.wordflip.core.model.today.TodayStats
import com.wordflip.core.model.today.TodayTask
import com.wordflip.core.model.today.TodayTasks
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress

/**
 * 继续学习主卡：推荐由服务端决定，展示层只在推荐、最近与空态之间回退。
 */
@Composable
fun TodayHeroCard(
    primary: TodayPrimaryCard,
    onStudyClick: (StudyNavigation) -> Unit,
    onRecentQuizClick: (Int, String) -> Unit,
    viewModel: TodayViewModel,
) {
    when (primary) {
        is TodayPrimaryCard.Recommended -> {
            val study = primary.study
            HeroSurface(
                eyebrow = "继续学习",
                title = study.groupName,
                detail = "${study.wordCount} 词",
                onClick = {
                    onStudyClick(
                        StudyNavigation(
                            groupId = study.groupId,
                            groupName = study.groupName,
                            wordCount = study.wordCount,
                        ),
                    )
                },
            )
        }

        is TodayPrimaryCard.Recent -> {
            val group = primary.group
            HeroSurface(
                eyebrow = "继续学习",
                title = group.name,
                detail = formatRelativeStudiedAt(group.lastStudiedAt),
                onClick = {
                    onStudyClick(viewModel.resolveRecentStudyNavigation(group))
                },
                secondaryAction = {
                    HeroQuizAction(
                        onClick = { onRecentQuizClick(group.groupId, group.name) },
                    )
                },
            )
        }

        TodayPrimaryCard.Empty -> EmptyHeroSurface()
    }
}

/** 展示服务端返回的三项今日统计。 */
@Composable
fun TodayMetricsRow(stats: TodayStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(
            value = stats.masteredCount.toString(),
            label = "已掌握",
            icon = Icons.Outlined.CheckCircle,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            value = stats.dueReviewCount.toString(),
            label = "待复习",
            icon = Icons.Outlined.Schedule,
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            value = "${stats.completionPercent}%",
            label = "完成度",
            icon = Icons.AutoMirrored.Outlined.ShowChart,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 今日任务列表：计数和标题均直接消费服务端 DTO。 */
@Composable
fun TodayTaskList(
    tasks: TodayTasks,
    subtitle: (TodayTask) -> String,
    onNewWords: () -> Unit,
    onDueReview: () -> Unit,
    onQuiz: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(text = "今日任务")
        TodayTaskCard(
            task = tasks.newWords,
            subtitle = subtitle(tasks.newWords),
            icon = Icons.Outlined.AutoStories,
            countSuffix = "词",
            onClick = onNewWords,
        )
        TodayTaskCard(
            task = tasks.dueReview,
            subtitle = subtitle(tasks.dueReview),
            icon = Icons.Outlined.Schedule,
            countSuffix = "词",
            onClick = onDueReview,
        )
        TodayTaskCard(
            task = tasks.quiz,
            subtitle = subtitle(tasks.quiz),
            icon = Icons.Outlined.Quiz,
            countSuffix = "题",
            onClick = onQuiz,
        )
    }
}

/** 最近学习最多展示三组，每组保留学习与测验双入口。 */
@Composable
fun TodayRecentGroups(
    groups: List<RecentGroup>,
    onStudy: (RecentGroup) -> Unit,
    onQuiz: (RecentGroup) -> Unit,
) {
    if (groups.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(text = "最近学习")
        groups.take(3).forEach { group ->
            RecentGroupSurface(
                group = group,
                onStudy = { onStudy(group) },
                onQuiz = { onQuiz(group) },
            )
        }
    }
}

/** 今日页静态骨架，保持页面层级而不引入循环动画。 */
@Composable
fun TodayAppleSkeleton() {
    val colors = AppleUi.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .width(132.dp)
                .height(34.dp),
        )
        SkeletonBlock(
            modifier = Modifier
                .width(188.dp)
                .height(18.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            shape = RoundedCornerShape(28.dp),
            color = colors.elevatedSurface,
            tonalElevation = 0.dp,
        ) {}
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(104.dp),
                    cornerRadius = 20.dp,
                )
            }
        }
        SkeletonBlock(
            modifier = Modifier
                .width(92.dp)
                .height(22.dp),
        )
        repeat(3) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                cornerRadius = 20.dp,
            )
        }
    }
}

/** 错误态保留就地重试，不打断今日页导航层级。 */
@Composable
internal fun TodayInlineError(
    message: String,
    onRetry: () -> Unit,
) {
    val colors = AppleUi.colors
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = colors.elevatedSurface,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "暂时无法加载今日内容",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
                Surface(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .applePress(interactionSource)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onRetry,
                        ),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "重试",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSurface(
    eyebrow: String,
    title: String,
    detail: String,
    onClick: () -> Unit,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    val colors = AppleUi.colors
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .applePress(interactionSource)
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(28.dp),
        color = colors.elevatedSurface,
        shadowElevation = 2.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = colors.accent,
                    )
                    Text(
                        text = "开始学习",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.accent,
                    )
                }
                secondaryAction?.invoke()
            }
        }
    }
}

@Composable
private fun HeroQuizAction(onClick: () -> Unit) {
    val colors = AppleUi.colors
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .applePress(interactionSource)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        color = colors.accent.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Quiz,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.accent,
            )
            Text(
                text = "测验",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun EmptyHeroSurface() {
    val colors = AppleUi.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = colors.elevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "暂无推荐学习",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "可从下方今日任务或最近分组开始。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
            )
        }
    }
}

@Composable
private fun MetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = AppleUi.colors
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = colors.elevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = colors.accent,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = colors.primaryText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TodayTaskCard(
    task: TodayTask,
    subtitle: String,
    icon: ImageVector,
    countSuffix: String,
    onClick: () -> Unit,
) {
    val colors = AppleUi.colors
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .applePress(interactionSource)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        color = colors.elevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.accent.copy(alpha = 0.12f),
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = colors.accent,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = task.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${task.count} $countSuffix",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryText,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.secondaryText,
            )
        }
    }
}

@Composable
private fun RecentGroupSurface(
    group: RecentGroup,
    onStudy: () -> Unit,
    onQuiz: () -> Unit,
) {
    val colors = AppleUi.colors
    val studyInteraction = remember { MutableInteractionSource() }
    val quizInteraction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .applePress(studyInteraction)
            .clip(shape)
            .clickable(
                interactionSource = studyInteraction,
                indication = null,
                role = Role.Button,
                onClick = onStudy,
            ),
        shape = shape,
        color = colors.elevatedSurface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = colors.accent.copy(alpha = 0.12f),
            ) {
                Box(
                    modifier = Modifier.size(42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = colors.accent,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatRelativeStudiedAt(group.lastStudiedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryText,
                )
            }
            Surface(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .applePress(quizInteraction)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = quizInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onQuiz,
                    ),
                shape = RoundedCornerShape(14.dp),
                color = colors.accent.copy(alpha = 0.12f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.accent,
                    )
                    Text(
                        text = "测验",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = AppleUi.colors.primaryText,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 10.dp,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = AppleUi.colors.separator,
        tonalElevation = 0.dp,
    ) {}
}
