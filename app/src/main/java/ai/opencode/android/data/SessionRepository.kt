package ai.opencode.android.data

import ai.opencode.android.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val api: OpenCodeApi,
) {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _currentSession = MutableStateFlow<SessionDetail?>(null)
    val currentSession: StateFlow<SessionDetail?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    suspend fun refreshSessions() = withContext(Dispatchers.IO) {
        try {
            val response = api.listSessions()
            _sessions.value = response.sessions
        } catch (_: Exception) { }
    }

    suspend fun loadSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val session = api.getSession(sessionId)
            _currentSession.value = session
            val msgResponse = api.listMessages(sessionId)
            _messages.value = msgResponse.messages
        } catch (_: Exception) { }
    }

    suspend fun sendMessage(content: String, agent: String? = null) {
        val sessionId = _currentSession.value?.id ?: return
        withContext(Dispatchers.IO) {
            api.sendMessage(sessionId, content, agent)
        }
    }

    suspend fun createSession(directory: String, title: String? = null) = withContext(Dispatchers.IO) {
        api.createSession(directory, title)
    }

    suspend fun abortSession() {
        val sessionId = _currentSession.value?.id ?: return
        withContext(Dispatchers.IO) {
            api.abortSession(sessionId)
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        api.deleteSession(sessionId)
        _sessions.value = _sessions.value.filter { it.id != sessionId }
    }

    fun clearCurrentSession() {
        _currentSession.value = null
        _messages.value = emptyList()
    }
}
