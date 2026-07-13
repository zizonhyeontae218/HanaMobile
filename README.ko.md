# HanaMobile

Kotlin과 Jetpack Compose로 만든 로컬 우선 Android 비서 플랫폼입니다.

## 제안하는 모듈·패키지 구조

- `app/`
  - `app/` 애플리케이션 부트스트랩과 DI 컨테이너
  - `core/model/` 표준 데이터 모델(`ChatMessage`, `ChatSession`, `PromptPreset` 등)
  - `core/session/` 세션 조립 파이프라인(`SessionManager`)
  - `core/extensions/` 백엔드/STT/TTS/파형/도구/멀티모달/소울 인터페이스
  - `domain/repository/` 리포지터리 추상화
  - `domain/service/` 오케스트레이션 서비스(`MemoryManager`)와 모의 엔진
  - `domain/service/inference/` 로컬 모델 백엔드 연동(LiteRT-LM 및 교체 가능한 대안)
  - `data/local/` Room 데이터베이스/DAO/엔티티
  - `data/repository/` 리포지터리 구현체와 매퍼
  - `ui/viewmodel/` ViewModel과 StateFlow UI 상태
  - `ui/navigation/` Compose 내비게이션 그래프
  - `ui/screen/` 채팅, 음성, 프롬프트, 메모리, 저장 채팅 화면

## 아키텍처 요약

- **의존성 역전**: UI와 SessionManager는 `LocalInferenceBackend`, `SpeechToTextEngine` 등의 인터페이스에 의존합니다.
- **세션 조립 파이프라인**: 요청 페이로드는 명시적인 계층으로 조립됩니다.
  1. 시스템 프롬프트
  2. 메모리 주입 블록
  3. 이전 채팅 기록
  4. 사용자 입력
  5. 선택적 도구 결과
  6. 선택적 멀티모달 페이로드
  7. 선택적 소울 엔진 맥락·검토 훅
- **영속성**: 프롬프트 프리셋, 메모리 항목, 채팅, 메시지는 리포지터리 인터페이스를 거쳐 Room에 저장됩니다.
- **상태 모델**: 채팅, 프롬프트 관리, 메모리 관리, 저장 채팅, 음성 파이프라인에 `ViewModel + StateFlow`를 사용합니다.

## 통합 지점

- `LocalInferenceBackend`는 **공식 타입드 LiteRT-LM API**(`Engine`, `EngineConfig`, `ConversationConfig`, `SamplerConfig`)를 쓰는 `LiteRtLmLocalInferenceBackend`에 연결됩니다.
- 추후 llama.cpp / ExecuTorch / MediaPipe 대안을 위해 백엔드는 교체 가능하게 유지합니다.
- 필요에 따라 `MockSpeechToTextEngine`, `MockTextToSpeechEngine`을 플랫폼 또는 내장 모델로 교체합니다.

## LiteRT-LM 백엔드 빠른 설정

- 의존성: `app/build.gradle.kts`의 `com.google.ai.edge.litertlm:litertlm-android:0.10.1`
- 모델 디렉터리: `Android/media/com.hanamobile/models`(`models/` 안의 파일을 앱 내에서 선택)
- 지원하는 로컬 모델 파일: `.litertlm`
- 백엔드 연결: `HanaApplication`이 `BackendConfig`와 `LocalModelCatalog`를 만들고, `LiteRtLmLocalInferenceBackend`를 `SessionManager`에 주입합니다.
- 문제 해결과 모델 교체는 `docs/litert-lm-backend.md`를 참고하세요.
