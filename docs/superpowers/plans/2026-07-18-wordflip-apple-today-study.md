# WordFlip Apple Today & Study Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Android 今日页、学习页和五 Tab 导航壳重构为已批准的 Apple 风格，并保留全部现有业务数据流。

**Architecture:** 新视觉基础组件放入 `core-ui`，浮动 Tab 导航留在 `app/navigation`，今日页只消费现有 `TodayViewModel`，学习页通过 feature 内 DataStore 偏好选择三种纯布局。现有 Repository、DTO、OpenAPI、FSRS 和媒体业务保持不变。

**Tech Stack:** Kotlin 2.3、Jetpack Compose BOM 2024.10.01、Material 3、Compose Foundation Pager/Grid、DataStore Preferences、Hilt、JUnit 4。

## Global Constraints

- 当前工作树包含大量用户未提交的 v7/FSRS 改动；禁止覆盖、回退或格式化无关文件。
- 设置、词书、分组、统计四页只继承浮动导航壳，页面内容不修改。
- 学习视图模式只在学习页设置，默认 `HYBRID`，持久化最后选择。
- Android 不计算 SRS、掌握度、任务数量或测验结果；翻卡与 session 上报不得写 FSRS。
- 不修改 OpenAPI、服务端、Repository、DTO 或 `StudyViewModel` 中无关的 v7 迁移代码。
- 新增和修改的业务类、方法文档与关键逻辑使用简体中文注释。
- 不新增第三方动画或毛玻璃依赖。
- 不自动执行 Git commit、push 或 PR；每个任务以 diff 检查点结束。

---

## File Map

### Create

- `wordflip-android/core-ui/src/main/java/com/wordflip/core/ui/apple/AppleUi.kt`：Apple 色彩、玻璃表面和即时按压 Modifier。
- `wordflip-android/app/src/main/java/com/wordflip/navigation/FloatingTabBar.kt`：五 Tab 浮动导航壳。
- `wordflip-android/feature-today/src/main/java/com/wordflip/feature/today/TodayPresentation.kt`：主卡选择纯逻辑。
- `wordflip-android/feature-today/src/main/java/com/wordflip/feature/today/TodayAppleComponents.kt`：今日页展示组件与骨架。
- `wordflip-android/feature-today/src/test/java/com/wordflip/feature/today/TodayPresentationTest.kt`：主卡回退顺序测试。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyViewMode.kt`：三种模式及存储编解码。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyViewModePreferences.kt`：模式 DataStore。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyModePicker.kt`：源点明确的模式菜单。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyLayouts.kt`：Focus/Grid/Hybrid 三种布局。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyInteraction.kt`：自动发音判断纯逻辑。
- `wordflip-android/feature-study/src/test/java/com/wordflip/feature/study/StudyViewModeTest.kt`：默认值和编解码测试。
- `wordflip-android/feature-study/src/test/java/com/wordflip/feature/study/StudyInteractionTest.kt`：正面到背面发音测试。

### Modify

- `wordflip-android/app/src/main/java/com/wordflip/navigation/MainScreen.kt:45-96`：接入浮动导航并保留 route 行为。
- `wordflip-android/feature-today/src/main/java/com/wordflip/feature/today/TodayScreen.kt:65-371`：替换页面结构。
- `wordflip-android/feature-today/build.gradle.kts:16-32`：增加 JUnit 测试依赖。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyScreen.kt:60-430`：接入偏好、模式菜单和三种布局。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyToolbar.kt:23-63`：改为浮动控制条。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyDetailSheet.kt:32-128`：只调整材质和排版。
- `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyGuideOverlay.kt:26-67`：Apple 风格引导卡。
- `wordflip-android/feature-study/build.gradle.kts:16-36`：增加 JUnit 测试依赖。
- `wordflip-android/core-ui/src/main/java/com/wordflip/core/ui/component/FlipCard.kt:68-374`：即时按压与可中断 spring 翻转。

---

### Task 1: 学习视图模式与持久化偏好

**Files:**
- Create: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyViewMode.kt`
- Create: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyViewModePreferences.kt`
- Create: `wordflip-android/feature-study/src/test/java/com/wordflip/feature/study/StudyViewModeTest.kt`
- Modify: `wordflip-android/feature-study/build.gradle.kts`

