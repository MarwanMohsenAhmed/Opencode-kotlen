# OpenCode Android — Feature Improvements & Future Roadmap

**Date**: 2026-04-25  
**Based on**: Analysis of OpenCode server API (80+ endpoints) vs current Android client coverage  

---

## Current Feature Coverage

The OpenCode server exposes 80+ endpoints across 15 route groups. The Android client currently implements a fraction of them, and most of what's implemented is broken or incomplete.

| Server Capability | Endpoints | Android Coverage | Status |
|-------------------|-----------|------------------|--------|
| Session CRUD | 7 | 5 called | Partially working |
| Chat/Prompt | 2 | 2 called | **Broken** — content not displayed |
| SSE Events | 2 | 0 connected | Dead code |
| Permission | 3 | 2 called | **Not integrated** |
| Question (agent Q&A) | 3 | 0 called | Not started |
| Agent | 1 | 1 called | Not connected to UI |
| File operations | 5 | 3 called | Partial |
| VCS/Git | 2 | 1 called | Not shown in UI |
| Config | 2 | 0 called | Not started |
| MCP | 7 | 0 called | Not started |
| Provider | 3 | 0 called | Not started |
| PTY/Terminal | 4 | 0 called | Not started |
| Project | 3 | 0 called | Not started |
| Sync | 3 | 0 called | Not started |
| TUI controls | 9 | 0 called | N/A (terminal-only) |
| Experimental | 6 | 0 called | Not started |

**Coverage**: ~15 of 80+ endpoints called, ~5 working end-to-end.

---

## Milestone 1: Working Chat (v1.1.0)

*Target: App is usable for basic chat with an AI agent on a remote server.*

### 1.1 Message Part Rendering

The server stores message content as `Part` objects, not in the `Message` itself. Each message can have multiple parts of different types.

**Part types to render**:

| Part Type | Display | Priority |
|-----------|---------|----------|
| `text` | Formatted text paragraph | P0 |
| `tool-call` | Collapsible card with tool name, input preview | P0 |
| `tool-result` | Collapsible card with success/failure badge, output preview | P0 |
| `thinking` | Collapsible "Thinking..." section | P1 |
| `image` | Inline image via Coil | P2 |
| `diff` | Unified diff with syntax highlighting | P1 |
| `error` | Red error banner | P0 |

**Implementation**:

1. Add `listParts()` API call — `GET /session/{id}/message/{msgId}/part`
2. Fetch parts when loading a session's messages
3. Create composable `PartRenderer` that switches on `part.type`:
   ```kotlin
   @Composable
   fun PartRenderer(part: Part, onViewFile: (String) -> Unit) {
       when (part.type) {
           "text" -> TextPart(part)
           "tool-call" -> ToolCallPart(part)
           "tool-result" -> ToolResultPart(part)
           "error" -> ErrorPart(part)
           "thinking" -> ThinkingPart(part)
           else -> UnknownPart(part)
       }
   }
   ```
4. Update `MessageBubble` to iterate `message.parts` and render each

**New files**: `ui/chat/PartRenderer.kt`  
**Modified**: `OpenCodeApi.kt`, `Models.kt`, `SessionRepository.kt`, `ChatScreen.kt`

---

### 1.2 SSE-Driven Chat Streaming

**Current state**: User sends a message, nothing appears. Must manually reload.

**Target state**: 
1. User sends message → message appears immediately
2. Assistant response streams in token-by-token
3. Tool calls appear as they happen
4. Generation complete → loading indicator disappears

**SSE Event Types** (from server):

| Event | Payload | Action |
|-------|---------|--------|
| `message.part` | Part object | Append part to current message |
| `message.complete` | Message ID | Mark generation as finished |
| `session.update` | Session object | Refresh session in list |
| `permission` | Permission request | Show permission dialog |
| `question` | Question request | Show question dialog |

**Implementation**:

1. `EventBus` singleton connects to `/event` SSE endpoint
2. `SessionRepository` observes `EventBus.events` and updates `_messages`
3. For per-session events, connect to `/session/{id}/event` when viewing a chat
4. Auto-reconnect on failure with exponential backoff (1s, 2s, 4s, 8s, max 30s)
5. Show "Reconnecting..." indicator when SSE is down

