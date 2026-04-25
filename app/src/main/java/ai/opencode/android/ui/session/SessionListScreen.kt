package ai.opencode.android.ui.session

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import ai.opencode.android.api.Session
import ai.opencode.android.data.SessionRepository

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val sessions: StateFlow<List<Session>> = sessionRepository.sessions

    private var _isRefreshing = mutableStateOf(false)
    val isRefreshing: androidx.compose.runtime.State<Boolean> = _isRefreshing

    private var _isCreating = mutableStateOf(false)
    val isCreating: androidx.compose.runtime.State<Boolean> = _isCreating

    private var _error = mutableStateOf<String?>(null)
    val error: androidx.compose.runtime.State<String?> = _error

    fun refreshSessions() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                sessionRepository.refreshSessions()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh sessions"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun createSession(directory: String, title: String?) {
        if (directory.isBlank()) return
        viewModelScope.launch {
            _isCreating.value = true
            _error.value = null
            try {
                sessionRepository.createSession(directory.trim(), title?.trim()?.ifBlank { null })
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create session"
            } finally {
                _isCreating.value = false
            }
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                sessionRepository.deleteSession(id)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete session"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(epochMillis)
        val now = Instant.now()
        val minutesAgo = ChronoUnit.MINUTES.between(instant, now)
        when {
            minutesAgo < 1 -> "Just now"
            minutesAgo < 60 -> "$minutesAgo min ago"
            minutesAgo < 1440 -> "${minutesAgo / 60}h ago"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    } catch (_: Exception) {
        "Unknown"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onSessionClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing
    val isCreating by viewModel.isCreating
    val error by viewModel.error

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var directoryInput by rememberSaveable { mutableStateOf("") }
    var titleInput by rememberSaveable { mutableStateOf("") }

    val pullToRefreshState = rememberPullToRefreshState()

    if (showCreateDialog) {
        NewSessionDialog(
            directoryInput = directoryInput,
            onDirectoryChange = { directoryInput = it },
            titleInput = titleInput,
            onTitleChange = { titleInput = it },
            isCreating = isCreating,
            onDismiss = {
                showCreateDialog = false
                directoryInput = ""
                titleInput = ""
            },
            onConfirm = {
                viewModel.createSession(directoryInput, titleInput.ifBlank { null })
                showCreateDialog = false
                directoryInput = ""
                titleInput = ""
            }
        )
    }

    error?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenCode") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New session"
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshSessions() },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (sessions.isEmpty() && !isRefreshing) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = sessions,
                        key = { it.id }
                    ) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onSessionClick(session.id) },
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No sessions yet",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to create a new session",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.title.ifBlank { session.slug.ifBlank { "Untitled" } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(session.time_created),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = session.directory,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (session.time_updated != session.time_created) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Updated ${formatTimestamp(session.time_updated)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewSessionDialog(
    directoryInput: String,
    onDirectoryChange: (String) -> Unit,
    titleInput: String,
    onTitleChange: (String) -> Unit,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isValid = directoryInput.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = { Text("New Session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = directoryInput,
                    onValueChange = onDirectoryChange,
                    label = { Text("Directory path") },
                    placeholder = { Text("/path/to/project") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = onTitleChange,
                    label = { Text("Title (optional)") },
                    placeholder = { Text("My session") },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = isValid && !isCreating
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}
