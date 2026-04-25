package ai.opencode.android.ui.codeviewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ai.opencode.android.data.FileRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: CodeViewerViewModel = hiltViewModel()
) {
    val fileContent by viewModel.fileContent.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val fileContentValue = fileContent
    val errorValue = error
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(filePath) {
        viewModel.loadFile(filePath)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = filePath.substringAfterLast("/"),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (fileContentValue != null) {
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(fileContentValue.content))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy to clipboard"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorValue != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = errorValue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            fileContentValue != null -> {
                val lines = fileContentValue.content.lines()
                val lineNumberWidth = lines.size.toString().length

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    lines.forEachIndexed { index, line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${index + 1}".padEnd(lineNumberWidth),
                                modifier = Modifier.padding(end = 12.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                            )
                            Text(
                                text = line.ifEmpty { " " },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
