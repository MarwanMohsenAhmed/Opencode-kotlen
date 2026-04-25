# OpenCode Android — Fix & Improvement Plan

**Date**: 2026-04-25  
**Based on**: Full codebase analysis of `/root/opencode/packages/android/`  

---

## Executive Summary

The Android client has a solid architectural foundation but contains **7 critical bugs** that render core features non-functional, **6 security issues** (3 high severity), **5 dead-code modules**, and **13 medium-priority code quality problems**. The most impactful issue is that **chat is completely broken** — messages show role names ("user"/"assistant") instead of content, and sent messages never appear in the UI.

This plan is organized into 4 phases, ordered by dependency and impact.

---

## Phase 1: Critical Bug Fixes (Day 1-2)

These fixes are required before any feature work. The app is currently non-functional for its primary use case (chat).

### 1.1 Fix Message Content Display

**Problem**: `ChatScreen.kt:240-242` — `extractMessageText()` returns `message.data.error ?: message.role`, which means:
- User messages display as "user"  
- Assistant messages display as "assistant"  
- Actual text content is never shown

**Root Cause**: Message `Part` objects (which contain the actual text, tool calls, code blocks) are never fetched from the server. The `/session/{id}/part` endpoint is never called.

**Fix**:

1. **Add `listParts()` method to `OpenCodeApi.kt`**:
   ```kotlin
   suspend fun listParts(sessionId: String, messageId: String): List<Part> =
       get("/session/$sessionId/message/$messageId/part")
   ```

2. **Add `parts` field to `Message` model in `Models.kt`**:
   ```kotlin
   @Serializable
   data class Message(
       val id: String,
       val session_id: String,
       val role: String,
       val data: MessageData,
       val parts: List<Part> = emptyList(), // Added
       val time_created: Long,
       val time_updated: Long,
   )
   ```

3. **Update `SessionRepository.loadSession()` to fetch parts for each message**:
   ```kotlin
   suspend fun loadSession(sessionId: String) = withContext(Dispatchers.IO) {
       try {
           val session = api.getSession(sessionId)
           _currentSession.value = session
           val msgResponse = api.listMessages(sessionId)
           val messagesWithParts = msgResponse.messages.map { msg ->
               val parts = api.listParts(sessionId, msg.id)
               msg.copy(parts = parts)
           }
           _messages.value = messagesWithParts
       } catch (e: Exception) {
           _error.value = e.message
       }
   }
   ```

4. **Rewrite `extractMessageText()` in `ChatScreen.kt`**:
   ```kotlin
   private fun extractMessageText(message: Message): String {
       val textParts = message.parts.filter { it.type == "text" && it.data.text != null }
       if (textParts.isNotEmpty()) {
           return textParts.joinToString("\n\n") { it.data.text!! }
       }
       return message.data.error ?: ""
   }
   ```

5. **Add rendering for tool-call and tool-result parts** in `MessageBubble`:
   - Text parts: rendered as formatted text
   - Tool-call parts: show tool name, collapsible input
   - Tool-result parts: show success/failure badge, collapsible output
   - Error parts: show in red error styling

**Files Changed**: `OpenCodeApi.kt`, `Models.kt`, `SessionRepository.kt`, `ChatScreen.kt`

---

### 1.2 Fix Sent Messages Not Appearing

**Problem**: `SessionRepository.kt:39-44` — `sendMessage()` calls the API but never updates `_messages`. User sends a message, it disappears from the input, and nothing appears in the chat.

**Fix**:

```kotlin
suspend fun sendMessage(content: String, agent: String? = null) {
    val sessionId = _currentSession.value?.id ?: return
    withContext(Dispatchers.IO) {
        val response = api.sendMessage(sessionId, content, agent)
        // Reload messages to get the new user message + any assistant response
        val msgResponse = api.listMessages(sessionId)
        _messages.value = msgResponse.messages
    }
}
```

After SSE is implemented (Phase 2), this will be replaced by SSE-driven updates. For now, a simple reload after send ensures the user sees their message.

**File**: `SessionRepository.kt`

---

### 1.3 Fix Response Body Leaks

**Problem**: `OpenCodeApi.kt:60-84` — Every `get()`, `post()`, `patch()`, `delete()` method calls `execute()` and reads the body, but **never calls `response.close()`**. OkHttp documentation states the response body must be closed or the connection will not be reclaimed.

