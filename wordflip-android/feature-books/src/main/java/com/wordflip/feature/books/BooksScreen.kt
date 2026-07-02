package com.wordflip.feature.books

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wordflip.core.ui.component.BookListItem
import com.wordflip.core.ui.component.NetworkErrorView
import com.wordflip.core.ui.component.WordFlipToastHost
import com.wordflip.core.ui.component.WordFlipTopBar
import com.wordflip.core.ui.component.rememberWordFlipToast

private val GROUP_SIZE_OPTIONS = listOf(10, 20, 30, 50)

/**
 * 词书页（REQ-BOOK-1~16）：勾选、分组大小、Sticky 保存栏；导入/手动分组 Toast 占位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    modifier: Modifier = Modifier,
    viewModel: BooksViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val (snackbarHostState, toast) = rememberWordFlipToast()
    var pendingDelete by remember { mutableStateOf<Pair<Long, String>?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BooksUiEvent.Toast -> toast.show(event.message)
                is BooksUiEvent.ConfirmDelete -> pendingDelete = event.bookId to event.bookName
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { WordFlipTopBar(title = "词书") },
        snackbarHost = { WordFlipToastHost(snackbarHostState) },
        bottomBar = {
            val content = uiState as? BooksUiState.Content
            if (content != null) {
                Surface(shadowElevation = 8.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = viewModel.buildSummaryText(content.summary, content.groupSize),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::saveSettings,
                            enabled = content.isDirty && !content.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (content.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Text("保存设置")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            BooksUiState.Loading -> {
                BoxLoading(modifier = Modifier.padding(innerPadding))
            }
            is BooksUiState.Error -> {
                NetworkErrorView(
                    message = state.message,
                    onRetry = viewModel::loadBooks,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            is BooksUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            onToggle = { viewModel.toggleBookSelection(book.id) },
                            onDelete = if (book.canDelete) {
                                { viewModel.requestDeleteBook(book.id) }
                            } else {
                                null
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            OutlinedButton(
                                onClick = viewModel::onImportClick,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                                Text(
                                    text = "导入单词书",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            OutlinedButton(
                                onClick = viewModel::onCustomGroupClick,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.GroupAdd, contentDescription = null)
                                Text(
                                    text = "手动添加分组",
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            Text(
                                text = "支持 JSON / CSV / TXT 格式",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
