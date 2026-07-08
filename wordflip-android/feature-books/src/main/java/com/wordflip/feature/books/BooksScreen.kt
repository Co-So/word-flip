package com.wordflip.feature.books

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wordflip.core.model.book.BookItem
import com.wordflip.core.model.book.GroupStrategy
import com.wordflip.core.ui.component.BookListItem
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

private val GROUP_SIZE_OPTIONS = listOf(10, 20, 30, 50)

private data class GroupStrategyOption(
    val strategy: GroupStrategy,
    val label: String,
)

private val GROUP_STRATEGY_OPTIONS = listOf(
    GroupStrategyOption(GroupStrategy.BOOK_ORDER, "词书顺序"),
    GroupStrategyOption(GroupStrategy.FREQUENCY, "词频"),
    GroupStrategyOption(GroupStrategy.RANDOM, "随机"),
)

/**
 * 词书页 Hub + 向导（REQ-BOOK-17~26）：增加书籍 / 重新分组分步流程。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onNavigateToCustomGroup: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BooksViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.loadBooks()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFileSelected(context, uri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BooksUiEvent.Toast -> toast.show(event.message)
                is BooksUiEvent.ConfirmDelete -> pendingDelete = event.bookId to event.bookName
                BooksUiEvent.NavigateToCustomGroup -> onNavigateToCustomGroup()
                BooksUiEvent.LaunchFilePicker -> {
                    filePickerLauncher.launch(
                        arrayOf("text/*", "application/json", "application/csv"),
                    )
                }
            }
        }
    }

    pendingDelete?.let { (bookId, bookName) ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除词书") },
            text = { Text("确定删除「$bookName」？已入组的单词将保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmDeleteBook(bookId)
                        pendingDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    val content = uiState as? BooksUiState.Content
    val inWizard = content?.wizardMode != null

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (inWizard && content != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(viewModel.wizardTitle(content.wizardMode))
                            Text(
                                text = viewModel.wizardStepLabel(content.wizardStep),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::wizardBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                )
            } else {
                WordFlipTopBar(title = "词书")
            }
        },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        bottomBar = {
            if (inWizard && content != null) {
                WizardBottomBar(
                    step = content.wizardStep,
                    mode = content.wizardMode,
                    isSaving = content.isSaving,
                    onNext = viewModel::wizardNext,
                    onSave = viewModel::wizardSave,
                )
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            BooksUiState.Loading -> BoxLoading(modifier = Modifier.padding(innerPadding))
            is BooksUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::loadBooks,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is BooksUiState.Content -> {
                if (state.isParsingImport) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.wizardMode != null) {
                    BooksWizardContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                } else {
                    BooksHubContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
                state.importSheet?.let { sheet ->
                    BookImportPreviewSheet(
                        state = sheet,
                        onNameChange = viewModel::updateImportName,
                        onConfirm = viewModel::confirmImport,
                        onDismiss = viewModel::cancelImport,
                    )
                }
            }
        }
    }
}

@Composable
private fun BooksHubContent(
    state: BooksUiState.Content,
    viewModel: BooksViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "当前学习词书",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val selected = state.books.filter { it.selected }
                    if (selected.isEmpty()) {
                        Text(
                            text = "尚未选择词书，请先增加书籍或重新分组",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        selected.forEach { book ->
                            Text(
                                text = "· ${book.name}（${book.wordCount} 词）",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    Text(
                        text = viewModel.buildSummaryText(state.summary, state.groupSize),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.startWizard(BooksWizardMode.ADD) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.LibraryAdd, contentDescription = null)
                Text(text = "增加书籍", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.startWizard(BooksWizardMode.REGROUP) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(text = "重新分组", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            HorizontalDivider()
        }
        item {
            OutlinedButton(
                onClick = viewModel::onImportClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Text(text = "导入单词书", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            OutlinedButton(
                onClick = viewModel::onCustomGroupClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                Text(text = "手动添加分组", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            Text(
                text = "支持 JSON / CSV / TXT 格式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BooksWizardContent(
    state: BooksUiState.Content,
    viewModel: BooksViewModel,
    modifier: Modifier = Modifier,
) {
    when (state.wizardStep) {
        BooksWizardStep.SELECT_BOOKS -> WizardSelectBooksStep(state, viewModel, modifier)
        BooksWizardStep.STRATEGY -> WizardStrategyStep(state, viewModel, modifier)
        BooksWizardStep.CONFIRM -> WizardConfirmStep(state, viewModel, modifier)
        null -> Unit
    }
}

@Composable
private fun WizardSelectBooksStep(
    state: BooksUiState.Content,
    viewModel: BooksViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = when (state.wizardMode) {
                BooksWizardMode.ADD -> "已选词书不可取消，请勾选要新增的词书"
                BooksWizardMode.REGROUP -> "勾选要纳入分组的词书（可调整原有选择）"
                null -> ""
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(state.books, key = { it.id }) { book ->
                val locked = state.wizardMode == BooksWizardMode.ADD &&
                    book.id in state.lockedBookIds
                BookListItem(
                    book = book,
                    onToggle = { viewModel.toggleBookSelection(book.id) },
                    onDelete = if (book.canDelete && !locked) {
                        { viewModel.requestDeleteBook(book.id) }
                    } else {
                        null
                    },
                    enabled = !locked,
                    modifier = Modifier.alpha(if (locked) 0.72f else 1f),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun WizardStrategyStep(
    state: BooksUiState.Content,
    viewModel: BooksViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "分组大小",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GROUP_SIZE_OPTIONS.forEach { size ->
                FilterChip(
                    selected = state.groupSize == size,
                    onClick = { viewModel.setGroupSize(size) },
                    label = { Text("$size") },
                )
            }
        }
        if (state.wizardMode == BooksWizardMode.ADD) {
            Text(
                text = "增加书籍时，分组大小仅影响新追加的 auto 组",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "分组策略",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GROUP_STRATEGY_OPTIONS.forEach { option ->
                FilterChip(
                    selected = state.groupStrategy == option.strategy,
                    onClick = { viewModel.setGroupStrategy(option.strategy) },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WizardConfirmStep(
    state: BooksUiState.Content,
    viewModel: BooksViewModel,
    modifier: Modifier = Modifier,
) {
    val selectedBooks = state.books.filter { it.selected }
    val strategyLabel = GROUP_STRATEGY_OPTIONS.first { it.strategy == state.groupStrategy }.label
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "请确认以下设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = viewModel.buildSummaryText(state.summary, state.groupSize),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "分组策略：$strategyLabel · 每组 ${state.groupSize} 词",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "已选词书（${selectedBooks.size} 本）",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        selectedBooks.forEach { book ->
            Text(text = "· ${book.name}", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.wizardMode == BooksWizardMode.REGROUP) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "将删除全部自动分组并按上述设置重建。手动分组与学习进度保留。",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WizardBottomBar(
    step: BooksWizardStep?,
    mode: BooksWizardMode?,
    isSaving: Boolean,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step == BooksWizardStep.CONFIRM) {
                Button(
                    onClick = onSave,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        when (mode) {
                            BooksWizardMode.ADD -> "保存并追加分组"
                            BooksWizardMode.REGROUP -> "确认重新分组"
                            null -> "保存"
                        },
                    )
                }
            } else {
                Button(
                    onClick = onNext,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("下一步")
                }
            }
        }
    }
}

@Composable
private fun BoxLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}
