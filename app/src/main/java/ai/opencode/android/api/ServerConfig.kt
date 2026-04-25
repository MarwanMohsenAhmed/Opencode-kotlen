package ai.opencode.android.api

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val url: String = "http://127.0.0.1:4096",
    val password: String? = null,
    val mode: ConnectionMode = ConnectionMode.REMOTE,
    val directory: String? = null,
)

enum class ConnectionMode(val label: String) {
    EMBEDDED("Embedded"),
    REMOTE("Remote"),
}
