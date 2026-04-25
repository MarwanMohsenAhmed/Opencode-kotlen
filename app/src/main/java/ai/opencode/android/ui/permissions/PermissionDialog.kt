package ai.opencode.android.ui.permissions

import ai.opencode.android.api.PermissionRequest
import ai.opencode.android.data.PermissionRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PermissionDialog(
    request: PermissionRequest,
    onAllow: (String) -> Unit,
    onDeny: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val toolDisplayName = request.tool.removePrefix("tool_")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val description = request.title ?: buildDescription(request.tool, request.input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "$toolDisplayName Permission",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!request.input.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Content:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        ) {
                            Text(
                                text = request.input,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(modifier = Modifier.padding(end = 8.dp)) {
                Button(
                    onClick = { onDeny(request.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text("Deny")
                }
                Button(
                    onClick = { onAllow(request.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text("Allow")
                }
            }
        },
        dismissButton = null,
    )
}

private fun buildDescription(tool: String, input: String?): String {
    val toolName = tool.removePrefix("tool_")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    return when {
        tool.startsWith("bash", ignoreCase = true) -> "The AI wants to run a shell command."
        tool.startsWith("file_edit", ignoreCase = true) || tool.startsWith("fileedit", ignoreCase = true) ->
            "The AI wants to edit a file."
        tool.startsWith("file_read", ignoreCase = true) || tool.startsWith("fileread", ignoreCase = true) ->
            "The AI wants to read a file."
        else -> "The AI wants to use $toolName."
    }
}

@Composable
fun PermissionOverlay(
    repository: PermissionRepository,
) {
    val scope = rememberCoroutineScope()
    val pendingPermissions by repository.pendingPermissions.collectAsState()
    val firstPending = pendingPermissions.firstOrNull()

    if (firstPending != null) {
        PermissionDialog(
            request = firstPending,
            onAllow = { requestId ->
                scope.launch { repository.reply(requestId, allow = true) }
            },
            onDeny = { requestId ->
                scope.launch { repository.reply(requestId, allow = false) }
            },
            onDismiss = {},
        )
    }
}