**Fix**: Use `use {}` block on all response objects:

```kotlin
private inline fun <reified T> get(path: String): T {
    val response = client.newCall(request(path).build()).execute()
    response.use {
        if (!it.isSuccessful) throw OpenCodeException(it.code, it.body?.string())
        return json.decodeFromStream<T>(it.body?.byteStream() ?: throw OpenCodeException(0, "Empty body"))
    }
}
```

Apply same pattern to `post()`, `patch()`, `delete()`, `abortSession()`, `replyPermission()`.

**File**: `OpenCodeApi.kt`

---

### 1.4 Fix `response.body!!` Force-Null Assertions

**Problem**: `OpenCodeApi.kt:63,71` — `response.body!!.byteStream()` will throw `KotlinNullPointerException` if the server returns a successful response with an empty body.

**Fix**: Replace `!!` with safe handling (combined with 1.3 above):

```kotlin
val body = it.body?.byteStream() ?: throw OpenCodeException(0, "Empty response body")
return json.decodeFromStream<T>(body)
```

**File**: `OpenCodeApi.kt`

---

### 1.5 Fix `runBlocking` on Main Thread

**Problem**: `ApiModule.kt:33` — `runBlocking { context.dataStore.data.first() }` blocks the main thread during Hilt component initialization. This can cause ANR (Application Not Responding) crashes.

**Fix**: Change `ServerConfig` and `OpenCodeApi` provision from synchronous to asynchronous. Two approaches:

**Approach A (Recommended — Lazy initialization)**:
Replace the frozen singleton with a lazy `ApiProvider` that reads settings on first access from a background thread:

```kotlin
@Singleton
class ApiProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.dataStore
    
    private val _api = MutableStateFlow<OpenCodeApi?>(null)
    val api: StateFlow<OpenCodeApi?> = _api.asStateFlow()
    
    suspend fun initialize() {
        val prefs = dataStore.data.first()
        val config = ServerConfig(
            url = prefs[KEY_SERVER_URL] ?: "http://127.0.0.1:4096",
            password = prefs[KEY_SERVER_PASSWORD],
            mode = when (prefs[KEY_CONNECTION_MODE]) {
                "EMBEDDED" -> ConnectionMode.EMBEDDED
                else -> ConnectionMode.REMOTE
            },
            directory = prefs[KEY_DIRECTORY],
        )
        _api.value = OpenCodeApi(config)
    }
    
    suspend fun updateConfig(config: ServerConfig) {
        _api.value = OpenCodeApi(config)
    }
}
```

Initialize in `OpenCodeApp.onCreate()` using `lifecycleScope.launch(Dispatchers.IO)`.

**Approach B (Minimal change)**:
Move the `runBlocking` to `Dispatchers.IO` by using `@BackgroundThread` annotation and ensuring Hilt doesn't call this on main. Less robust but quicker.

**Files**: `ApiModule.kt`, `OpenCodeApp.kt`, new `ApiProvider.kt`

---

### 1.6 Fix Silent Exception Swallowing

**Problem**: `SessionRepository.kt:23-28,30-37` — `catch (_: Exception) { }` means the user never sees error feedback. Same in `FileRepository.kt:19-24`.

**Fix**: Add error state to repositories and propagate to ViewModels:

```kotlin
// In SessionRepository
private val _error = MutableStateFlow<String?>(null)
val error: StateFlow<String?> = _error.asStateFlow()

suspend fun refreshSessions() = withContext(Dispatchers.IO) {
    try {
        val response = api.listSessions()
        _sessions.value = response.sessions
        _error.value = null
    } catch (e: Exception) {
        _error.value = e.message
    }
}
```

In ViewModels, expose error state. In Composables, show `Snackbar` or error banner.

**Files**: `SessionRepository.kt`, `FileRepository.kt`, `PermissionRepository`, all ViewModels, all Screens

---

### 1.7 Fix `isLoading` Stuck on Error

**Problem**: `ChatScreen.kt:266-274` — `ChatViewModel.sendMessage()` has `try/finally` but if `sessionRepo.sendMessage()` throws, `_isLoading` is set to `false` without any error feedback. Worse, the `sendMessage()` in the repository doesn't update `_messages`, so the UI shows nothing.

