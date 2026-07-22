package com.wordflip.feature.quiz

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.quiz.QuizFeedbackType
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.emoji
import com.wordflip.core.model.quiz.label
import com.wordflip.core.ui.apple.AppleGroupedSurface
import com.wordflip.core.ui.apple.ApplePrimaryAction
import com.wordflip.core.ui.apple.AppleUi
import com.wordflip.core.ui.apple.applePress
import com.wordflip.core.ui.component.FlowingSyllableWord
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.theme.WordFlipColors

/**
 * 默写测验页（REQ-QUIZ-1~10）；每次进入强制新建 session（REQ-NAV-6）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    source: QuizSource,
    groupId: Int?,
    viewModelKey: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuizViewModel = hiltViewModel(key = viewModelKey),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppleUi.colors.canvas,
        topBar = {
            WordFlipTopBar(
                title = "测验",
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            QuizUiState.Loading -> QuizLoading(modifier = Modifier.padding(innerPadding))
            is QuizUiState.Error -> NetworkErrorView(
                message = state.message,
                onRetry = viewModel::startSession,
                modifier = Modifier.padding(innerPadding),
            )
            is QuizUiState.Question -> QuizQuestionContent(
                state = state,
                onAnswerChange = viewModel::onAnswerChange,
                onSubmit = viewModel::submitAnswer,
                onSubmitChoice = viewModel::submitChoice,
                onToggleHintCovered = viewModel::toggleHintCovered,
                onNextQuestion = viewModel::goToNextQuestion,
                modifier = Modifier.padding(innerPadding),
            )
            is QuizUiState.Result -> QuizResultContent(
                result = state.data,
                onRetry = viewModel::startSession,
                onBack = onNavigateBack,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun QuizLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AppleUi.colors.accent)
        Text(
            text = "正在抽题…",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = AppleUi.colors.secondaryText,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizQuestionContent(
    state: QuizUiState.Question,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSubmitChoice: (String) -> Unit,
    onToggleHintCovered: () -> Unit,
    onNextQuestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val tts = remember { QuizTtsHelper(context) }
    val isSpeaking by tts.isSpeaking.collectAsState()
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    val scrollState = rememberScrollState()
    val isCorrect = state.feedback?.type == QuizFeedbackType.CORRECT
    val practicePassed = state.practicePassed

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    // 换题时重置语速
    LaunchedEffect(state.currentIndex) {
        tts.resetRate()
        speechRate = tts.rate
    }

    // 答错进入巩固：自动朗读一次正确答案
    LaunchedEffect(state.currentIndex, state.consolidationActive) {
        if (state.consolidationActive) {
            delay(300)
            tts.speak(state.question.expectedEn)
        }
    }

    LaunchedEffect(state.currentIndex, state.inputEnabled, state.question.isChoice) {
        // 选择题不抢焦点；默写/巩固才弹出键盘
        if (
            state.totalQuestions <= 0 ||
            !state.inputEnabled ||
            (state.question.isChoice && !state.consolidationActive)
        ) {
            return@LaunchedEffect
        }
        delay(120)
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (_: IllegalStateException) {
            // 输入框尚未进入组合树，忽略
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleUi.colors.canvas),
    ) {
        // 紧凑进度始终在题干上方，不与得分或答题控件争夺注意力。
        QuizProgressHeader(
            current = state.currentIndex + 1,
            total = state.totalQuestions,
            score = state.score,
            progress = state.progress,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        // 题干和纠正信息占据中央可滚动区，长文本不会挤出底部主操作。
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.totalQuestions <= 0) {
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "暂无可用题目",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppleUi.colors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "请返回后重新开始测验",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppleUi.colors.secondaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                QuizWordHeader(
                    state = state,
                    isSpeaking = isSpeaking,
                    speechRate = speechRate,
                    onToggleHintCovered = onToggleHintCovered,
                    onSpeakAnswer = { tts.speak(state.question.expectedEn) },
                    onRateDown = {
                        tts.adjustRate(-0.25f)
                        speechRate = tts.rate
                    },
                    onRateUp = {
                        tts.adjustRate(0.25f)
                        speechRate = tts.rate
                    },
                )
                if (state.consolidationActive) {
                    Spacer(modifier = Modifier.height(14.dp))
                    QuizHintSection(state = state)
                }
            }
        }

        // 答题区固定在底部安全区；IME 弹出后随键盘上移，保留原有提交时机。
        if (state.totalQuestions > 0) {
            QuizAnswerDock(
                state = state,
                focusRequester = focusRequester,
                practicePassed = practicePassed,
                isCorrect = isCorrect,
                onAnswerChange = onAnswerChange,
                onSubmit = onSubmit,
                onSubmitChoice = onSubmitChoice,
                onNextQuestion = onNextQuestion,
            )
        }
    }
}

/** 底部答题层：只承载当前一步的主操作。 */
@Composable
private fun QuizAnswerDock(
    state: QuizUiState.Question,
    focusRequester: FocusRequester,
    practicePassed: Boolean,
    isCorrect: Boolean,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSubmitChoice: (String) -> Unit,
    onNextQuestion: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = AppleUi.colors.glass,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            if (state.question.isChoice && !state.consolidationActive) {
                QuizChoiceSection(
                    state = state,
                    isCorrect = isCorrect,
                    onSubmitChoice = onSubmitChoice,
                    onNextQuestion = onNextQuestion,
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                QuizInputSection(
                    state = state,
                    focusRequester = focusRequester,
                    practicePassed = practicePassed,
                    isCorrect = isCorrect,
                    onAnswerChange = onAnswerChange,
                    onSubmit = onSubmit,
                    onNextQuestion = onNextQuestion,
                )
            }
        }
    }
}

