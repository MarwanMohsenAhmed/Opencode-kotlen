# OpenCode Android — Product Requirements Document

**Version**: 1.0  
**Date**: 2026-04-25  
**Status**: Draft  
**Repository**: https://github.com/MarwanMohsenAhmed/Opencode-kotlen

---

## 1. Overview

### 1.1 Product Vision

OpenCode Android is a native mobile client for the OpenCode AI coding agent. It provides developers with a full-featured, always-available interface to interact with AI coding assistants from their Android device — whether connected to a local embedded server or a remote OpenCode instance.

### 1.2 Problem Statement

Developers using OpenCode are tethered to desktop environments (Tauri/Electron apps or CLI). There is no way to monitor ongoing AI coding sessions, approve permission requests, review code changes, or start new conversations from a mobile device. This limits flexibility and slows down workflows where a developer steps away from their desk.

### 1.3 Target Users

| Persona | Description |
|---------|-------------|
| **Desktop Developer** | Already uses OpenCode on their workstation. Wants mobile access to monitor sessions, approve permissions, and send quick prompts while away from desk. |
| **Remote Server User** | Runs OpenCode on a remote server (headless/SSH). Needs a GUI client to interact with it. |
| **On-Device User** | Wants to run OpenCode entirely on an Android device (ARM64, Termux-style). Niche but growing segment. |

### 1.4 Key Differentiators

- **Native performance** — Kotlin + Jetpack Compose, not a web wrapper
- **Real-time streaming** — SSE-based token-by-token rendering of AI responses
- **Permission flow** — Approve/deny tool calls (file edits, shell commands) from your phone
- **Embedded mode** — Run OpenCode server directly on the device (future)
- **Terminal access** — WebSocket PTY streaming for shell interaction (future)

---

## 2. Functional Requirements

### 2.1 P0 — Must Have (MVP)

These requirements define the minimum viable product. Without these, the app is not usable.

#### 2.1.1 Chat — Functional Message Display

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| P0-1 | Display message content (text parts) for both user and assistant messages | Critical | **Broken** — `extractMessageText()` only shows role name |
| P0-2 | Fetch and render `Part` objects for each message via `/session/{id}/part` | Critical | **Not implemented** |
| P0-3 | Render text parts with proper formatting (paragraphs, whitespace) | Critical | **Not implemented** |
| P0-4 | Render tool-call parts showing tool name and input summary | High | **Not implemented** |
| P0-5 | Render tool-result parts showing success/failure and output | High | **Not implemented** |
| P0-6 | Show user messages in chat immediately after sending | Critical | **Broken** — messages never appear |
| P0-7 | Auto-refresh messages after sending (poll or SSE) | Critical | **Not implemented** |

#### 2.1.2 SSE — Real-Time Event Streaming

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| P0-8 | Subscribe to SSE event stream when viewing a session | Critical | **Dead code** — `SSEClient` exists but never used |
| P0-9 | Append new assistant message parts as they stream in | Critical | **Not implemented** |
| P0-10 | Update loading state when generation completes (SSE event) | High | **Not implemented** |
| P0-11 | Handle SSE connection failures with automatic reconnection | High | **Not implemented** |
| P0-12 | Show "streaming" indicator while AI is generating | Medium | Partial — `isLoading` exists but never set by SSE |

#### 2.1.3 Session Management

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| P0-13 | List sessions with title, date, and model info | High | Partially working |
| P0-14 | Create new session and have it appear in the list | Critical | **Broken** — list not refreshed after creation |
| P0-15 | Delete session with confirmation dialog | High | Working |
| P0-16 | Pull-to-refresh session list | Medium | Working |

#### 2.1.4 Permission Flow

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| P0-17 | Show permission request dialog when AI needs tool approval | Critical | **Dead code** — `PermissionDialog`/`PermissionOverlay` exist but never integrated |
| P0-18 | Allow user to approve or deny permission requests | Critical | **Not connected** |
| P0-19 | Poll for pending permissions or receive via SSE | High | **Not implemented** |

#### 2.1.5 Connection & Settings

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| P0-20 | Settings changes take effect immediately (no app restart) | Critical | **Broken** — `OpenCodeApi` is frozen singleton |
| P0-21 | Test connection functionality | Medium | Working |
| P0-22 | Show connection status indicator | Medium | **Not implemented** |

