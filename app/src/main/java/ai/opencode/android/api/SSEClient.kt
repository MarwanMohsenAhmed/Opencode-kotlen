package ai.opencode.android.api

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit

class SSEClient(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private var eventSource: EventSource? = null

    fun connect(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Flow<EventMessage> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()

        val factory = EventSources.createFactory(client)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = EventMessage(
                    type = type ?: "message",
                    data = data,
                    id = id,
                )
                trySend(event)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                Log.w("SSEClient", "SSE connection failed", t)
                close(t ?: IOException("SSE connection failed"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        })

        awaitClose {
            eventSource?.cancel()
        }
    }

    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
    }
}
