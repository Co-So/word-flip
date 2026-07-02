package com.wordflip.feature.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.model.fake.FakeQuizData
import com.wordflip.core.model.quiz.QuizFeedbackType
import com.wordflip.core.model.quiz.QuizSource
import com.wordflip.core.model.quiz.emoji
import com.wordflip.core.model.quiz.label
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.QuizFeedbackBanner
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
    viewModel: QuizViewModel = viewModel(
        key = viewModelKey,
        factory = QuizViewModel.Factory(source, groupId),
    ),
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

@Composable
private fun QuizQuestionContent(
    state: QuizUiState.Question,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val isCorrect = state.feedback?.type == QuizFeedbackType.CORRECT
    val isWrong = state.feedback?.type == QuizFeedbackType.WRONG

    LaunchedEffect(state.currentIndex) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.question.prompt.cn,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                state.question.prompt.pos?.let { pos ->
                    Text(
                        text = pos,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                state.question.prompt.ph?.let { ph ->
                    Text(
                        text = ph,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.userAnswer,
            onValueChange = onAnswerChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            enabled = state.inputEnabled,
            placeholder = { Text("输入英文单词") },
            singleLine = true,
            isError = isWrong,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (state.inputEnabled) onSubmit() }),
        )
        state.feedback?.let { feedback ->
            QuizFeedbackBanner(
                message = feedback.message,
                isCorrect = feedback.type == QuizFeedbackType.CORRECT,
                expectedEn = feedback.expectedEn,
            )
        }
        Button(
            onClick = onSubmit,
            enabled = state.inputEnabled && state.userAnswer.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("确认")
        }
        if (isCorrect) {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun RowMeta(current: Int, total: Int, score: Int) {
    androidx.compose.foundation.layout.Row(
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
    androidx.compose.foundation.layout.Row(
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
        else -> QuizSource.TODAY
    }
}