**Fix**:
```kotlin
fun sendMessage(content: String) {
    viewModelScope.launch {
        _isLoading.value = true
        try {
            sessionRepo.sendMessage(content)
        } catch (e: Exception) {
            _error.value = e.message
        } finally {
            _isLoading.value = false
        }
    }
}
```

Add `_error` StateFlow to `ChatViewModel` and show it in `ChatScreen`.

**Files**: `ChatScreen.kt` (ChatViewModel), `ChatScreen.kt` (Composable)

---

### 1.8 Fix Default Connection Mode Mismatch

**Problem**: `SettingsScreen.kt:89` defaults to `ConnectionMode.EMBEDDED` when no preference is set, but `ApiModule.kt:39` defaults to `ConnectionMode.REMOTE`. After clearing data or on first install, the two layers disagree.

**Fix**: Use a single source of truth for defaults. Define in `ServerConfig.kt`:

```kotlin
enum class ConnectionMode(val label: String) {
    EMBEDDED("Embedded"),
    REMOTE("Remote");

    companion object {
        val DEFAULT = REMOTE
    }
}
```

Reference `ConnectionMode.DEFAULT` in both `ApiModule.kt` and `SettingsScreen.kt`.

**Files**: `ServerConfig.kt`, `ApiModule.kt`, `SettingsScreen.kt`

---

## Phase 2: Core Feature Implementation (Day 3-5)

These are the features that make the app actually useful for its intended purpose.

### 2.1 SSE Real-Time Event Streaming

**Status**: Dead code — `SSEClient.kt` and `OpenCodeApi.subscribeEvents()` exist but are never called.

**Implementation**:

1. **Integrate `SSEClient` into Hilt DI** via `ApiModule`:
   ```kotlin
   @Provides
   @Singleton
   fun provideSSEClient(api: OpenCodeApi): SSEClient =
       SSEClient(api.sseClient, api.json)
   ```

2. **Create `EventBus` in new file `EventBus.kt`**:
   ```kotlin
   @Singleton
   class EventBus @Inject constructor(
       private val api: OpenCodeApi,
   ) {
       private var eventSource: EventSource? = null
       private val _events = MutableSharedFlow<EventMessage>(extraBufferCapacity = 64)
       val events: SharedFlow<EventMessage> = _events.asSharedFlow()
       
       fun connect() {
           if (eventSource != null) return
           eventSource = api.subscribeEvents(
               onEvent = { event -> _events.tryEmit(event) },
               onError = { /* handle reconnect */ },
           )
       }
       
       fun disconnect() {
           eventSource?.cancel()
           eventSource = null
       }
   }
   ```

3. **Process SSE events in `SessionRepository`**:
   ```kotlin
   init {
       viewModelScope.launch {
           eventBus.events.collect { event ->
               when (event.type) {
                   "message.part" -> handleNewPart(event)
                   "message.complete" -> handleGenerationComplete(event)
                   "session.update" -> refreshSessions()
                   "permission" -> permissionRepo.refresh()
               }
           }
       }
   }
   ```

4. **Connect/disconnect in `ChatViewModel`** based on session visibility.

5. **Add `x-opencode-directory` header to SSE client** (`OpenCodeApi.kt:50` — currently missing).

6. **Implement automatic reconnection** with exponential backoff on SSE failure.

**Files**: `SSEClient.kt`, `ApiModule.kt`, new `EventBus.kt`, `SessionRepository.kt`, `ChatViewModel`

---

### 2.2 Permission Flow Integration

**Status**: `PermissionDialog.kt` and `PermissionOverlay` are fully implemented but never called from any screen.

**Implementation**:

1. **Integrate `PermissionOverlay` into `MainActivity.kt`** or `Navigation.kt` as a global overlay:
   ```kotlin
   // In MainActivity's setContent, wrap NavHost with:
   val permissionRepo: PermissionRepository = hiltViewModel<PermissionHostViewModel>().permissionRepo
   PermissionOverlay(repository = permissionRepo)
   ```

2. **Create `PermissionHostViewModel`** to hold `PermissionRepository` reference.

3. **Trigger `permissionRepo.refresh()`** when:
   - SSE event of type `"permission"` is received
   - Chat screen is opened (initial poll)
   - Periodically (every 5 seconds as fallback)

4. **Add notification for pending permissions** when app is backgrounded.

**Files**: `MainActivity.kt`, `Navigation.kt`, new `PermissionHostViewModel.kt`, `EventBus.kt`

---