**New files**: `api/EventBus.kt`  
**Modified**: `ApiModule.kt`, `SSEClient.kt`, `SessionRepository.kt`, `ChatViewModel.kt`

---

### 1.3 Permission & Question Flow

**Permission Flow**:
- AI agent calls `bash`, `edit`, or `write` tool → server creates permission request
- SSE event `"permission"` fires → `EventBus` receives it
- `PermissionRepository.refresh()` is called → updates `pendingPermissions` StateFlow
- `PermissionOverlay` composable detects first pending → shows `PermissionDialog`
- User taps Allow/Deny → `replyPermission()` called → next pending shown

**Question Flow** (new):
- AI agent asks user a question via `question` tool
- SSE event `"question"` fires
- Show `QuestionDialog` with question text and text input for reply
- User submits answer → `POST /question/{id}/reply`

**New files**: `ui/permissions/QuestionDialog.kt`, `ui/permissions/PermissionHostViewModel.kt`  
**Modified**: `EventBus.kt`, `MainActivity.kt`, `OpenCodeApi.kt` (add question endpoints)

---

## Milestone 2: Enhanced Experience (v1.2.0)

*Target: App feels polished and professional. Rich content rendering.*

### 2.1 Markdown Rendering

**Current**: `markdown-compose` dependency exists but is unused. Messages render as plain text.

**Implementation**:
- Use `com.halilibo.compose-markdown` (already in dependencies) to render assistant text parts
- Configure with monospace code blocks, syntax highlighting integration
- Handle code blocks specially: copy button, language label
- Handle links: tappable file paths → code viewer, URLs → open in browser

**Modified**: `ChatScreen.kt` (PartRenderer)

---

### 2.2 Syntax Highlighting

**Current**: Code viewer and code blocks in chat have no syntax highlighting.

**Implementation**:
- Add `com.halilibo.compose-markdown` syntax highlighting extension
- Or use `multiplatform-markdown-renderer` with code highlighting
- Support common languages: Kotlin, Python, TypeScript, Go, Rust, JSON, YAML
- Theme: match app dark/light theme

**New dependency**: syntax highlight library  
**Modified**: `ChatScreen.kt`, `CodeViewerScreen.kt`

---

### 2.3 Agent Selection

**Current**: `AgentChip` composable exists in `SharedComponents.kt` but agent selection is not connected.

**Implementation**:
- Add agent dropdown/selector above chat input bar
- Fetch agents via `GET /agent` on session open
- Default: "code" agent
- Show agent name + model info in selector
- Pass selected agent to `sendMessage()` API call

**Modified**: `ChatScreen.kt`, `SessionRepository.kt`

---

### 2.4 Error Feedback UI

**Current**: All errors are silently swallowed. User sees empty states with no explanation.

**Implementation**:
- Add `SnackbarHost` to `MainActivity` scaffold
- ViewModels expose `error: StateFlow<String?>`
- When error occurs, show snackbar with "Retry" action
- Add dedicated error views: connection lost, server unreachable, auth failed
- Add "No connection" banner at top of screens when server is unreachable

**Modified**: `MainActivity.kt`, all ViewModels, all Screens

---

### 2.5 File Search UI

**Current**: `FileRepository.searchFiles()` and `searchText()` exist but no UI calls them.

**Implementation**:
- Add search icon to session list top bar
- Search screen with two tabs: "Files" (by name) and "Content" (by pattern)
- Results displayed as list with file path, line number, and text preview
- Tap result → open in Code Viewer

**New files**: `ui/search/FileSearchScreen.kt`, `ui/search/FileSearchViewModel.kt`  
**Modified**: `Navigation.kt`

---

### 2.6 VCS Branch Display

**Current**: `getVcs()` API call exists but result is never shown.

**Implementation**:
- Show branch name in session detail/chat header
- Color-code: main/default branch vs feature branch
- Tap to see diff summary (additions/deletions/files changed from `SessionDetail`)

**Modified**: `ChatScreen.kt` (TopAppBar), `SessionListScreen.kt`

---

### 2.7 Session Title Editing

**Current**: `updateSession()` API method exists but no UI to rename.

**Implementation**:
- Long-press session in list → "Rename" option
- Edit title in-place or via dialog
- Call `PATCH /session/{id}` with new title