**Interfaces:**
- Produces: `enum class StudyViewMode { FOCUS, GRID, HYBRID }`
- Produces: `StudyViewMode.fromStorage(raw: String?): StudyViewMode`
- Produces: `StudyViewModePreferences.modeFlow: Flow<StudyViewMode>`
- Produces: `suspend fun StudyViewModePreferences.setMode(mode: StudyViewMode)`

- [ ] **Step 1: 增加测试依赖并写失败测试**

```kotlin
package com.wordflip.feature.study

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyViewModeTest {
    @Test
    fun `空值默认使用焦点加缩略轨道`() {
        assertEquals(StudyViewMode.HYBRID, StudyViewMode.fromStorage(null))
    }

    @Test
    fun `持久化值可恢复三种模式`() {
        StudyViewMode.entries.forEach { mode ->
            assertEquals(mode, StudyViewMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun `未知值安全回退默认模式`() {
        assertEquals(StudyViewMode.HYBRID, StudyViewMode.fromStorage("unknown"))
    }
}
```

在 `dependencies` 增加：

```kotlin
testImplementation(libs.junit)
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
cd wordflip-android
.\gradlew.bat :feature-study:testDebugUnitTest --tests "com.wordflip.feature.study.StudyViewModeTest"
```

Expected: FAIL，提示 `Unresolved reference: StudyViewMode`。

- [ ] **Step 3: 实现模式模型**

```kotlin
package com.wordflip.feature.study

/** 学习页三种纯展示模式；不改变卡片顺序与业务状态。 */
enum class StudyViewMode(
    val storageValue: String,
    val label: String,
) {
    FOCUS("focus", "专注卡组"),
    GRID("grid", "卡片墙"),
    HYBRID("hybrid", "焦点与缩略轨道"),
    ;

    companion object {
        /** 未保存或保存值失效时使用已确认的混合模式。 */
        fun fromStorage(raw: String?): StudyViewMode =
            entries.firstOrNull { it.storageValue == raw } ?: HYBRID
    }
}
```

- [ ] **Step 4: 实现 feature 内 DataStore 偏好**

```kotlin
package com.wordflip.feature.study

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studyViewDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "study_view_prefs",
)

private val KEY_STUDY_VIEW_MODE = stringPreferencesKey("study_view_mode")

/** 学习布局偏好；只保存 UI 模式，不保存业务进度。 */
class StudyViewModePreferences(
    private val context: Context,
) {
    val modeFlow: Flow<StudyViewMode> = context.studyViewDataStore.data.map { preferences ->
        StudyViewMode.fromStorage(preferences[KEY_STUDY_VIEW_MODE])
    }

    suspend fun setMode(mode: StudyViewMode) {
        context.studyViewDataStore.edit { preferences ->
            preferences[KEY_STUDY_VIEW_MODE] = mode.storageValue
        }
    }
}
```

- [ ] **Step 5: 运行测试并检查差异**

Run:

```powershell
.\gradlew.bat :feature-study:testDebugUnitTest --tests "com.wordflip.feature.study.StudyViewModeTest"
git diff --check -- feature-study
```

Expected: `StudyViewModeTest` 3 tests PASS；`git diff --check` 无输出。

---

### Task 2: Apple UI 基础组件与浮动五 Tab 导航

**Files:**
- Create: `wordflip-android/core-ui/src/main/java/com/wordflip/core/ui/apple/AppleUi.kt`
- Create: `wordflip-android/app/src/main/java/com/wordflip/navigation/FloatingTabBar.kt`
- Modify: `wordflip-android/app/src/main/java/com/wordflip/navigation/MainScreen.kt:45-96`

**Interfaces:**
- Produces: `AppleUi.colors: AppleColors`
- Produces: `@Composable fun Modifier.applePress(interactionSource: MutableInteractionSource, enabled: Boolean = true): Modifier`
- Produces: `@Composable fun AppleGlassSurface(...)`
- Consumes: `MainTab.entries` 与现有 `onSelect: (MainTab) -> Unit`

- [ ] **Step 1: 创建 Apple 色彩、按压反馈和玻璃表面**