### 2.2 P1 — Should Have

#### 2.2.1 Chat Enhancements

| ID | Requirement | Priority |
|----|-------------|----------|
| P1-1 | Markdown rendering in assistant messages (code blocks, bold, lists) | High |
| P1-2 | Syntax highlighting in code blocks | High |
| P1-3 | Copy code block content to clipboard | Medium |
| P1-4 | Diff view for file edit tool results | Medium |
| P1-5 | Agent selection dropdown when sending a message | Medium |
| P1-6 | Session title editing | Low |
| P1-7 | Message timestamp display | Low |
| P1-8 | Cost/token display per message | Low |

#### 2.2.2 Code Viewer

| ID | Requirement | Priority |
|----|-------------|----------|
| P1-9 | Syntax highlighting in code viewer | High |
| P1-10 | File search UI (file name and content search) | Medium |
| P1-11 | Navigate to files from chat tool results | Medium |

#### 2.2.3 Project Context

| ID | Requirement | Priority |
|----|-------------|----------|
| P1-12 | Display VCS branch info in session header | Medium |
| P1-13 | Display project directory info | Low |

#### 2.2.4 Error Handling

| ID | Requirement | Priority |
|----|-------------|----------|
| P1-15 | Show error snackbar/banner when API calls fail | High |
| P1-16 | Retry failed operations | Medium |
| P1-17 | Offline detection and network state monitoring | Medium |
| P1-18 | Connection timeout with user feedback | Low |

### 2.3 P2 — Nice to Have

| ID | Requirement | Priority |
|----|-------------|----------|
| P2-1 | Embedded server mode (run OpenCode binary on device) | High (complex) |
| P2-2 | Terminal/PTY access via WebSocket | Medium |
| P2-3 | MCP server management UI | Low |
| P2-4 | Provider/model configuration UI | Low |
| P2-5 | Session sharing | Low |
| P2-6 | Push notifications for permission requests | Low |
| P2-7 | Multi-server profile support | Low |
| P2-8 | Biometric auth for app access | Low |
| P2-9 | Widget for quick prompt submission | Low |

---

## 3. Non-Functional Requirements

### 3.1 Security

| ID | Requirement | Priority | Current State |
|----|-------------|----------|---------------|
| NFR-1 | Password must be encrypted at rest (EncryptedDataStore or Android Keystore) | Critical | Plaintext in DataStore |
| NFR-2 | Network security config restricting cleartext to localhost only | Critical | `usesCleartextTraffic="true"` globally |
| NFR-3 | Release APK must use proper production signing key | High | Debug-signed |
| NFR-4 | Remove unused `FOREGROUND_SERVICE` permission | Medium | Declared but unused |
| NFR-5 | Add ProGuard minification for release builds | Medium | Disabled |

### 3.2 Performance

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-6 | App cold start < 2 seconds | High |
| NFR-7 | Message list scroll at 60fps with 1000+ messages | High |
| NFR-8 | SSE reconnection within 3 seconds of disconnect | Medium |
| NFR-9 | No `runBlocking` on main thread | Critical |

### 3.3 Reliability

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-10 | Zero silent exception swallowing in repositories | Critical |
| NFR-11 | Proper OkHttp response body closing (no connection leaks) | Critical |
| NFR-12 | No force-null assertions on nullable API responses | Critical |
| NFR-13 | Atomic StateFlow updates (use `update {}` instead of read-modify-write) | High |

### 3.4 Compatibility

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-14 | minSdk 26 (Android 8.0) | Set |
| NFR-15 | targetSdk 35 (Android 15) | Set |
| NFR-16 | Support both light and dark themes | Working |

### 3.5 Quality

| ID | Requirement | Priority |
|----|-------------|----------|
| NFR-17 | Unit tests for Repository and ViewModel layers | High |
| NFR-18 | CI pipeline runs lint and tests | Medium |
| NFR-19 | No unused dependencies in build.gradle.kts | Low |

---

## 4. User Flows

### 4.1 Remote Connection Setup (Primary)

```
1. User installs app, opens it
2. App shows "No sessions" with settings prompt
3. User navigates to Settings
4. User enters remote server URL (e.g., http://192.168.1.100:4096)
5. User enters password
6. User selects "Remote" mode
7. User taps "Test Connection" → sees "Connected: Healthy (v1.14.24)"
8. User navigates back → session list loads from server
```

