# OpenCode Android

Native Android client for the OpenCode AI coding agent.

## Tech Stack

- **Kotlin** — Primary language
- **Jetpack Compose** — Declarative UI toolkit
- **Material3** — Design system
- **Hilt** — Dependency injection
- **Retrofit** — HTTP client

## Features

- Chat with AI
- Code viewer
- Session management
- Embedded server mode
- Remote server mode

## Architecture

```
┌──────────────┐     ┌────────────┐     ┌─────────────────┐
│  Android App │────▶│  REST API  │────▶│  OpenCode Server│
└──────────────┘     └────────────┘     └─────────────────┘
```

## Setup

1. Open the project in Android Studio
2. Sync Gradle
3. Run on device or emulator