### 2.3 Dynamic API Reconfiguration

**Status**: `OpenCodeApi` is a frozen Hilt singleton. Settings changes require app restart.

**Implementation**:

1. **Replace `OpenCodeApi` singleton with `ApiProvider`** (see fix 1.5):
   - `ApiProvider` holds a `MutableStateFlow<OpenCodeApi?>`
   - Repositories observe `apiProvider.api` and update their reference
   - When settings change, `ApiProvider.updateConfig()` creates a new `OpenCodeApi`

2. **Update repositories** to accept `ApiProvider` instead of `OpenCodeApi`:
   ```kotlin
   @Singleton
   class SessionRepository @Inject constructor(
       private val apiProvider: ApiProvider,
   ) {
       private val api: OpenCodeApi? get() = apiProvider.api.value
       // ... all methods check api != null
   }
   ```

3. **In `SettingsViewModel`**, after saving settings, call `apiProvider.updateConfig()`:
   ```kotlin
   fun setServerUrl(value: String) {
       viewModelScope.launch {
           dataStore.edit { prefs -> prefs[SettingsKeys.SERVER_URL] = value }
           apiProvider.reinitialize()
       }
   }
   ```

4. **Close existing SSE connections** before recreating API.

**Files**: `ApiModule.kt`, new `ApiProvider.kt`, all Repositories, `SettingsViewModel`

---

### 2.4 Session List Refresh After Creation

**Problem**: `SessionRepository.kt:46-48` — `createSession()` creates a session on the server but never refreshes `_sessions`.

**Fix**:
```kotlin
suspend fun createSession(directory: String, title: String? = null): Session {
    return withContext(Dispatchers.IO) {
        val session = api.createSession(directory, title)
        refreshSessions() // Refresh the list
        session
    }
}
```

**File**: `SessionRepository.kt`

---

## Phase 3: Security Hardening (Day 5-6)

### 3.1 Encrypted Password Storage

**Problem**: Password stored plaintext in DataStore Preferences at `/data/data/ai.opencode.android/files/datastore/opencode.preferences_pb`.

**Fix**: Use `EncryptedDataStore` or wrap with Android Keystore:

```kotlin
// Add dependency: androidx.security:security-crypto:1.1.0-alpha06
// Use EncryptedSharedPreferences or custom EncryptedDataStore

// Alternatively, use Jetpack Security:
val masterKey = MasterKey.Builder(context)
    .keyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "opencode_secure",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)
```

Store password in encrypted prefs, other settings in regular DataStore.

**Files**: `ApiModule.kt`, `SettingsViewModel.kt`, `build.gradle.kts` (add dependency)

---

### 3.2 Network Security Configuration

**Problem**: `AndroidManifest.xml:16` — `usesCleartextTraffic="true"` allows HTTP to any host. No `network_security_config.xml` exists.

**Fix**:

1. Create `app/src/main/res/xml/network_security_config.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <network-security-config>
       <domain-config cleartextTrafficPermitted="true">
           <domain includeSubdomains="true">127.0.0.1</domain>
           <domain includeSubdomains="true">localhost</domain>
           <domain includeSubdomains="true">10.0.0.0</domain>
           <domain includeSubdomains="true">192.168.0.0</domain>
       </domain-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors>
               <certificates src="system" />
           </trust-anchors>
       </base-config>
   </network-security-config>
   ```

2. Update `AndroidManifest.xml`:
   ```xml
   android:networkSecurityConfig="@xml/network_security_config"
   ```
   Remove `android:usesCleartextTraffic="true"`.

**Files**: new `res/xml/network_security_config.xml`, `AndroidManifest.xml`

---

### 3.3 Remove Unused Permissions

**Problem**: `FOREGROUND_SERVICE` and `POST_NOTIFICATIONS` declared but `ServerService` is a no-op stub.

**Fix**: Remove permissions and service declaration from manifest until embedded mode is implemented.

**File**: `AndroidManifest.xml`

---

### 3.4 Proper Release Signing

**Problem**: Release build type uses debug keystore. Anyone can decompile and resign the APK.

**Fix**:

1. Generate a proper release keystore in CI using a secret:
   ```yaml
   - name: Decode release keystore
     run: echo "${{ secrets.RELEASE_KEYSTORE_BASE64 }}" | base64 --decode > app/release.keystore
   ```