**Modified**: `SessionListScreen.kt`, `SessionRepository.kt`

---

## Milestone 3: Configuration & MCP (v1.3.0)

*Target: Users can configure the AI agent from their phone.*

### 3.1 Config Screen

**Server endpoints**: `GET /config`, `PATCH /config`

**Implementation**:
- Display current config: providers, models, MCP servers, permissions
- Allow editing: custom instructions, permission rules, enabled/disabled providers
- Changes saved via `PATCH /config`
- Live preview of config changes

**New files**: `ui/config/ConfigScreen.kt`, `ui/config/ConfigViewModel.kt`  
**Modified**: `Navigation.kt`, `OpenCodeApi.kt`

---

### 3.2 MCP Server Management

**Server endpoints**: `GET /mcp/status`, `POST /mcp/add`, OAuth flow endpoints

**Implementation**:
- List connected MCP servers with status (connected/disconnected)
- Add new MCP server (stdio, SSE, or streamable HTTP)
- Remove MCP server
- OAuth flow for remote MCP servers (start → callback → authenticate)
- View MCP-provided tools

**New files**: `ui/mcp/McpScreen.kt`, `ui/mcp/McpViewModel.kt`  
**Modified**: `Navigation.kt`, `OpenCodeApi.kt`

---

### 3.3 Provider & Model Selection

**Server endpoints**: `GET /provider`, `GET /provider/auth`, OAuth flow

**Implementation**:
- List available providers (Anthropic, OpenAI, Google, etc.)
- Show auth status per provider
- OAuth authorization flow
- Model selection per session or per agent
- Cost tracking per session

**New files**: `ui/provider/ProviderScreen.kt`, `ui/provider/ProviderViewModel.kt`  
**Modified**: `Navigation.kt`, `OpenCodeApi.kt`

---

## Milestone 4: Embedded Mode (v2.0.0)

*Target: OpenCode runs entirely on the Android device — no remote server needed.*

### 4.1 Bundle OpenCode Binary

**Approach**:
- Build OpenCode for `linux-arm64` using Bun's ARM64 support
- Bundle the binary in APK's `assets/` directory
- Extract to internal storage on first launch
- Verify binary integrity (checksum)

**Challenges**:
- Bun ARM64 Android support is experimental
- Binary size (~80MB compressed) will increase APK significantly
- Android SELinux may restrict execution from app-private directories
- May require Termux-style approach (shared UID or companion app)

**Alternative approach**:
- Use Termux as the runtime environment
- Install Bun + OpenCode via Termux package manager
- App connects to the embedded server via localhost
- Less seamless but more reliable

---

### 4.2 Server Lifecycle Management

**Implementation**:
- `ServerService` (foreground service) manages server process
- Start/stop server from Settings
- Server process: `Runtime.exec()` or `ProcessBuilder`
- Port discovery: server writes port to known file, app reads it
- Health monitoring: periodic `/global/health` checks
- Auto-restart on crash
- Server logs viewer (scrolling log output)

**Modified**: `ServerService.kt`, `SettingsScreen.kt`, `AndroidManifest.xml` (re-add permissions)

---

### 4.3 Terminal Access

**Server endpoints**: `POST /pty`, `WebSocket /pty/{id}/ws`

**Implementation**:
- WebView or custom terminal emulator view
- WebSocket connection to PTY endpoint
- Keyboard input forwarding
- Terminal output rendering (ANSI escape code handling)
- Multiple terminal sessions support

**New files**: `ui/terminal/TerminalScreen.kt`, `ui/terminal/TerminalViewModel.kt`, `api/TerminalClient.kt`  
**Modified**: `Navigation.kt`, `AndroidManifest.xml`

---

## Milestone 5: Advanced Features (v2.1.0+)

### 5.1 Session Sharing

**Server endpoint**: `POST /session/{id}/share`

- Generate share link for session
- Share via Android share sheet
- View shared sessions from others

### 5.2 Push Notifications

- Firebase Cloud Messaging for permission requests when app is backgrounded
- Requires server-side FCM integration (not currently in OpenCode server)
- Alternative: periodic polling via WorkManager

### 5.3 Multi-Server Profiles

- Save multiple server configurations (work, home, cloud)
- Quick-switch between profiles
- Per-profile connection mode, URL, password, directory