/**
 * 顶部题干区：dictation / choice_cn_en 展示中文；choice_en_cn 展示英文；
 * 巩固阶段展示英文，盖住后在同位置换显中文。
 */
@Composable
private fun QuizWordHeader(
    state: QuizUiState.Question,
    isSpeaking: Boolean,
    speechRate: Float,
    onToggleHintCovered: () -> Unit,
    onSpeakAnswer: () -> Unit,
    onRateDown: () -> Unit,
    onRateUp: () -> Unit,
) {
    val colors = AppleUi.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (state.consolidationActive) {
            WordFlipColors.extra.successContainer
        } else {
            Color.Transparent
        },
        tonalElevation = 0.dp,
    ) {
        if (state.consolidationActive) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "正确答案",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.secondaryText,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.hintPanelCovered,
                        onClick = onToggleHintCovered,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        label = { Text(if (state.hintPanelCovered) "显示单词" else "盖住单词") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.hintPanelCovered) {
                                    Icons.Outlined.Visibility
                                } else {
                                    Icons.Outlined.VisibilityOff
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
                // 英选中答错：优先展示中文正确答案，避免用户以为「答案是另一个英文」
                val type = state.question.type.lowercase()
                val displayAnswerText = state.feedback?.expectedAnswer
                    ?.takeIf { !it.isNullOrBlank() }
                    ?: state.question.prompt.cn ?: ""
                if (type == "choice_en_cn" && !displayAnswerText.isNullOrBlank()) {
                    Text(
                        text = displayAnswerText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = WordFlipColors.extra.success,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.hintPanelCovered) {
                    // 盖住英文：原单词位显示中文释义，便于对照默写
                    Text(
                        text = preferChinesePrompt(state.question.prompt.cn),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PosPhRow(
                        pos = state.question.prompt.pos,
                        ph = state.question.prompt.ph,
                    )
                } else {
                    FlowingSyllableWord(
                        word = state.question.expectedEn,
                        isSpeaking = isSpeaking,
                        speechRate = speechRate,
                        animateFlow = true,
                    )
                    PosPhRow(
                        pos = state.question.prompt.pos,
                        ph = state.question.prompt.ph,
                    )
                }
                QuizSpeakControls(
                    speechRate = speechRate,
                    isSpeaking = isSpeaking,
                    onRateDown = onRateDown,
                    onRateUp = onRateUp,
                    onSpeak = onSpeakAnswer,
                )
            }
        } else {
            // REQ-QUIZ-11：英选中=英文题干+中文选项；中选英=中文题干+英文选项
            val type = state.question.type.lowercase()
            val promptText = when (type) {
                "choice_en_cn" -> state.question.prompt.en?.takeIf { it.isNotBlank() }
                    ?: state.question.expectedEn.takeIf { it.isNotBlank() }
                    ?: ""
                // 词书 cn 常夹带英文搭配（如 favour (prep.)；为收款人），从首个汉字起展示，避免「英文选英文」观感
                "choice_cn_en" -> preferChinesePrompt(state.question.prompt.cn)
                else -> preferChinesePrompt(state.question.prompt.cn)
            }
            val hint = when (type) {
                "choice_en_cn" -> "选出正确的中文释义"
                "choice_cn_en" -> "选出正确的英文单词"
                else -> null
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.secondaryText,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText,
                    textAlign = TextAlign.Center,
                )
                // 英选中才展示音标；中选英题干为中文，音标易造成「英文题干」错觉
                if (type == "choice_en_cn" || type == "dictation") {
                    PosPhRow(
                        pos = state.question.prompt.pos,
                        ph = state.question.prompt.ph,
                    )
                }
            }
        }
    }
}