2. Update `build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file(System.getenv("KEYSTORE_FILE") ?: "debug.keystore")
           storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "android"
           keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
           keyPassword = System.getenv("KEY_PASSWORD") ?: "android"
       }
   }
   buildTypes {
       release {
           signingConfig = signingConfigs.getByName("release")
       }
   }
   ```

3. For now (no production keystore secret), keep debug signing but document that this is temporary.

**Files**: `build.gradle.kts`, `.github/workflows/build.yml`

---

## Phase 4: Code Quality & Cleanup (Day 6-7)

### 4.1 Remove Dead Code

| File | Action |
|------|--------|
| `SSEClient.kt` | Wire into DI (currently unused) — see Phase 2.1 |
| `SharedComponents.kt` | Replace inline implementations in screens with these shared composables |
| `ServerService.kt` | Remove from manifest; keep file but mark as TODO for embedded mode |

### 4.2 Remove Unused Dependencies

| Dependency | File:Line | Action |
|------------|-----------|--------|
| `coil-compose` | `build.gradle.kts:94` | Remove (no image loading) |
| `markdown-compose` | `build.gradle.kts:95` | Keep if P1-1 (markdown rendering) is planned; otherwise remove |

### 4.3 Fix Anti-Patterns

| Issue | File:Line | Fix |
|-------|-----------|-----|
| `mutableStateOf` in ViewModel | `SessionListScreen.kt:69-76` | Replace with `MutableStateFlow` |
| Duplicated DataStore keys | `SettingsScreen.kt:64-69` vs `ApiModule.kt:25-28` | Create single `PreferencesKeys.kt` object |
| `collectAsState()` | `ChatScreen.kt:41-42` | Replace with `collectAsStateWithLifecycle()` |
| `URLEncoder` for query params | `OpenCodeApi.kt:141-148` | Use `HttpUrl.Builder` |
| Non-atomic StateFlow updates | `SessionRepository.kt:59`, `FileRepository.kt:62` | Use `_sessions.update { it.filter { ... } }` |
| `getAppVersion()` stub | `SettingsScreen.kt:352-359` | Read from `PackageInfo` via `context.packageManager.getPackageInfo(context.packageName, 0).versionName` |
| `viewModelScope.launch` from composable | `SettingsScreen.kt:297` | Move to ViewModel method |

### 4.4 Enable ProGuard for Release

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(...)
    }
}
```

Add keep rules for serialization models, Hilt, and OkHttp SSE.

**File**: `build.gradle.kts`, `proguard-rules.pro`

### 4.5 Add Unit Tests

Create test directory structure:
```
app/src/test/
  └── java/ai/opencode/android/
      ├── api/OpenCodeApiTest.kt
      ├── data/SessionRepositoryTest.kt
      └── ui/chat/ChatViewModelTest.kt
```

Write tests for:
- `SessionRepository` — message loading, sending, error handling
- `ChatViewModel` — loading state, error propagation
- `OpenCodeApi` — URL construction, header injection
- `extractMessageText()` — text extraction from Parts

### 4.6 Update CI Pipeline

```yaml
# Add to build.yml
- name: Run lint
  run: ./gradlew lintDebug

- name: Run unit tests
  run: ./gradlew testDebugUnitTest

