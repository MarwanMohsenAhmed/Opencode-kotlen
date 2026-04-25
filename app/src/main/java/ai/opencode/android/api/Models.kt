package ai.opencode.android.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val healthy: Boolean,
    val version: String,
)

@Serializable
data class SessionListResponse(
    val sessions: List<Session>,
)

@Serializable
data class Session(
    val id: String,
    val project_id: String,
    val title: String,
    val slug: String,
    val directory: String,
    val share_url: String? = null,
    val time_created: Long,
    val time_updated: Long,
    val time_archived: Long? = null,
)

@Serializable
data class SessionDetail(
    val id: String,
    val project_id: String,
    val title: String,
    val slug: String,
    val directory: String,
    val share_url: String? = null,
    val summary_additions: Int? = null,
    val summary_deletions: Int? = null,
    val summary_files: Int? = null,
    val time_created: Long,
    val time_updated: Long,
    val time_archived: Long? = null,
)

@Serializable
data class CreateSessionRequest(
    val title: String? = null,
    val directory: String,
)

@Serializable
data class MessageListResponse(
    val messages: List<Message>,
)

@Serializable
data class Message(
 val id: String,
 val session_id: String,
 val role: String,
 val data: MessageData,
 val parts: List<Part> = emptyList(),
 val time_created: Long,
 val time_updated: Long,
)

@Serializable
data class MessageData(
    val role: String,
    val agent: String? = null,
    val model: ModelInfo? = null,
    val error: String? = null,
    val cost: CostInfo? = null,
    val tokens: TokenInfo? = null,
)

@Serializable
data class ModelInfo(
    val provider_id: String,
    val model_id: String,
    val variant: String? = null,
)

@Serializable
data class CostInfo(
    val input: Double? = null,
    val output: Double? = null,
    val total: Double? = null,
)

@Serializable
data class TokenInfo(
    val input: Int? = null,
    val output: Int? = null,
    val cache_read: Int? = null,
    val cache_write: Int? = null,
)

@Serializable
data class Part(
    val id: String,
    val message_id: String,
    val session_id: String,
    val type: String,
    val data: PartData,
    val time_created: Long,
    val time_updated: Long,
)

@Serializable
data class PartData(
    val type: String,
    val text: String? = null,
    val call_id: String? = null,
    val tool: String? = null,
    val state: String? = null,
    val input: String? = null,
    val output: String? = null,
    val error: String? = null,
    val title: String? = null,
    val mime: String? = null,
    val filename: String? = null,
    val url: String? = null,
    val time: PartTimeInfo? = null,
)

@Serializable
data class PartTimeInfo(
    val start: Long? = null,
    val end: Long? = null,
)

@Serializable
data class PromptRequest(
    val content: String,
    val agent: String? = null,
    val model: String? = null,
)

@Serializable
data class PromptResponse(
    val id: String,
    val session_id: String,
    val role: String,
    val data: MessageData,
    val time_created: Long,
    val time_updated: Long,
)

@Serializable
data class PermissionRequest(
    val id: String,
    val session_id: String,
    val tool: String,
    val input: String? = null,
    val title: String? = null,
)

@Serializable
data class PermissionReply(
    val allow: Boolean,
)

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val model: ModelInfo? = null,
)

@Serializable
data class Project(
    val id: String,
    val name: String? = null,
    val path: String,
    val icon_url: String? = null,
)

@Serializable
data class VcsInfo(
    val branch: String,
    val default_branch: String,
)

@Serializable
data class FileContent(
    val path: String,
    val content: String,
)

@Serializable
data class SearchResult(
    val path: String,
    val line: Int,
    val text: String,
)

@Serializable
data class EventMessage(
    val type: String,
    val data: String,
    val id: String? = null,
    val retry: Long? = null,
)
