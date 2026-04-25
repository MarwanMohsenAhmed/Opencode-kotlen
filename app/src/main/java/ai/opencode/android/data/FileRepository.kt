package ai.opencode.android.data

import ai.opencode.android.api.ApiProvider
import ai.opencode.android.api.OpenCodeApi
import ai.opencode.android.api.PermissionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
 private val apiProvider: ApiProvider,
) {
 private val _currentFile = MutableStateFlow<ai.opencode.android.api.FileContent?>(null)
 val currentFile: StateFlow<ai.opencode.android.api.FileContent?> = _currentFile.asStateFlow()

 private val api: OpenCodeApi? get() = apiProvider.api.value

 suspend fun loadFile(path: String) = withContext(Dispatchers.IO) {
 val currentApi = api ?: return@withContext
 try {
 val content = currentApi.getFileContent(path)
 _currentFile.value = content
 } catch (_: Exception) {
 }
 }

 suspend fun searchFiles(query: String): List<ai.opencode.android.api.SearchResult> = withContext(Dispatchers.IO) {
 val currentApi = api ?: return@withContext emptyList()
 try {
 currentApi.searchFiles(query)
 } catch (_: Exception) {
 emptyList()
 }
 }

 suspend fun searchText(pattern: String): List<ai.opencode.android.api.SearchResult> = withContext(Dispatchers.IO) {
 val currentApi = api ?: return@withContext emptyList()
 try {
 currentApi.searchText(pattern)
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
 private val apiProvider: ApiProvider,
) {
 private val _pendingPermissions = MutableStateFlow<List<PermissionRequest>>(emptyList())
 val pendingPermissions: StateFlow<List<PermissionRequest>> = _pendingPermissions.asStateFlow()

 private val _error = MutableStateFlow<String?>(null)
 val error: StateFlow<String?> = _error.asStateFlow()

 private val api: OpenCodeApi? get() = apiProvider.api.value

 suspend fun refresh() = withContext(Dispatchers.IO) {
 val currentApi = api ?: return@withContext
 try {
 _pendingPermissions.value = currentApi.listPermissions()
 _error.value = null
 } catch (e: Exception) {
 _error.value = e.message
 }
 }

 suspend fun reply(requestId: String, allow: Boolean) {
 val currentApi = api ?: return
 withContext(Dispatchers.IO) {
 currentApi.replyPermission(requestId, allow)
 _pendingPermissions.update { it.filter { p -> p.id != requestId } }
 }
 }
}