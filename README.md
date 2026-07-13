# HanaMobile

> **한국어:** [README.ko.md](README.ko.md)

Local-first Android assistant platform in Kotlin + Jetpack Compose.

## Proposed module/package structure

- `app/`
  - `app/` application bootstrap + DI container
  - `core/model/` canonical data models (`ChatMessage`, `ChatSession`, `PromptPreset`, etc.)
  - `core/session/` session assembly pipeline (`SessionManager`)
  - `core/extensions/` backend/STT/TTS/waveform/tools/multimodal/soul interfaces
  - `domain/repository/` repository abstractions
  - `domain/service/` orchestration services (`MemoryManager`) + mock engines
  - `domain/service/inference/` local-model backend integrations (LiteRT-LM and swappable alternatives)
  - `data/local/` Room database/DAO/entities
  - `data/repository/` repository implementations + mappers
  - `ui/viewmodel/` ViewModel + StateFlow UI states
  - `ui/navigation/` compose navigation graph
  - `ui/screen/` chat, voice, prompt, memory, saved-chats screens

## Architecture summary

- **Dependency inversion**: UI and SessionManager depend on interfaces (`LocalInferenceBackend`, `SpeechToTextEngine`, etc.).
- **Session assembly pipeline**: Request payload is assembled with explicit layers:
  1. system prompt
  2. memory injection block
  3. prior chat history
  4. user input
  5. optional tool results
  6. optional multimodal payload
  7. optional soul-engine context/review hooks
- **Persistence**: Prompt presets, memory entries, chats, and messages are persisted via Room through repository interfaces.
- **State model**: `ViewModel + StateFlow` for chat, prompt management, memory management, saved chats, and voice pipeline.

## Integration points

- `LocalInferenceBackend` is wired to `LiteRtLmLocalInferenceBackend` with the **official typed LiteRT-LM API** (`Engine`, `EngineConfig`, `ConversationConfig`, `SamplerConfig`).
- Backend remains swappable for future llama.cpp / ExecuTorch / MediaPipe alternatives.
- Replace `MockSpeechToTextEngine` and `MockTextToSpeechEngine` with platform or embedded models as needed.

## LiteRT-LM backend quick setup

- Dependency: `com.google.ai.edge.litertlm:litertlm-android:0.10.1` in `app/build.gradle.kts`.
- Model directory: `Android/media/com.hanamobile/models` (in-app model selection from files in `models/`).
- Supported local model files: **`.litertlm` only**.
- Backend wiring: `HanaApplication` uses `LiteRtLmBackendProvider` to build `BackendConfig` + `LocalModelCatalog` and inject `LiteRtLmLocalInferenceBackend` into `SessionManager`.
- See `docs/litert-lm-backend.md` for troubleshooting and model swapping.
- 엔진 재초기화 조건: 엔진 미존재, 또는 runtime signature( canonical model path + runtime config ) 변경시에만 재초기화
- `executionTarget`는 현재 예약 필드이며, 이 버전에서는 엔진 생성 인자로 아직 직접 반영되지 않음

## Model filename policy

For 안전한 로컬 파일 선택(path traversal 방지) 때문에 모델 선택 문자열은 아래 규칙을 따릅니다.

- plain filename only (디렉터리 구분자 금지)
- 허용 문자: `A-Z a-z 0-9 . _ -`
- dotfile 금지 (`.hidden.litertlm` 불가)
- 확장자는 `.litertlm`만 허용

이 정책은 `LocalModelCatalog`와 `LiteRtLmModelLoader`에서 동일하게 사용됩니다.