### 5.4 Biometric Authentication

- Require fingerprint/face unlock to open the app
- Protect stored passwords with Android Keystore + biometric
- Optional: per-session biometric for sensitive operations

### 5.5 Home Screen Widget

- Quick-prompt submission widget
- Active session status indicator
- Pending permissions count badge

### 5.6 Session Compaction

**Server endpoint**: `POST /session/{id}/compact`

- Trigger context compaction for long sessions
- View compaction summary

### 5.7 Session Revert

**Server endpoint**: `POST /session/{id}/revert`

- Undo last message/exchange
- Confirmation dialog with preview of what will be reverted

### 5.8 Sync & Control Plane

**Server endpoints**: `POST /sync/start`, `GET /sync/history`

- Sync sessions to OpenCode cloud
- View sync history
- Multi-device session continuity

---

## Architecture Improvements Roadmap

### Current Architecture Issues

| Issue | Impact | When to Fix |
|-------|--------|-------------|
| No Room/SQLite cache | Every screen hit requires network call; no offline support | v1.3.0 |
| No offline support | App useless without active server connection | v1.3.0 |
| Single `:app` module | No build-time isolation; compile times grow with codebase | v2.0.0 |
| No effect handlers | Side effects (navigation, toasts) mixed into composables | v1.2.0 |
| No navigation animation | Jarring screen transitions | v1.2.0 |
| No deep links | Can't open specific session from notification/URL | v2.0.0 |

### Proposed Multi-Module Architecture (v2.0.0)

```
:app                    (Android application, Hilt setup, navigation)
:core:api               (OpenCodeApi, SSEClient, Models)
:core:data              (Repositories, DataStore, Room database)
:core:domain            (Use cases, business logic)
:feature:chat           (ChatScreen, ChatViewModel, PartRenderer)
:feature:sessions       (SessionListScreen, SessionListViewModel)
:feature:settings       (SettingsScreen, SettingsViewModel)
:feature:codeviewer     (CodeViewerScreen, CodeViewerViewModel)
:feature:permissions    (PermissionDialog, PermissionOverlay)
:feature:terminal       (TerminalScreen, TerminalViewModel)
:feature:config         (ConfigScreen, ConfigViewModel)
:feature:mcp            (McpScreen, McpViewModel)
```

### Proposed Offline Support (v1.3.0)

```
┌──────────┐     ┌───────────┐     ┌──────────┐
│  Server   │────>│  Room DB   │────>│   UI     │
│  (REST)   │<────│  (Cache)   │<────│          │
└──────────┘     └───────────┘     └──────────┘
      │                │
      │          ┌─────┴─────┐
      │          │ Sync Log  │
      └──────────┤ (Offline  │
         SSE     │  Queue)   │
                   └───────────┘
```

- Room database caches sessions, messages, and parts
- `PagingSource` for message list (infinite scroll)
- Write operations queued offline and replayed when connectivity returns
- `WorkManager` for periodic sync

---

## OpenCode Server API Coverage Plan

### Currently Implemented (15 endpoints)

| Endpoint | Method | Used From |
|----------|--------|-----------|
| `/global/health` | GET | SettingsScreen (test connection) |
| `/session` | GET | SessionRepository.refreshSessions |
| `/session` | POST | SessionRepository.createSession |
| `/session/{id}` | GET | SessionRepository.loadSession |
| `/session/{id}` | DELETE | SessionRepository.deleteSession |
| `/session/{id}/message` | GET | SessionRepository.loadSession |
| `/session/{id}/message` | POST | SessionRepository.sendMessage |
| `/session/{id}/abort` | POST | SessionRepository.abortSession |
| `/agent` | GET | (called but not used) |
| `/vcs` | GET | (called but not shown) |
| `/file/content` | GET | FileRepository.loadFile |
| `/find/file` | GET | FileRepository.searchFiles |
| `/find` | GET | FileRepository.searchText |
| `/permission` | GET | PermissionRepository.refresh |
| `/permission/{id}/reply` | POST | PermissionRepository.reply |

### Milestone 1 Additions (4 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/event` | SSE | Global event stream |
| `/session/{id}/event` | SSE | Per-session event stream |
| `/session/{id}/message/{msgId}/part` | GET | Message content parts |
| `/question/{id}/reply` | POST | Answer agent questions |