### 4.2 Chat with AI Agent

```
1. User taps session from list (or creates new session)
2. Chat screen opens, loads message history
3. Messages display with proper content (text, code blocks, tool calls)
4. User types prompt in input bar, taps send
5. User message appears immediately in chat
6. "Streaming" indicator shows
7. Assistant response streams in token-by-token via SSE
8. Tool calls appear as collapsible cards (tool name, input, output)
9. When generation completes, streaming indicator disappears
```

### 4.3 Permission Approval

```
1. AI agent attempts a tool call that requires permission
2. Permission dialog appears with tool name, description, and input preview
3. User taps "Allow" or "Deny"
4. Dialog dismisses, AI agent continues or stops
5. If another permission is pending, next dialog appears
```

### 4.4 View Code from Chat

```
1. AI assistant mentions a file or edits a file
2. File path appears as tappable link in message
3. User taps link → Code Viewer screen opens
4. Code displays with syntax highlighting and line numbers
5. User can copy code to clipboard
6. User navigates back to chat
```

---

## 5. Technical Architecture

### 5.1 Architecture Pattern

**MVVM with Repository layer**:

```
┌─────────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────┐
│  Composable  │────>│   ViewModel   │────>│  Repository   │────>│  OpenCodeApi │
│  (UI Layer)  │<────│  (StateFlow)  │<────│  (Data Layer) │<────│  (Network)   │
└─────────────┘     └──────────────┘     └──────────────┘     └────────────┘
                                                  │                    │
                                           ┌──────┴──────┐     ┌──────┴──────┐
                                           │  DataStore   │     │  SSE Stream  │
                                           │  (Settings)  │     │  (Events)    │
                                           └─────────────┘     └─────────────┘
```

### 5.2 Key Components

| Layer | Component | Responsibility |
|-------|-----------|---------------|
| UI | Composable screens | Render state, capture user input |
| UI | ViewModels | Hold UI state, coordinate repositories |
| Data | Repositories | Manage in-memory state, call API, handle errors |
| Data | DataStore | Persist user settings (encrypted) |
| Network | OpenCodeApi | REST API calls with OkHttp |
| Network | SSEClient | Server-Sent Events streaming |
| DI | Hilt modules | Provide dependencies, manage API lifecycle |

### 5.3 API Instance Lifecycle (Required Fix)

The `OpenCodeApi` must NOT be a frozen singleton. It must be recreatable when settings change:

```
SettingsViewModel.setServerUrl()
  → Saves to DataStore
  → Notifies ApiProvider
  → ApiProvider closes old SSE connections
  → ApiProvider creates new OpenCodeApi with updated config
  → Repositories receive new API instance
```

### 5.4 SSE Event Flow (Required Implementation)

```
OpenCodeApi.subscribeEvents()
  → SSEClient.connect() → Flow<EventMessage>
  → SessionRepository processes events
    → "message.part" → append part to current message
    → "message.complete" → mark generation complete
    → "permission" → add to PermissionRepository.pendingPermissions
    → "session.update" → refresh session list
  → ViewModels collect StateFlows → UI recomposes
```

---

## 6. Release Milestones

### Milestone 1: Working Chat (v1.1.0)

- Fix all P0 critical bugs
- Implement message content display with Part fetching
- Implement SSE streaming for real-time chat
- Implement permission flow
- Fix settings to take effect without restart
- Security fixes: encrypted storage, network security config

### Milestone 2: Enhanced Experience (v1.2.0)

- Markdown rendering in messages
- Syntax highlighting in code blocks and code viewer
- Agent selection
- Error feedback UI (snackbars, error states)
- File search UI
- VCS branch display
- Proper release signing

### Milestone 3: Embedded Mode (v2.0.0)

- Bundle OpenCode ARM64 binary in APK
- Foreground service to manage server lifecycle
- Server log viewer
- Automatic port discovery
- Health monitoring and auto-restart

---

## 7. Success Metrics

| Metric | Target |
|--------|--------|
| App crash rate | < 1% of sessions |
| Chat message display | 100% of messages show content (vs 0% currently) |
| SSE connection reliability | > 99% uptime during active session |
| Settings change latency | < 500ms to take effect (vs requiring restart) |
| Permission response time | < 2 seconds from request to user seeing dialog |
| Cold start time | < 2 seconds |