- name: Upload test results
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: app/build/reports/tests/
```

**File**: `.github/workflows/build.yml`

---

## Appendix A: Complete Issue Tracker

### Critical (P0) — App is Broken

| # | File | Line(s) | Issue | Phase |
|---|------|---------|-------|-------|
| 1 | `ChatScreen.kt` | 240-242 | `extractMessageText()` returns role name not content | 1.1 |
| 2 | `SessionRepository.kt` | 39-44 | Sent messages never appear in UI | 1.2 |
| 3 | `OpenCodeApi.kt` | 60-84 | Response bodies never closed (connection leaks) | 1.3 |
| 4 | `OpenCodeApi.kt` | 63,71 | `response.body!!` force-null-assertion (NPE crash) | 1.4 |
| 5 | `ApiModule.kt` | 33 | `runBlocking` on main thread (ANR risk) | 1.5 |
| 6 | `SessionRepository.kt` | 23-28,30-37 | Silent exception swallowing | 1.6 |
| 7 | `ChatScreen.kt` | 266-274 | No try/catch on sendMessage; isLoading stuck on error | 1.7 |
| 8 | `SettingsScreen.kt` | 89 vs `ApiModule.kt:39` | Default connection mode mismatch | 1.8 |
| 9 | `OpenCodeApi` | N/A | Frozen singleton — settings changes require restart | 2.3 |
| 10 | `SSEClient.kt` | N/A | Entire class is dead code — no real-time updates | 2.1 |
| 11 | `PermissionDialog.kt` | 134-153 | PermissionOverlay never integrated | 2.2 |
| 12 | `SessionRepository.kt` | 46-48 | New sessions don't appear in list | 2.4 |

### High (P1) — Security & Significant Functional Issues

| # | File | Line(s) | Issue | Phase |
|---|------|---------|-------|-------|
| 13 | `AndroidManifest.xml` | 16 | Cleartext traffic allowed globally | 3.2 |
| 14 | `ApiModule.kt` | 26 | Password stored plaintext | 3.1 |
| 15 | `build.gradle.kts` | 44 | Release signed with debug key | 3.4 |
| 16 | `OpenCodeApi.kt` | 50 | `x-opencode-directory` header missing from SSE client | 2.1 |
| 17 | `SessionListScreen.kt` | 69-76 | `mutableStateOf` in ViewModel | 4.3 |

### Medium (P2) — Code Quality

| # | File | Line(s) | Issue | Phase |
|---|------|---------|-------|-------|
| 18 | `SharedComponents.kt` | N/A | 6 composables defined, none used | 4.1 |
| 19 | `SettingsScreen.kt` | 64-69 | Duplicated DataStore key definitions | 4.3 |
| 20 | `SettingsScreen.kt` | 352-359 | `getAppVersion()` is a stub | 4.3 |
| 21 | `OpenCodeApi.kt` | 141-148 | `URLEncoder` instead of `HttpUrl.Builder` | 4.3 |
| 22 | `ChatScreen.kt` | 41-42 | `collectAsState()` instead of `collectAsStateWithLifecycle()` | 4.3 |
| 23 | `SessionRepository.kt` | 59 | Non-atomic StateFlow update | 4.3 |
| 24 | `FileRepository.kt` | 62 | Non-atomic StateFlow update | 4.3 |
| 25 | `SettingsScreen.kt` | 297 | `viewModelScope.launch` from composable | 4.3 |
| 26 | `build.gradle.kts` | 94-95 | Unused dependencies (coil, markdown) | 4.2 |
| 27 | `AndroidManifest.xml` | 6-7 | Unused FOREGROUND_SERVICE permission | 3.3 |
| 28 | `build.gradle.kts` | 25-30 | ProGuard disabled in release | 4.4 |
| 29 | N/A | N/A | No unit tests | 4.5 |
| 30 | `.github/workflows/build.yml` | N/A | CI doesn't run lint or tests | 4.6 |

---

## Appendix B: File Change Matrix

| File | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|---------|---------|---------|---------|
| `OpenCodeApi.kt` | 1.3, 1.4 | 2.1 | | 4.3 |
| `Models.kt` | 1.1 | | | |
| `SessionRepository.kt` | 1.2, 1.6, 1.8 | 2.1, 2.4 | | 4.3 |
| `FileRepository.kt` | 1.6 | | | 4.3 |
| `ChatScreen.kt` | 1.1, 1.7 | 2.1 | | 4.3 |
| `SessionListScreen.kt` | | | | 4.3 |
| `SettingsScreen.kt` | 1.8 | 2.3 | 3.1 | 4.3 |
| `ApiModule.kt` | 1.5, 1.8 | 2.3 | 3.1 | 4.3 |
| `ServerConfig.kt` | 1.8 | | | |
| `SSEClient.kt` | | 2.1 | | |
| `PermissionDialog.kt` | | 2.2 | | |
| `MainActivity.kt` | | 2.2 | | |
| `Navigation.kt` | | 2.2 | | |
| `AndroidManifest.xml` | | | 3.2, 3.3 | |
| `build.gradle.kts` | | | 3.1, 3.4 | 4.2, 4.4 |
| `build.yml` | | | 3.4 | 4.6 |
| new `ApiProvider.kt` | 1.5 | 2.3 | | |
| new `EventBus.kt` | | 2.1, 2.2 | | |
| new `PreferencesKeys.kt` | | | | 4.3 |
| new `network_security_config.xml` | | | 3.2 | |
| new test files | | | | 4.5 |
