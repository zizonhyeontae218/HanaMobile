# LiteRT-LM backend integration (Android)

This repository uses a real on-device local inference backend based on the official LiteRT-LM Kotlin API.

## Dependency

- Gradle artifact: `com.google.ai.edge.litertlm:litertlm-android:0.10.1`
- Declared in: `app/build.gradle.kts`

The backend uses typed LiteRT-LM classes directly (`Engine`, `EngineConfig`, `ConversationConfig`, `SamplerConfig`).

## Where backend code lives

- Backend runtime implementation: `app/src/main/java/com/hanamobile/domain/service/inference/LiteRtLmLocalInferenceBackend.kt`
- Generation validator/helper: `app/src/main/java/com/hanamobile/domain/service/inference/GenerationConfigValidator.kt`
- Prompt adapter/formatter: `app/src/main/java/com/hanamobile/domain/service/inference/LiteRtLmPromptFormatter.kt`
- Model loading/path validation: `app/src/main/java/com/hanamobile/domain/service/inference/LiteRtLmModelLoader.kt`
- Local model directory scanner: `app/src/main/java/com/hanamobile/domain/service/inference/LocalModelCatalog.kt`
- Shared model rules: `app/src/main/java/com/hanamobile/domain/service/inference/LiteRtLmModelSupport.kt`
- Backend config + errors: `app/src/main/java/com/hanamobile/core/model/BackendConfig.kt`
- App bootstrap wiring: `app/src/main/java/com/hanamobile/app/HanaApplication.kt`

## Model directory strategy (`Android/media/.../models`)

The app scans model files from app media storage:

- `${externalMediaDir}/models`
- Typical path: `/storage/emulated/0/Android/media/com.hanamobile/models`

From Prompt Settings screen, users can refresh model list and choose an active model file by filename.

Supported file extensions:

- **`.litertlm` only**

Filename acceptance policy (shared by catalog + loader):

- plain filename only (no `/`, `\\`, no traversal)
- regex: `^[A-Za-z0-9._-]+$`
- cannot start with `.`
- extension must be `.litertlm`

## Runtime behavior

- Engine initialization is performed on a background coroutine dispatcher.
- Engine is reused while the selected **canonical model path** is unchanged.
- Reinitialization happens only when there is no engine yet or the canonical model path changed.
- On model switch, existing engine is closed and a new engine is initialized.
- Each generation request creates a new LiteRT-LM conversation.
- Streaming path is available through `generateStream(...)` using LiteRT-LM async message flow.

## Generation settings

Configured in `app/build.gradle.kts` as BuildConfig fields and mapped in backend provider:

- `LITERT_MAX_TOKENS`
- `LITERT_TOP_K`
- `LITERT_TOP_P`
- `LITERT_TEMPERATURE`
- `LITERT_RANDOM_SEED`

Validation is centralized in `GenerationConfigValidator` and fails early on invalid values.

## Execution target configuration

`BackendConfig.runtime.executionTarget` is currently a **reserved runtime signature field** for future CPU/GPU/NPU rollout.

- default: `CPU_COMPAT`
- placeholders: `GPU_PREFERRED`, `NPU_PREFERRED`

In `litertlm-android:0.10.1` usage in this repo, engine creation still uses `EngineConfig(modelPath=...)`. The execution target is not yet applied to engine construction arguments; it is included in the engine runtime signature to prevent cache/reuse bugs when target selection becomes active later.

## Failure troubleshooting

- **No models visible**: ensure files are in `Android/media/com.hanamobile/models` and use `.litertlm`.
- **Model file missing**: selected file was removed; choose another model in Prompt Settings.
- **Invalid model selection**: filename violates policy (unsafe chars/path traversal pattern).
- **Model initialization fails**: model/device/runtime mismatch, invalid model package, or native init failure.
- **Generation fails**: check prompt length/model compatibility and generation parameter values.
- **Engine lifecycle error**: engine close/recreate operation failed during model switch or shutdown.
