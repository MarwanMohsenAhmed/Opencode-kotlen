package ai.opencode.android.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSource.Factory
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class OpenCodeApi(
    private val config: ServerConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            config.password?.let { pwd ->
                request.header("Authorization", okhttp3.Credentials.basic("opencode", pwd))
            }
            request.header("x-opencode-directory", config.directory.orEmpty())
            chain.proceed(request.build())
        }
        .build()

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
            config.password?.let { pwd ->
                request.header("Authorization", okhttp3.Credentials.basic("opencode", pwd))
            }
            chain.proceed(request.build())
        }
        .build()

    private val baseUrl get() = config.url.trimEnd('/')
    private val contentType = "application/json".toMediaType()

    private fun request(path: String): Request.Builder =
        Request.Builder().url("$baseUrl$path")

    private inline fun <reified T> get(path: String): T {
        val response = client.newCall(request(path).build()).execute()
        if (!response.isSuccessful) throw OpenCodeException(response.code, response.body?.string())
        return json.decodeFromStream<T>(response.body!!.byteStream())
    }

    private inline fun <reified T, reified B> post(path: String, body: B): T {
        val bodyStr = json.encodeToString(body)
        val response = client.newCall(
            request(path).post(bodyStr.toRequestBody(contentType)).build()
        ).execute()
        if (!response.isSuccessful) throw OpenCodeException(response.code, response.body?.string())
        return json.decodeFromStream<T>(response.body!!.byteStream())
    }

    private fun patch(path: String, bodyStr: String) {
        val response = client.newCall(
            request(path).patch(bodyStr.toRequestBody(contentType)).build()
        ).execute()
        if (!response.isSuccessful) throw OpenCodeException(response.code, response.body?.string())
    }

    private fun delete(path: String) {
        val response = client.newCall(request(path).delete().build()).execute()
        if (!response.isSuccessful) throw OpenCodeException(response.code, response.body?.string())
    }

    private fun buildJsonObject(fields: Map<String, String?>): JsonObject {
        val map = fields.mapValues { v ->
            v.value?.let { JsonPrimitive(it) } ?: JsonNull
        }
        return JsonObject(map)
    }

    suspend fun health(): HealthResponse = get("/global/health")

    suspend fun listSessions(limit: Int = 50): SessionListResponse =
        get("/session?limit=$limit")

    suspend fun getSession(sessionId: String): SessionDetail =
        get("/session/$sessionId")

    suspend fun createSession(directory: String, title: String? = null): Session {
        val body = CreateSessionRequest(title = title, directory = directory)
        return post("/session", body)
    }

    suspend fun deleteSession(sessionId: String) = delete("/session/$sessionId")

    suspend fun updateSession(sessionId: String, title: String? = null) {
        val fields = mutableMapOf<String, String?>()
        title?.let { fields["title"] = it }
        val bodyStr = json.encodeToString(JsonObject.serializer(), buildJsonObject(fields))
        patch("/session/$sessionId", bodyStr)
    }

    suspend fun listMessages(sessionId: String, limit: Int = 50, before: String? = null): MessageListResponse {
        val path = buildString {
            append("/session/$sessionId/message?limit=$limit")
            before?.let { append("&before=$it") }
        }
        return get(path)
    }

    suspend fun sendMessage(sessionId: String, content: String, agent: String? = null): PromptResponse {
        val body = PromptRequest(content = content, agent = agent)
        return post("/session/$sessionId/message", body)
    }

    suspend fun abortSession(sessionId: String) {
        val response = client.newCall(
            request("/session/$sessionId/abort").post("{}".toRequestBody(contentType)).build()
        ).execute()
        if (!response.isSuccessful) throw OpenCodeException(response.code, response.body?.string())
    }

    suspend fun listAgents(): List<Agent> = get("/agent")

    suspend fun getVcs(): VcsInfo = get("/vcs")

    suspend fun getFileContent(path: String): FileContent =
        get("/file/content?path=${java.net.URLEncoder.encode(path, "UTF-8")}")

    suspend fun searchFiles(query: String): List<SearchResult> =
        get("/find/file?query=${java.net.URLEncoder.encode(query, "UTF-8")}")

    suspend fun searchText(pattern: String): List<SearchResult> =
        get("/find?pattern=${java.net.URLEncoder.encode(pattern, "UTF-8")}")

    suspend fun listPermissions(): List<PermissionRequest> = get("/permission")

    suspend fun replyPermission(requestId: String, allow: Boolean) {
        val bodyStr = json.encodeToString(PermissionReply.serializer(), PermissionReply(allow = allow))
        val response = client.newCall(
            request("/permission/$requestId/reply").post(bodyStr.toRequestBody(contentType)).build()
        ).execute()
        if (!response.isSuccessful) throw OpenCodeException(response.code, response.body?.string())
    }

    fun subscribeEvents(
        onEvent: (EventMessage) -> Unit,
        onError: (Throwable) -> Unit,
    ): EventSource {
        val request = Request.Builder()
            .url("$baseUrl/event")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val factory = EventSources.createFactory(sseClient)
        return factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                val event = EventMessage(
                    type = type ?: "message",
                    data = data,
                    id = id,
                )
                onEvent(event)
            }

            override fun onFailure(source: EventSource, t: Throwable?, response: okhttp3.Response?) {
                onError(t ?: RuntimeException("SSE connection failed: ${response?.code}"))
            }

            override fun onClosed(source: EventSource) {}
        })
    }

    companion object {
        private var instance: OpenCodeApi? = null

        fun create(config: ServerConfig): OpenCodeApi {
            val api = OpenCodeApi(config)
            instance = api
            return api
        }

        fun get(): OpenCodeApi =
            instance ?: throw IllegalStateException("OpenCodeApi not initialized")
    }
}

class OpenCodeException(val statusCode: Int, message: String?) : RuntimeException("OpenCode API error $statusCode: $message")
