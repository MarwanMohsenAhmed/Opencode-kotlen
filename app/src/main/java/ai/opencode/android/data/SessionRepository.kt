package ai.opencode.android.data

import ai.opencode.android.api.ApiProvider
import ai.opencode.android.api.OpenCodeApi
import ai.opencode.android.api.Part
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
 private val apiProvider: ApiProvider,
) {
 private val _sessions = MutableStateFlow<List<ai.opencode.android.api.Session>>(emptyList())
 val sessions: StateFlow<List<ai.opencode.android.api.Session>> = _sessions.asStateFlow()

 private val _currentSession = MutableStateFlow<ai.opencode.android.api.SessionDetail?>(null)
 val currentSession: StateFlow<ai.opencode.android.api.SessionDetail?> = _currentSession.asStateFlow()

 private val _messages = MutableStateFlow<List<ai.opencode.android.api.Message>>(emptyList())
 val messages: StateFlow<List<ai.opencode.android.api.Message>> = _messages.asStateFlow()

 private val _error = MutableStateFlow<String?>(null)
 val error: StateFlow<String?> = _error.asStateFlow()

 private val api: OpenCodeApi? get() = apiProvider.api.value

 suspend fun refreshSessions() = withContext(Dispatchers.IO) {
 val currentApi = api
 if (currentApi == null) {
 _error.value = "Not connected to server"
 return@withContext
 }
 try {
 val response = currentApi.listSessions()
 _sessions.value = response.sessions
 _error.value = null
 } catch (e: Exception) {
 _error.value = e.message ?: "Failed to load sessions"
 }
 }

 suspend fun loadSession(sessionId: String) = withContext(Dispatchers.IO) {
 val currentApi = api
 if (currentApi == null) {
 _error.value = "Not connected to server"
 return@withContext
 }
 try {
 val session = currentApi.getSession(sessionId)
 _currentSession.value = session
 val msgResponse = currentApi.listMessages(sessionId)
 val messagesWithParts = msgResponse.messages.map { msg ->
 try {
 val parts = currentApi.listParts(sessionId, msg.id)
 msg.copy(parts = parts)
 } catch (_: Exception) {
 msg
 }
 }
 _messages.value = messagesWithParts
 _error.value = null
 } catch (e: Exception) {
 _error.value = e.message ?: "Failed to load session"
 }
 }

 suspend fun sendMessage(content: String, agent: String? = null) {
 val sessionId = _currentSession.value?.id ?: return
 val currentApi = api ?: return
 withContext(Dispatchers.IO) {
 currentApi.sendMessage(sessionId, content, agent)
 val msgResponse = currentApi.listMessages(sessionId)
 val messagesWithParts = msgResponse.messages.map { msg ->
 try {
 val parts = currentApi.listParts(sessionId, msg.id)
 msg.copy(parts = parts)
 } catch (_: Exception) {
 msg
 }
 }
 _messages.value = messagesWithParts
 }
 }

 suspend fun createSession(directory: String, title: String? = null): ai.opencode.android.api.Session {
 val currentApi = api ?: throw IllegalStateException("Not connected to server")
 return withContext(Dispatchers.IO) {
 val session = currentApi.createSession(directory, title)
 refreshSessions()
 session
 }
 }

 suspend fun abortSession() {
 val sessionId = _currentSession.value?.id ?: return
 val currentApi = api ?: return
 withContext(Dispatchers.IO) {
 currentApi.abortSession(sessionId)
 }
 }

 suspend fun deleteSession(sessionId: String) {
 val currentApi = api ?: return
 withContext(Dispatchers.IO) {
 currentApi.deleteSession(sessionId)
 _sessions.update { it.filter { s -> s.id != sessionId } }
 }
 }

 fun clearCurrentSession() {
 _currentSession.value = null
 _messages.value = emptyList()
 }
}