```kotlin
package com.wordflip.core.ui.apple

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppleColors(
    val accent: Color,
    val canvas: Color,
    val elevatedSurface: Color,
    val glass: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val separator: Color,
)

object AppleUi {
    val colors: AppleColors
        @Composable get() {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            return if (dark) {
                AppleColors(
                    accent = Color(0xFF0A84FF), canvas = Color(0xFF000000),
                    elevatedSurface = Color(0xFF1C1C1E), glass = Color(0xE629292B),
                    primaryText = Color(0xFFF5F5F7), secondaryText = Color(0xFFAEAEB2),
                    separator = Color(0x3D8E8E93),
                )
            } else {
                AppleColors(
                    accent = Color(0xFF007AFF), canvas = Color(0xFFF2F2F7),
                    elevatedSurface = Color(0xFFFFFFFF), glass = Color(0xE6F8F8FA),
                    primaryText = Color(0xFF17171A), secondaryText = Color(0xFF6E6E73),
                    separator = Color(0x1F3C3C43),
                )
            }
        }
}

@Composable
fun AppleGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        shape = RoundedCornerShape(cornerRadius),
        color = AppleUi.colors.glass,
        shadowElevation = 14.dp,
        tonalElevation = 0.dp,
    ) { Box { content() } }
}

@Composable
fun Modifier.applePress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "applePressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
```

实现时补充 `import androidx.compose.ui.graphics.luminance`，并保持公开 API 与上面一致。

- [ ] **Step 2: 创建浮动导航组件**

```kotlin
@Composable
fun FloatingTabBar(
    selectedTab: MainTab?,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppleGlassSurface(modifier = modifier, cornerRadius = 26.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            MainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val interactions = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .applePress(interactions)
                        .clickable(
                            interactionSource = interactions,
                            indication = null,
                            onClick = { onSelect(tab) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) AppleUi.colors.accent else AppleUi.colors.secondaryText,
                    )
                    Text(
                        text = tab.label,
                        color = if (selected) AppleUi.colors.accent else AppleUi.colors.secondaryText,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 在 MainScreen 接入并保留原导航 lambda**

将原 `NavigationBar` 替换为：

```kotlin
val selectedTab = MainTab.entries.firstOrNull { it.route == currentRoute }

