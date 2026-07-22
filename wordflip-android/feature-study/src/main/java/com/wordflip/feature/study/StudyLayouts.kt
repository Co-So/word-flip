package com.wordflip.feature.study

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.ui.component.FlipCard
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 学习页三种布局的统一入口；切换布局不复制或改写学习状态。
 */
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
        StudyViewMode.FOCUS -> FocusStudyLayout(
            state = state,
            onCardClick = onCardClick,
            onCardLongClick = onCardLongClick,
            modifier = modifier,
        )
        StudyViewMode.GRID -> GridStudyLayout(
            state = state,
            reduceMotion = reduceMotion,
            onShuffle = onShuffle,
            onFlipAll = onFlipAll,
            onCardClick = onCardClick,
            onCardLongClick = onCardLongClick,
            modifier = modifier,
        )
        StudyViewMode.HYBRID -> HybridStudyLayout(
            state = state,
            onCardClick = onCardClick,
            onCardLongClick = onCardLongClick,
            modifier = modifier,
        )
    }
}

/** 专注模式：一次呈现一张可水平翻页的学习卡。 */
@Composable
private fun FocusStudyLayout(
    state: StudyUiState.Content,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { state.orderedWords.size })
    StudyCardPager(
        state = state,
        pagerState = pagerState,
        onCardClick = onCardClick,
        onCardLongClick = onCardLongClick,
        modifier = modifier.fillMaxSize(),
    )
}

/** 卡片墙模式：保留打乱视口锚点和全翻操作。 */
@Composable
private fun GridStudyLayout(
    state: StudyUiState.Content,
    reduceMotion: Boolean,
    onShuffle: (ShuffleViewportAnchor) -> Unit,
    onFlipAll: () -> Unit,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    val horizontalPadding = 16.dp
    val verticalPadding = 8.dp
    val gap = 12.dp
    val minimumCardWidth = 150.dp

    // 打乱动画结束后回到顶部，让新顺序从第一屏开始呈现。
    LaunchedEffect(state.isShuffling, state.shuffleSettling) {
        if (!state.isShuffling && !state.shuffleSettling && state.shuffleEpoch > 0) {
            gridState.animateScrollToItem(index = 0)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false },
    ) {
        val horizontalPaddingPx = with(density) { horizontalPadding.roundToPx() }
        val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
        val gapPx = with(density) { gap.roundToPx() }
        val minimumCardWidthPx = with(density) { minimumCardWidth.roundToPx() }
        val contentWidthPx = (constraints.maxWidth - horizontalPaddingPx * 2).coerceAtLeast(0)
        val columnCount = max(
            1,
            (contentWidthPx + gapPx) / (minimumCardWidthPx + gapPx),
        )
        val cardWidthPx = (
            contentWidthPx - gapPx * (columnCount - 1)
        ).coerceAtLeast(0) / columnCount
        val cardHeightPx = (cardWidthPx * 4.2f / 3f).roundToInt()
        val gridSpec = remember(cardWidthPx, cardHeightPx, gapPx, columnCount) {
            ShuffleGridSpec(
                cardWidthPx = cardWidthPx.toFloat(),
                cardHeightPx = cardHeightPx.toFloat(),
                gapPx = gapPx.toFloat(),
                columnCount = columnCount,
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StudyToolbar(
                allFlippedToBack = state.allFlippedToBack,
                isShuffling = state.isShuffling,
                onShuffle = {
                    val layoutInfo = gridState.layoutInfo
                    val measuredByWordKey = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                        val word = state.orderedWords.getOrNull(item.index) ?: return@mapNotNull null
                        word.wordKey to (
                            item.offset.x + item.size.width / 2f to
                                item.offset.y + item.size.height / 2f
                            )
                    }.toMap()
                    val base = ShuffleViewportAnchor(
                        centerXPx = layoutInfo.viewportSize.width / 2f,
                        centerYPx = layoutInfo.viewportStartOffset.toFloat() +
                            layoutInfo.viewportSize.height / 2f,
                        contentPaddingLeftPx = horizontalPaddingPx.toFloat(),
                        contentPaddingTopPx = verticalPaddingPx.toFloat(),
                        viewportStartOffsetPx = layoutInfo.viewportStartOffset,
                        measuredCentersByWordKey = measuredByWordKey,
                    )
                    val anchor = base.copy(
                        centersByIndexAtStart = ShuffleMotion.precomputeCentersByIndex(
                            cardCount = state.orderedWords.size,
                            spec = gridSpec,
                            anchor = base,
                        ),
                    )
                    onShuffle(anchor)
                },
                onFlipAll = onFlipAll,
            )
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = minimumCardWidth),
                contentPadding = PaddingValues(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { clip = false },
                userScrollEnabled = !state.isShuffling,
            ) {
                itemsIndexed(
                    items = state.orderedWords,
                    key = { _, word -> word.wordKey },
                ) { index, word ->
                    Box(
                        modifier = Modifier.shuffleCardMotion(
                            wordKey = word.wordKey,
                            phase = state.shufflePhase,
                            shuffleEpoch = state.shuffleEpoch,
                            index = index,
                            motion = state.shuffleMotions[word.wordKey],
                            gridSpec = gridSpec,
                            viewportAnchor = state.shuffleViewportAnchor,
                            shuffleSettling = state.shuffleSettling,
                            reduceMotion = reduceMotion,
                            dealStartOffset = state.shuffleDealStartOffsets[word.wordKey],
                        ),
                    ) {
                        StudyWordCard(
                            word = word,
                            flipped = state.flipStates[word.wordKey] == true,
                            enabled = !state.isShuffling,
                            onClick = { onCardClick(word.wordKey) },
                            onLongClick = { onCardLongClick(word.wordKey) },
                        )
                    }
                }
            }
        }
    }
}

/** 混合模式：大卡翻页与可直接跳转的缩略卡轨道。 */
@Composable
private fun HybridStudyLayout(
    state: StudyUiState.Content,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { state.orderedWords.size })
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        StudyCardPager(
            state = state,
            pagerState = pagerState,
            onCardClick = onCardClick,
            onCardLongClick = onCardLongClick,
            modifier = Modifier.weight(1f),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            userScrollEnabled = !state.isShuffling,
        ) {
            itemsIndexed(
                items = state.orderedWords,
                key = { _, word -> word.wordKey },
            ) { index, word ->
                val selected = index == pagerState.currentPage
                StudyWordCard(
                    word = word,
                    flipped = state.flipStates[word.wordKey] == true,
                    enabled = !state.isShuffling,
                    onClick = {
                        scope.launch {
                            val pageCount = pagerState.pageCount
                            if (index in 0 until pageCount) {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    },
                    onLongClick = { onCardLongClick(word.wordKey) },
                    modifier = Modifier
                        .width(72.dp)
                        .border(
                            width = 2.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(3.dp),
                )
            }
        }
    }
}

/** 专注和混合模式共用的大卡翻页。 */
@Composable
private fun StudyCardPager(
    state: StudyUiState.Content,
    pagerState: PagerState,
    onCardClick: (String) -> Unit,
    onCardLongClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 20.dp),
        pageSpacing = 16.dp,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = !state.isShuffling,
        key = { page -> state.orderedWords[page].wordKey },
    ) { page ->
        val word = state.orderedWords[page]
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            StudyWordCard(
                word = word,
                flipped = state.flipStates[word.wordKey] == true,
                enabled = !state.isShuffling,
                onClick = { onCardClick(word.wordKey) },
                onLongClick = { onCardLongClick(word.wordKey) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 三种布局共用的完整卡片参数映射，避免释义、图片或污渍展示漂移。 */
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