/** 选择题：整块选项点击后立即提交 selectedKey，不改变原有答案时机。 */
@Composable
private fun QuizChoiceSection(
    state: QuizUiState.Question,
    isCorrect: Boolean,
    onSubmitChoice: (String) -> Unit,
    onNextQuestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = state.question.options.orEmpty()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (options.isEmpty()) {
            AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "暂无可用选项",
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppleUi.colors.secondaryText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            options.forEachIndexed { index, option ->
                QuizChoiceOption(
                    label = option.label,
                    index = index,
                    selected = state.userAnswer == option.key,
                    correct = isCorrect && state.userAnswer == option.key,
                    enabled = state.inputEnabled,
                    onClick = { onSubmitChoice(option.key) },
                )
            }
        }

        when {
            isCorrect -> {
                QuizInlineFeedback(
                    text = state.feedback?.message ?: "正确！",
                    success = true,
                )
                ApplePrimaryAction(
                    text = "下一题",
                    onClick = onNextQuestion,
                )
            }
            !state.inputEnabled -> {
                Text(
                    text = "正在提交答案…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleUi.colors.secondaryText,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 单个答案的整块触控面，选中、正确和禁用状态均保留语义反馈。 */
@Composable
private fun QuizChoiceOption(
    label: String,
    index: Int,
    selected: Boolean,
    correct: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppleUi.colors
    val interactions = remember { MutableInteractionSource() }
    val containerColor = when {
        correct -> WordFlipColors.extra.successContainer
        selected -> colors.accent.copy(alpha = 0.14f)
        else -> colors.groupedSurface
    }
    val contentColor = when {
        correct -> WordFlipColors.extra.success
        selected -> colors.accent
        enabled -> colors.primaryText
        else -> colors.secondaryText.copy(alpha = 0.62f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .applePress(interactions, enabled)
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                interactionSource = interactions,
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = if (selected || correct) 1.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
            ) {
                Box(
                    modifier = Modifier.size(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ('A'.code + index).toChar().toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                fontWeight = if (selected || correct) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 词性与音标同一行展示 */
@Composable
private fun PosPhRow(
    pos: String?,
    ph: String?,
    modifier: Modifier = Modifier,
) {
    if (pos.isNullOrBlank() && ph.isNullOrBlank()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!pos.isNullOrBlank()) {
            Text(
                text = pos,
                style = MaterialTheme.typography.labelLarge,
                color = AppleUi.colors.accent,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!pos.isNullOrBlank() && !ph.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (!ph.isNullOrBlank()) {
            Text(
                text = ph,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = AppleUi.colors.secondaryText,
            )
        }
    }
}

/** 朗读语速调节 + 脉冲朗读按钮（与学习详情抽屉一致） */
@Composable
private fun QuizSpeakControls(
    speechRate: Float,
    isSpeaking: Boolean,
    onRateDown: () -> Unit,
    onRateUp: () -> Unit,
    onSpeak: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onRateDown,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text("−") }
        Text(
            text = "${"%.1f".format(speechRate)}x",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OutlinedButton(
            onClick = onRateUp,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) { Text("+") }
        val infiniteTransition = rememberInfiniteTransition(label = "quizSpeakPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseScale",
        )
        FilledIconButton(
            onClick = onSpeak,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(48.dp)
                .scale(if (isSpeaking) pulseScale else 1f),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = AppleUi.colors.accent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = "朗读",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 中部输入区：输入框 + 确认 / 巩固操作按钮 */
@Composable
private fun QuizInputSection(
    state: QuizUiState.Question,
    focusRequester: FocusRequester,
    practicePassed: Boolean,
    isCorrect: Boolean,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onNextQuestion: () -> Unit,
) {
    // 只在巩固再次答错后标记错误，避免刚进入练习就显示负面反馈。
    val inputIsError = state.consolidationActive && state.practiceHint == "再试一次"

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.userAnswer,
            onValueChange = onAnswerChange,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp)
                .focusRequester(focusRequester),
            enabled = state.inputEnabled,
            isError = inputIsError,
            label = {
                Text(if (state.consolidationActive) "再写一遍" else "英文单词")
            },
            placeholder = {
                Text(if (state.consolidationActive) "默写英文单词" else "输入英文单词")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (state.inputEnabled && state.userAnswer.isNotBlank()) onSubmit()
            }),
        )

        if (state.consolidationActive) {
            QuizInlineFeedback(
                text = when {
                    practicePassed -> state.practiceHint.orEmpty()
                    inputIsError -> state.practiceHint.orEmpty()
                    else -> "正确后即可继续"
                },
                success = practicePassed,
                subdued = !practicePassed && !inputIsError,
            )
            ApplePrimaryAction(
                text = if (practicePassed) "再检查一次" else "检查练习",
                onClick = onSubmit,
                enabled = state.userAnswer.isNotBlank(),
            )
            OutlinedButton(
                onClick = onNextQuestion,
                enabled = practicePassed,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppleUi.colors.accent,
                ),
            ) {
                Text("下一题")
            }
        } else if (state.inputEnabled) {
            ApplePrimaryAction(
                text = "确认答案",
                onClick = onSubmit,
                enabled = state.userAnswer.isNotBlank(),
            )
        } else if (isCorrect) {
            QuizInlineFeedback(
                text = state.feedback?.message ?: "✓ 正确！",
                success = true,
            )
            ApplePrimaryAction(text = "下一题", onClick = onNextQuestion)
        } else {
            Text(
                text = "正在提交答案…",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleUi.colors.secondaryText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 就地呈现提交或巩固反馈，不生成新的业务状态。 */
@Composable
private fun QuizInlineFeedback(
    text: String,
    success: Boolean,
    subdued: Boolean = false,
) {
    if (text.isBlank()) return
    val colors = AppleUi.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = when {
            subdued -> colors.elevatedSurface
            success -> WordFlipColors.extra.successContainer
            else -> MaterialTheme.colorScheme.errorContainer
        },
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    subdued -> colors.secondaryText
                    success -> WordFlipColors.extra.success
                    else -> colors.destructive
                },
                fontWeight = if (subdued) FontWeight.Normal else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 底部提示区：仅答错巩固阶段展示 */
@Composable
private fun QuizHintSection(
    state: QuizUiState.Question,
) {
    val detail = state.question.detail

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "需要巩固",
                style = MaterialTheme.typography.titleMedium,
                color = AppleUi.colors.destructive,
                fontWeight = FontWeight.Bold,
            )
            state.feedback?.message?.let { message ->
                HintLine(
                    text = message,
                    color = AppleUi.colors.destructive,
                    bold = true,
                )
            }
            state.wrongAttemptAnswer?.let { wrong ->
                val prefix = if (state.question.isChoice) "你选了：" else "你写了："
                HintLine(text = prefix + wrong)
            }
            state.feedback?.expectedAnswer?.takeIf { it.isNotBlank() }?.let { answer ->
                HintLine(
                    text = "正确答案：$answer",
                    color = WordFlipColors.extra.success,
                    bold = true,
                )
            }

            if (detail != null) {
                DetailSection(title = "词义", body = detail.meaning)
                if (detail.examples.isNotEmpty()) {
                    DetailSection(
                        title = "例句",
                        body = detail.examples.filter { it.isNotBlank() && it != "暂无更多例句" }
                            .joinToString("\n") { "· $it" }
                            .ifBlank { detail.examples.firstOrNull().orEmpty() },
                    )
                }
                detail.etymology?.takeIf { it.isNotBlank() }?.let { etymology ->
                    DetailSection(title = "词根", body = etymology)
                }
            }
        }
    }
}

@Composable
private fun HintLine(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bold: Boolean = false,
    monospace: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        color = color,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DetailSection(title: String, body: String) {
    if (body.isBlank()) return
    Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 将进度与得分压缩为辅助信息，保留中央题干的唯一主焦点。 */
@Composable
private fun QuizProgressHeader(
    current: Int,
    total: Int,
    score: Int,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (total > 0) "第 $current 题 · 共 $total 题" else "暂无题目",
                style = MaterialTheme.typography.labelLarge,
                color = AppleUi.colors.secondaryText,
            )
            Text(
                text = "$score 分",
                style = MaterialTheme.typography.labelLarge,
                color = AppleUi.colors.primaryText,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = AppleUi.colors.accent,
            trackColor = AppleUi.colors.separator,
        )
    }
}

@Composable
private fun QuizResultContent(
    result: com.wordflip.core.model.quiz.QuizResultData,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accuracyText = "${"%.0f".format(result.accuracy * 100)}%"
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppleUi.colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(text = result.rating.emoji(), fontSize = 44.sp)
        Text(
            text = result.rating.label(),
            style = MaterialTheme.typography.headlineMedium,
            color = AppleUi.colors.primaryText,
            fontWeight = FontWeight.Bold,
        )
        AppleGroupedSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "正确率",
                    style = MaterialTheme.typography.labelLarge,
                    color = AppleUi.colors.secondaryText,
                )
                Text(
                    text = accuracyText,
                    style = MaterialTheme.typography.displayMedium,
                    color = WordFlipColors.extra.success,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ResultStat(
                        label = "答对",
                        value = "${result.correctCount} 题",
                        valueColor = WordFlipColors.extra.success,
                        modifier = Modifier.weight(1f),
                    )
                    ResultStat(
                        label = "答错",
                        value = "${result.wrongCount} 题",
                        valueColor = AppleUi.colors.destructive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (result.wrongCards.isNotEmpty()) {
            Text(
                text = "错题列表",
                style = MaterialTheme.typography.titleLarge,
                color = AppleUi.colors.primaryText,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            result.wrongCards.forEach { wrong ->
                AppleGroupedSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = wrong.en,
                            style = MaterialTheme.typography.titleMedium,
                            color = AppleUi.colors.primaryText,
                            fontWeight = FontWeight.SemiBold,
                        )
                        wrong.cn?.takeIf { it.isNotBlank() }?.let { meaning ->
                            Text(
                                text = meaning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppleUi.colors.secondaryText,
                            )
                        }
                        Text(
                            text = "你的答案：${wrong.userAnswer}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppleUi.colors.destructive,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        ApplePrimaryAction(text = "再来一次", onClick = onRetry)
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppleUi.colors.accent),
        ) {
            Text("返回")
        }
    }
}

@Composable
private fun ResultStat(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = AppleUi.colors.secondaryText,
        )
    }
}

/** 解析导航参数字符串为 QuizSource */
fun parseQuizSource(value: String): QuizSource {
    return when (value.lowercase()) {
        "study" -> QuizSource.STUDY
        "retry" -> QuizSource.RETRY
        "groups" -> QuizSource.GROUPS
        "all" -> QuizSource.ALL
        "recent" -> QuizSource.RECENT
        else -> QuizSource.TODAY
    }
}

/**
 * 词书 cn 常以英文搭配开头（如 `favour (prep.)；为收款人`）。
 * 中选英题干从首个汉字截取，避免题干看起来像英文、再配英文选项形成「英文选英文」。
 */
internal fun preferChinesePrompt(cn: String?): String {
    if (cn.isNullOrBlank()) return cn ?: ""
    val index = cn.indexOfFirst { it in '\u4e00'..'\u9fff' }
    return if (index > 0) cn.substring(index) else cn
}