### Milestone 2 Additions (3 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/session/{id}` | PATCH | Rename session |
| `/find/symbol` | GET | LSP symbol search |
| `/session/{id}/compact` | POST | Context compaction |

### Milestone 3 Additions (10 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/config` | GET | Read config |
| `/config` | PATCH | Update config |
| `/mcp/status` | GET | MCP server status |
| `/mcp/add` | POST | Add MCP server |
| `/mcp/{id}/remove` | POST | Remove MCP server |
| `/mcp/{id}/connect` | POST | Connect MCP server |
| `/mcp/{id}/disconnect` | POST | Disconnect MCP server |
| `/provider` | GET | List providers |
| `/provider/{id}/auth` | GET | Provider auth methods |
| `/provider/{id}/oauth/authorize` | GET | Start OAuth flow |

### Milestone 4 Additions (4 endpoints)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/pty` | POST | Create PTY session |
| `/pty/{id}` | DELETE | Delete PTY session |
| `/pty/{id}/ws` | WebSocket | Terminal I/O |
| `/project` | GET | List projects |

---

## Dependency Changes

### Additions

| Dependency | Version | Purpose | Milestone |
|------------|---------|---------|-----------|
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Encrypted password storage | v1.1.0 |
| `androidx.room:room-runtime` + KSP | 2.6.1 | Local database caching | v1.3.0 |
| `androidx.paging:paging-compose` | 3.3.5 | Infinite scroll for messages | v1.3.0 |
| `androidx.work:work-runtime-ktx` | 2.9.1 | Periodic sync, offline queue | v1.3.0 |
| Terminal emulator library | TBD | PTY rendering | v2.0.0 |

### Removals

| Dependency | Reason | Milestone |
|------------|--------|-----------|
| `coil-compose` | No image loading in app | v1.2.0 (or wire it in) |
| `markdown-compose` | Currently unused | v1.2.0 (or wire it in) |

### Upgrades

| Dependency | Current | Target | Reason |
|------------|---------|--------|--------|
| `okhttp` | 4.12.0 | 5.0.0-alpha | OkHttp 5 has better SSE support |
| `compose-bom` | 2024.12.01 | Latest | New Compose features |

---

## CI/CD Improvements

### Current

- Build debug APK on push
- Build release APK on tag
- No lint, no tests, no code quality checks

### Target

```yaml
on:
  push:
    branches: [main, dev]
  pull_request:
    branches: [main, dev]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - checkout + JDK 17 + Gradle
      - ./gradlew lintDebug
      - Upload lint report

  test:
    runs-on: ubuntu-latest
    steps:
      - checkout + JDK 17 + Gradle
      - ./gradlew testDebugUnitTest
      - Upload test report

  build:
    needs: [lint, test]
    runs-on: ubuntu-latest
    steps:
      - Build debug APK
      - Upload artifact

  release:
    needs: [lint, test, build]
    if: startsWith(github.ref, 'refs/tags/v')
    steps:
      - Build release APK (proper signing)
      - Create GitHub Release
      - Generate release notes from commits

  distribute:
    needs: [release]
    if: startsWith(github.ref, 'refs/tags/v')
    steps:
      - Upload to Google Play Internal Testing (optional)
```

### Version Automation

- Extract `versionCode` and `versionName` from git tag
- `v1.2.3` → `versionName = "1.2.3"`, `versionCode = 10203`
- Auto-increment versionCode for non-tag commits

---

## Summary Timeline

| Milestone | Target | Key Deliverables |
|-----------|--------|------------------|
| **v1.1.0** — Working Chat | Week 1-2 | Fix all critical bugs, SSE streaming, permission flow, message content display |
| **v1.2.0** — Enhanced UX | Week 3-4 | Markdown, syntax highlighting, agent selection, error UI, file search |
| **v1.3.0** — Configuration | Week 5-7 | Config screen, MCP management, provider selection, offline cache (Room) |
| **v2.0.0** — Embedded Mode | Week 8-12 | Bundled server binary, foreground service, terminal access, multi-module architecture |
| **v2.1.0+** — Advanced | Week 13+ | Session sharing, push notifications, biometrics, widgets, sync |