if (showBottomBar) {
    FloatingTabBar(
        selectedTab = selectedTab,
        onSelect = { tab ->
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
    )
}
```

删除 `NavigationBar`、`NavigationBarItem` 和旧 label 字重 import；不要改变 `showBottomBar` 与 `NavHost`。

- [ ] **Step 4: 编译并检查差异**

Run:

```powershell
.\gradlew.bat :core-ui:compileDebugKotlin :app:compileDebugKotlin
git diff --check -- core-ui app/src/main/java/com/wordflip/navigation
```

Expected: 两模块编译 PASS；其余四页源文件无内容 diff。

---

### Task 3: 今日页主卡逻辑与 Apple 首页

**Files:**
- Create: `wordflip-android/feature-today/src/main/java/com/wordflip/feature/today/TodayPresentation.kt`
- Create: `wordflip-android/feature-today/src/main/java/com/wordflip/feature/today/TodayAppleComponents.kt`
- Create: `wordflip-android/feature-today/src/test/java/com/wordflip/feature/today/TodayPresentationTest.kt`
- Modify: `wordflip-android/feature-today/src/main/java/com/wordflip/feature/today/TodayScreen.kt`
- Modify: `wordflip-android/feature-today/build.gradle.kts`

**Interfaces:**
- Produces: `sealed interface TodayPrimaryCard`
- Produces: `fun resolveTodayPrimaryCard(dashboard: TodayDashboard): TodayPrimaryCard`
- Consumes: 现有 `TodayViewModel.resolveRecentStudyNavigation`、`resolveTaskStudyNavigation`。

- [ ] **Step 1: 写主卡选择失败测试**

```kotlin
class TodayPresentationTest {
    @Test
    fun `推荐学习优先于最近分组`() {
        val dashboard = dashboard(
            recommended = RecommendedStudy(7, "推荐组", 24, StudyReason.NEW_WORDS),
            recent = listOf(RecentGroup(8, "最近组", "2026-07-18T08:00:00Z")),
        )
        assertTrue(resolveTodayPrimaryCard(dashboard) is TodayPrimaryCard.Recommended)
    }

    @Test
    fun `无推荐时使用第一条最近分组`() {
        val dashboard = dashboard(
            recommended = null,
            recent = listOf(RecentGroup(8, "最近组", "2026-07-18T08:00:00Z")),
        )
        assertEquals(8, (resolveTodayPrimaryCard(dashboard) as TodayPrimaryCard.Recent).group.groupId)
    }

    @Test
    fun `推荐与最近都为空时返回空态`() {
        assertEquals(TodayPrimaryCard.Empty, resolveTodayPrimaryCard(dashboard(null, emptyList())))
    }
}
```

测试文件内的 `dashboard` helper 必须构造完整 `TodayDashboard`，统计和任务使用零值对象。`feature-today` 增加 `testImplementation(libs.junit)`。

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
.\gradlew.bat :feature-today:testDebugUnitTest --tests "com.wordflip.feature.today.TodayPresentationTest"
```

Expected: FAIL，提示 `TodayPrimaryCard` 未定义。

- [ ] **Step 3: 实现主卡纯逻辑**

```kotlin
sealed interface TodayPrimaryCard {
    data class Recommended(val study: RecommendedStudy) : TodayPrimaryCard
    data class Recent(val group: RecentGroup) : TodayPrimaryCard
    data object Empty : TodayPrimaryCard
}

/** 主卡只做展示回退，不重新计算服务端推荐优先级。 */
fun resolveTodayPrimaryCard(dashboard: TodayDashboard): TodayPrimaryCard = when {
    dashboard.recommendedStudy != null -> TodayPrimaryCard.Recommended(dashboard.recommendedStudy)
    dashboard.recentGroups.isNotEmpty() -> TodayPrimaryCard.Recent(dashboard.recentGroups.first())
    else -> TodayPrimaryCard.Empty
}
```

- [ ] **Step 4: 创建今日展示组件**

`TodayAppleComponents.kt` 提供以下稳定接口：

```kotlin
@Composable fun TodayHeroCard(
    primary: TodayPrimaryCard,
    onStudyClick: (StudyNavigation) -> Unit,
    onRecentQuizClick: (Int, String) -> Unit,
    viewModel: TodayViewModel,
)

@Composable fun TodayMetricsRow(stats: TodayStats)

@Composable fun TodayTaskList(
    tasks: TodayTasks,
    subtitle: (TodayTask) -> String,
    onNewWords: () -> Unit,
    onDueReview: () -> Unit,
    onQuiz: () -> Unit,
)

@Composable fun TodayRecentGroups(
    groups: List<RecentGroup>,
    onStudy: (RecentGroup) -> Unit,
    onQuiz: (RecentGroup) -> Unit,
)

@Composable fun TodayAppleSkeleton()
```

所有可点击卡片使用独立 `MutableInteractionSource`、`applePress`、`indication = null`；计数与文案直接消费 DTO。

- [ ] **Step 5: 重写 TodayScreen 的三态编排**

保留两个 `LaunchedEffect` 与现有 Toast/静默刷新逻辑，将内容替换为：

```kotlin
when (val state = uiState) {
    TodayUiState.Loading -> TodayAppleSkeleton()
    is TodayUiState.Error -> TodayInlineError(
        message = state.message,
        onRetry = viewModel::loadDashboard,
    )
    is TodayUiState.Content -> TodayAppleContent(
        dashboard = state.dashboard,
        primary = resolveTodayPrimaryCard(state.dashboard),
        viewModel = viewModel,
        onNavigateToStudy = onNavigateToStudy,
        onNavigateToQuiz = onNavigateToQuiz,
        onNavigateToRecentQuiz = onNavigateToRecentQuiz,
        onNotificationClick = { toast.show("提醒功能即将上线") },
    )
}
```

`TodayAppleContent` 顺序固定为：问候/日期/streak → 主卡 → 三统计 → 今日任务 → 最近学习。保留最近组最多三条和学习/测验双入口。

- [ ] **Step 6: 运行测试和模块编译**

Run:

```powershell
.\gradlew.bat :feature-today:testDebugUnitTest --tests "com.wordflip.feature.today.TodayPresentationTest"
.\gradlew.bat :feature-today:compileDebugKotlin
git diff --check -- feature-today
```

Expected: 3 tests PASS；模块编译 PASS；无空白错误。

---

### Task 4: 三种学习布局与模式菜单

**Files:**
- Create: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyModePicker.kt`
- Create: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyLayouts.kt`
- Modify: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyScreen.kt`
- Modify: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyToolbar.kt`

**Interfaces:**
- Consumes: `StudyViewModePreferences.modeFlow` 与 `setMode`。
- Produces: `StudyModePicker(mode, onModeSelected)`。
- Produces: `StudyModeContent(mode, state, callbacks...)`。

- [ ] **Step 1: 创建源点明确的模式菜单**

```kotlin
@Composable
fun StudyModePicker(
    mode: StudyViewMode,
    onModeSelected: (StudyViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.ViewCarousel, contentDescription = "切换学习视图")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StudyViewMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        if (option == mode) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onModeSelected(option)
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 2: 创建三种布局统一接口**

```kotlin
@Composable
fun StudyModeContent(
    mode: StudyViewMode,
    state: StudyUiState.Content,
    reduceMotion: Boolean,
    onShuffle: (ShuffleViewportAnchor) -> Unit,
    onFlipAll: () -> Unit,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (mode) {
        StudyViewMode.FOCUS -> FocusStudyLayout(state, onCardClick, onCardLongClick, modifier)
        StudyViewMode.GRID -> GridStudyLayout(
            state, reduceMotion, onShuffle, onFlipAll, onCardClick, onCardLongClick, modifier,
        )
        StudyViewMode.HYBRID -> HybridStudyLayout(state, onCardClick, onCardLongClick, modifier)
    }
}
```

`FocusStudyLayout` 使用 `HorizontalPager(state = rememberPagerState(pageCount = { orderedWords.size }))`；`GridStudyLayout` 从当前 `StudyContent` 搬移打乱锚点逻辑并改为 `GridCells.Adaptive(150.dp)`；`HybridStudyLayout` 使用同一 pager 加 `LazyRow` 缩略轨道，缩略项点击 `pagerState.animateScrollToPage(index)`。

- [ ] **Step 3: 统一卡片渲染函数，避免三份参数漂移**

```kotlin
@Composable
private fun StudyWordCard(
    word: WordCard,
    flipped: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlipCard(
        en = word.en,
        cn = word.displayMeaning(),
        ph = word.ph,
        pos = word.pos,
        wordKey = word.wordKey,
        stainSeed = word.stain.seed,
        stainHidden = word.stain.hidden,
        stainConfig = word.stain.config,
        hasImage = word.image.hasImage,
        imageUrl = word.image.imageUrl,
        imageTransform = word.image.transform,
        imageFilters = word.image.filters,
        showCnOnImage = word.image.showCnOnImage,
        isFlipped = flipped,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionEnabled = enabled,
        modifier = modifier,
    )
}
```

- [ ] **Step 4: 在 StudyScreen 收集偏好并接入菜单**

```kotlin
val context = LocalContext.current
val viewModePreferences = remember(context) { StudyViewModePreferences(context.applicationContext) }
val viewMode by viewModePreferences.modeFlow.collectAsState(initial = StudyViewMode.HYBRID)
val scope = rememberCoroutineScope()

StudyModePicker(
    mode = viewMode,
    onModeSelected = { selected ->
        scope.launch { viewModePreferences.setMode(selected) }
    },
)
```

把 picker 放在学习页 TopBar 的测验按钮之前。原 `StudyContent(...)` 调用替换为 `StudyModeContent(...)`；详情、选图、编辑器、引导和 BackHandler 原样保留。

- [ ] **Step 5: 将 StudyToolbar 改为 Apple 浮动控制条**

保留函数签名和 `isShuffling` 禁用规则，只替换容器与按钮：使用 `AppleGlassSurface`、两个紧凑 `TextButton`、即时按压反馈，不改变 `onShuffle` / `onFlipAll`。

- [ ] **Step 6: 编译学习模块**

Run:

```powershell
.\gradlew.bat :feature-study:compileDebugKotlin
git diff --check -- feature-study
```

Expected: 编译 PASS；三种 mode 分支穷尽；无无关 `StudyViewModel` diff。

---

### Task 5: 翻卡交互与自动发音修正

**Files:**
- Create: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyInteraction.kt`
- Create: `wordflip-android/feature-study/src/test/java/com/wordflip/feature/study/StudyInteractionTest.kt`
- Modify: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyScreen.kt:160-177`
- Modify: `wordflip-android/core-ui/src/main/java/com/wordflip/core/ui/component/FlipCard.kt:68-374`

**Interfaces:**
- Produces: `fun shouldAutoSpeakAfterFlip(wasFlipped: Boolean, autoSpeakEnabled: Boolean): Boolean`
- Preserves: `FlipCard` public signature。

- [ ] **Step 1: 写自动发音失败测试**

```kotlin
class StudyInteractionTest {
    @Test fun `正面翻到背面且开关开启时发音`() {
        assertTrue(shouldAutoSpeakAfterFlip(wasFlipped = false, autoSpeakEnabled = true))
    }

    @Test fun `背面翻回正面时不发音`() {
        assertFalse(shouldAutoSpeakAfterFlip(wasFlipped = true, autoSpeakEnabled = true))
    }

    @Test fun `关闭自动发音时不发音`() {
        assertFalse(shouldAutoSpeakAfterFlip(wasFlipped = false, autoSpeakEnabled = false))
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run:

```powershell
.\gradlew.bat :feature-study:testDebugUnitTest --tests "com.wordflip.feature.study.StudyInteractionTest"
```

Expected: FAIL，提示函数未定义。

- [ ] **Step 3: 实现判断并在点击前读取旧状态**

```kotlin
/** 只有正面翻到背面的提交动作才允许触发自动发音。 */
fun shouldAutoSpeakAfterFlip(
    wasFlipped: Boolean,
    autoSpeakEnabled: Boolean,
): Boolean = !wasFlipped && autoSpeakEnabled
```

点击回调改为：

```kotlin
val wasFlipped = state.flipStates[wordKey] == true
viewModel.toggleFlip(wordKey)
if (shouldAutoSpeakAfterFlip(wasFlipped, autoSpeak)) {
    viewModel.currentWord(wordKey)?.let { tts.speakForCard(it.en) }
}
```

- [ ] **Step 4: 将 FlipCard 改为即时按压与 spring 翻转**

将按压反馈延迟设为 `0L`，并使用：

```kotlin
val pressScale by animateFloatAsState(
    targetValue = if (isPressed) 0.97f else 1f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    ),
    label = "cardPressScale",
)

val rotation by animateFloatAsState(
    targetValue = if (isFlipped) 180f else 0f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    ),
    label = "flipRotation",
)
```

删除旧 `FlipEasing` 与 500ms tween。卡片仍以 `cameraDistance = 8f * density` 呈现 3D，长按提交时保留一次 `HapticFeedbackType.LongPress`。

- [ ] **Step 5: 运行测试和相关编译**

Run:

```powershell
.\gradlew.bat :feature-study:testDebugUnitTest --tests "com.wordflip.feature.study.StudyInteractionTest"
.\gradlew.bat :core-ui:compileDebugKotlin :feature-study:compileDebugKotlin
git diff --check -- core-ui feature-study
```

Expected: 3 tests PASS；相关模块编译 PASS。

---

### Task 6: 学习详情、引导与状态降级

**Files:**
- Modify: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyDetailSheet.kt`
- Modify: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyGuideOverlay.kt`
- Modify: `wordflip-android/feature-study/src/main/java/com/wordflip/feature/study/StudyScreen.kt`

**Interfaces:**
- Preserves: `StudyDetailSheet` 全部现有回调参数。
- Preserves: `StudyGuideOverlay(visible, onDismiss)`。

- [ ] **Step 1: 只替换详情 Sheet 的展示层**

保持现有 `word`、来源、TTS、图片和污渍回调不变。根容器使用 `AppleUi.colors.elevatedSurface`，标题层级改为系统大标题，section 之间使用 `AppleUi.colors.separator`，所有 icon button 保持至少 48dp。

关键容器：

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
) {
    Box(
        Modifier
            .width(36.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(AppleUi.colors.separator)
            .align(Alignment.CenterHorizontally),
    )
    Text(word.en, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    word.ph?.let { Text(it, color = AppleUi.colors.secondaryText) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onRateDown) { Text("−") }
        Text("${"%.1f".format(speechRate)}x")
        OutlinedButton(onClick = onRateUp) { Text("+") }
        Button(onClick = onSpeak) {
            Icon(Icons.Outlined.VolumeUp, contentDescription = null)
            Text(if (isDetailSpeaking) "播放中" else "朗读")
        }
    }
    HorizontalDivider(color = AppleUi.colors.separator)
    Text("本书考义", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SenseSection(word.sensesForDetail())
    word.sourceMaterials.forEach { source ->
        HorizontalDivider(color = AppleUi.colors.separator)
        Text(source.sourceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(source.revision.takeIf { it.isNotBlank() }, source.licenseNote).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = AppleUi.colors.secondaryText,
        )
        SenseSection(source.senses)
    }
    HorizontalDivider(color = AppleUi.colors.separator)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onChangeStain(word.wordKey, StainType.entries) }) { Text("换污渍") }
        OutlinedButton(onClick = { onToggleStainVisibility(word.wordKey) }) {
            Text(if (word.stain.hidden) "显示污渍" else "隐藏污渍")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onTakePhoto(word.wordKey) }) { Text("拍照") }
        OutlinedButton(onClick = { onPickGallery(word.wordKey) }) { Text("相册") }
        if (word.image.hasImage) {
            OutlinedButton(onClick = { onEditPhoto(word.wordKey) }) { Text("编辑") }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("图片显示中文", modifier = Modifier.weight(1f))
        Switch(
            checked = word.image.showCnOnImage,
            enabled = word.image.hasImage,
            onCheckedChange = { onToggleShowCnOnImage(word.wordKey) },
        )
    }
}
```

- [ ] **Step 2: 重绘首次引导但不改持久化**

`StudyGuideOverlay` 使用半透明 scrim、居中的实色 28dp 圆角卡和单一蓝色按钮；`onDismiss` 继续调用现有 `StudyGuidePreferences.dismissGuide()` 路径。

- [ ] **Step 3: 加入减少动态效果与空学习组分支**

复用现有 `reduceMotion`：Focus/Hybrid pager 在减少动态效果时禁用额外缩放和旁卡位移，只保留 pager 默认定位；空 `orderedWords` 时显示：

```kotlin
StudyEmptyState(
    title = "这个分组还没有学习卡",
    message = "返回分组后添加已发布学习卡再继续。",
    onNavigateBack = handleNavigateBack,
)
```

- [ ] **Step 4: 编译并检查脏文件差异**

Run:

```powershell
.\gradlew.bat :feature-study:compileDebugKotlin
git diff --check -- feature-study/src/main/java/com/wordflip/feature/study
git diff -- feature-study/src/main/java/com/wordflip/feature/study/StudyDetailSheet.kt
```

Expected: 编译 PASS；详情 diff 只含视觉结构，保留当前未提交的 v7 `sourceMaterials` 行为。

---

### Task 7: 全量验证与 APK 交付检查

**Files:**
- Verify only; do not edit unrelated modules.

**Interfaces:**
- Consumes: Tasks 1–6 的全部产物。
- Produces: 可安装 Debug APK 与验证记录。

- [ ] **Step 1: 运行定向单测**

Run:

```powershell
cd wordflip-android
.\gradlew.bat :feature-today:testDebugUnitTest :feature-study:testDebugUnitTest
```

Expected: 新增 9 个纯逻辑测试及现有 Android 单测全部 PASS。

- [ ] **Step 2: 运行模块编译**

Run:

```powershell
.\gradlew.bat :core-ui:compileDebugKotlin :feature-today:compileDebugKotlin :feature-study:compileDebugKotlin :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 组装 Debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: `wordflip-android/app/build/outputs/apk/debug/app-debug.apk` 存在。

- [ ] **Step 4: 检查业务禁区与差异边界**

Run:

```powershell
git diff --check
git diff --name-only
rg -n "mastery|stability|dueAt|review_events" feature-today feature-study app/src/main/java/com/wordflip/navigation core-ui/src/main/java/com/wordflip/core/ui/apple
```

Expected: 无空白错误；新增 UI 代码不写 mastery/FSRS；设置、词书、分组、统计页面内容文件没有本轮 diff。

- [ ] **Step 5: 手工验收清单**

在模拟器或真机逐项确认：

1. 五 Tab 浮动导航切换、选中态、系统安全区与子页隐藏正确。
2. 今日页主卡按推荐 → 最近 → 空态回退；任务和最近组学习/测验入口正确。
3. 三种学习模式即时切换，杀进程重启后恢复最后模式。
4. 三种模式共用翻转状态；打乱后状态不丢失。
5. 只有正面翻到背面触发自动发音。
6. 长按详情、选图、编辑器与返回优先级无回归。
7. Light/Dark、字体放大、窄屏、横屏和减少动态效果可用。
8. 仅翻卡离开后 FSRS/掌握度不变。

- [ ] **Step 6: 最终工作树报告（不提交）**

Run:

```powershell
git status --short
git diff --stat -- wordflip-android docs/superpowers
```

Expected: 清楚区分本轮 UI 文件与用户原有 v7/FSRS 改动；不执行 commit、push 或 reset。
