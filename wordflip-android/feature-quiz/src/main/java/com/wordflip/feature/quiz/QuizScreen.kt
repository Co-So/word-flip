package com.wordflip.feature.quiz

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wordflip.core.model.fake.FakeQuizData
import com.wordflip.core.model.quiz.QuizFeedbackType
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.emoji
import com.wordflip.core.model.quiz.label
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
        topBar = {
            WordFlipTopBar(
                title = "默写测验",
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
        CircularProgressIndicator()
        Text(
            text = "正在抽题…",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
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
    val practiceWrong = state.practiceHint == "再试一次"
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
        if (!state.inputEnabled || (state.question.isChoice && !state.consolidationActive)) {
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
        )
        RowMeta(
            current = state.currentIndex + 1,
            total = state.totalQuestions,
            score = state.score,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ① 顶部题干：dictation/中选英看中文；英选中看英文（REQ-QUIZ-11）
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

        Spacer(modifier = Modifier.height(12.dp))

        // ② 中部：选择题 4 选项 / 默写输入框
        if (state.question.isChoice && !state.consolidationActive) {
            QuizChoiceSection(
                state = state,
                isCorrect = isCorrect,
                onSubmitChoice = onSubmitChoice,
                onNextQuestion = onNextQuestion,
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

        Spacer(modifier = Modifier.height(12.dp))

        // ③ 底部：答错巩固时才显示提示信息
        if (state.consolidationActive) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuizHintSection(
                    state = state,
                    practiceWrong = practiceWrong,
                    practicePassed = practicePassed,
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (state.consolidationActive) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "正确答案",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.hintPanelCovered,
                        onClick = onToggleHintCovered,
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
                    ?.takeIf { it.isNotBlank() }
                    ?: state.question.prompt.cn
                if (type == "choice_en_cn" && displayAnswerText.isNotBlank()) {
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
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
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
                    ?: state.question.wordKey
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hint != null) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = promptText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
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

/** 选择题：4 选项按钮；选中后 submit selectedKey */
@Composable
private fun QuizChoiceSection(
    state: QuizUiState.Question,
    isCorrect: Boolean,
    onSubmitChoice: (String) -> Unit,
    onNextQuestion: () -> Unit,
) {
    val options = state.question.options.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.inputEnabled) {
            options.forEach { option ->
                OutlinedButton(
                    onClick = { onSubmitChoice(option.key) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = option.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
            }
        } else if (isCorrect) {
            Text(
                text = state.feedback?.message ?: "✓ 正确！",
                style = MaterialTheme.typography.labelLarge,
                color = WordFlipColors.extra.success,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onNextQuestion,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下一题")
            }
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
                color = MaterialTheme.colorScheme.primary,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        OutlinedButton(onClick = onRateDown) { Text("−") }
        Text(
            text = "${"%.1f".format(speechRate)}x",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OutlinedButton(onClick = onRateUp) { Text("+") }
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
                .scale(if (isSpeaking) pulseScale else 1f),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Outlined.VolumeUp,
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
    // 答错进入巩固或练习未通过时，输入框标红
    val inputIsError = state.consolidationActive && !practicePassed

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.userAnswer,
            onValueChange = onAnswerChange,
            modifier = Modifier
                .fillMaxWidth()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = state.userAnswer.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (practicePassed) "再检查一次" else "检查练习")
                }
                OutlinedButton(
                    onClick = onNextQuestion,
                    enabled = practicePassed,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("下一题")
                }
            }
        } else if (state.inputEnabled) {
            Button(
                onClick = onSubmit,
                enabled = state.userAnswer.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确认")
            }
        } else if (isCorrect) {
            Text(
                text = state.feedback?.message ?: "✓ 正确！",
                style = MaterialTheme.typography.labelLarge,
                color = WordFlipColors.extra.success,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onNextQuestion,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("下一题")
            }
        }
    }
}

/** 底部提示区：仅答错巩固阶段展示 */
@Composable
private fun QuizHintSection(
    state: QuizUiState.Question,
    practiceWrong: Boolean,
    practicePassed: Boolean,
) {
    val detail = state.question.detail

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                practicePassed -> {
                    HintLine(
                        text = state.practiceHint.orEmpty(),
                        color = WordFlipColors.extra.success,
                        bold = true,
                    )
                }
                practiceWrong -> {
                    HintLine(
                        text = state.practiceHint.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        bold = true,
                    )
                }
                else -> {
                    HintLine(text = "练习正确后才能进入下一题")
                }
            }

            state.feedback?.message?.let { message ->
                HintLine(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
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

@Composable
private fun RowMeta(current: Int, total: Int, score: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "得分 $score",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = result.rating.emoji(), fontSize = 48.sp)
        Text(
            text = result.rating.label(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultStatRow("答对", "${result.correctCount} 题")
                ResultStatRow("答错", "${result.wrongCount} 题")
                ResultStatRow(
                    "正确率",
                    "${FakeQuizData.accuracyPercent(result.accuracy)}%",
                    valueColor = WordFlipColors.extra.success,
                )
            }
        }
        if (result.wrongWords.isNotEmpty()) {
            Text(
                text = "错题列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
            )
            result.wrongWords.forEach { wrong ->
                Text(
                    text = "${wrong.cn} → ${wrong.en}（你的答案：${wrong.userAnswer}）",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("再来一次")
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("返回")
        }
    }
}

@Composable
private fun ResultStatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
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
internal fun preferChinesePrompt(cn: String): String {
    if (cn.isBlank()) return cn
    val index = cn.indexOfFirst { it in '\u4e00'..'\u9fff' }
    return if (index > 0) cn.substring(index) else cn
}
