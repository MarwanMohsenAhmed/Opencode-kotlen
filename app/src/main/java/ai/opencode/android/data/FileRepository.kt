package ai.opencode.android.data

import ai.opencode.android.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    private val api: OpenCodeApi,
) {
    private val _currentFile = MutableStateFlow<FileContent?>(null)
    val currentFile: StateFlow<FileContent?> = _currentFile.asStateFlow()

    suspend fun loadFile(path: String) = withContext(Dispatchers.IO) {
        try {
            val content = api.getFileContent(path)
            _currentFile.value = content
        } catch (_: Exception) { }
    }

    suspend fun searchFiles(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            api.searchFiles(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchText(pattern: String): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            api.searchText(pattern)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearFile() {
        _currentFile.value = null
    }
}

@Singleton
class PermissionRepository @Inject constructor(
    private val api: OpenCodeApi,
) {
    private val _pendingPermissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
    val pendingPermissions: StateFlow<List<PermissionRequest>> = _pendingPermissions.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            _pendingPermissions.value = api.listPermissions()
        } catch (_: Exception) { }
    }

    suspend fun reply(requestId: String, allow: Boolean) = withContext(Dispatchers.IO) {
        api.replyPermission(requestId, allow)
        _pendingPermissions.value = _pendingPermissions.value.filter { it.id != requestId }
    }
}
