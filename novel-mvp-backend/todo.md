# Novel MVP Backend - TODO List
## 🛡️ 에러 처리 및 안정성

### 4. 에러 처리 개선
- [ ] ElevenLabs API 실패 시 폴백 처리 구현
    - [ ] API 할당량 초과 시 대응 방안
    - [ ] 네트워크 오류 시 로컬 TTS 대안 고려
- [ ] WebSocket 연결 끊김 시 리소스 정리 개선
    - [ ] finally 블록에서 명확한 리소스 해제
    - [ ] Channel 닫기 및 컨텍스트 정리

### 9. 리소스 관리
- [ ] `ElevenLabsService`의 `httpClient` 생명주기 관리
    - [ ] Application 종료 시 `close()` 호출 보장
- [ ] Application shutdown hook 구현
  ```kotlin
  Runtime.getRuntime().addShutdownHook(Thread {
      // 모든 서비스 정리
      elevenLabsService.close()
  })
  ```

## ⚙️ 설정 및 환경

### 5. 설정 파일 개선
- [ ] `application.yaml` 확장
  ```yaml
  ktor:
    deployment:
      port: 8080
    application:
      modules:
        - com.novel.ApplicationKt.module
  
  services:
    elevenLabs:
      apiKey: ${ELEVENLABS_API_KEY}
      baseUrl: "https://api.elevenlabs.io/v1"
      defaultVoiceId: "21m00Tcm4TlvDq8ikWAM"
      timeout: 30000
    
  logging:
    level: INFO
    agents:
      conversation: DEBUG
      emotion: DEBUG
      story: INFO
  ```

### 7. Gradle 설정 최적화
- [ ] `gradle.properties` 파일 생성 및 설정 추가
  ```properties
  kotlin.code.style=official
  kotlin.incremental=true
  org.gradle.parallel=true
  org.gradle.caching=true
  org.gradle.configuration-cache=true
  ```

## 📡 통신 및 메시징

### 8. WebSocket 메시지 타입 개선
- [ ] 메시지 버전 관리 전략 수립
- [ ] 클라이언트를 위한 TypeScript 타입 정의 파일 생성

## 🔒 보안

### 10. 보안 강화
- [ ] WebSocket 인증/인가 구현
    - [ ] JWT 토큰 기반 인증
    - [ ] 연결 시 토큰 검증
- [ ] Rate limiting 구현
    - [ ] IP 기반 제한
    - [ ] 사용자별 제한
- [ ] WebSocket 메시지 검증
    - [ ] 메시지 크기 제한
    - [ ] 악성 입력 필터링

## 🚀 성능 및 확장성

### 11. 성능 최적화
- [ ] 대화 컨텍스트 영속성
    - [ ] Redis 또는 데이터베이스 저장 구현
    - [ ] 세션 복구 메커니즘
- [ ] 메모리 사용량 모니터링
    - [ ] JVM 메트릭 수집
    - [ ] 메모리 누수 체크
- [ ] 연결 풀링 최적화
    - [ ] HTTP 클라이언트 연결 풀 설정
    - [ ] 동시 연결 수 제한

## 📊 모니터링 및 로깅

### 12. 로깅 시스템 개선
- [ ] 구조화된 로깅 도입
    - [ ] JSON 형식 로거 설정
    - [ ] MDC (Mapped Diagnostic Context) 활용
- [ ] 에이전트별 로그 레벨 분리
  ```xml
  <logger name="com.novel.agents.ConversationAgent" level="DEBUG"/>
  <logger name="com.novel.agents.EmotionAnalysisAgent" level="DEBUG"/>
  <logger name="com.novel.agents.StoryGenerationAgent" level="INFO"/>
  ```
- [ ] 성능 메트릭 로깅
    - [ ] API 응답 시간
    - [ ] 에이전트 처리 시간
    - [ ] WebSocket 메시지 처리 시간

## 🏗️ 아키텍처 개선

### 추가 고려사항
- [ ] 의존성 주입 프레임워크 도입 검토 (Koin, Dagger)
- [ ] 이벤트 기반 아키텍처로 전환 검토
- [ ] 도메인 모델과 DTO 분리
- [ ] Repository 패턴 도입 (대화 저장용)

## 📦 배포 준비

### DevOps
- [ ] Dockerfile 작성
- [ ] Docker Compose 설정 (Fish-Speech 포함)
- [ ] CI/CD 파이프라인 구성
- [ ] 환경별 설정 분리 (dev, staging, prod)
- [ ] 헬스체크 엔드포인트 추가

## 📝 문서화

### Documentation
- [ ] API 문서 자동화 (OpenAPI/Swagger)
- [ ] 아키텍처 다이어그램 작성
- [ ] 개발자 가이드 작성
- [ ] 운영 가이드 작성

---

## 우선순위 (Priority)

### 🔴 높음 (High)
1. 에러 처리 개선 (4)
2. 리소스 관리 (9)
3. 테스트 코드 작성 (6)
4. WebSocket 메시지 타입 개선 (8)

### 🟡 중간 (Medium)
5. 설정 파일 개선 (5)
6. 보안 강화 (10)
7. 로깅 시스템 개선 (12)

### 🟢 낮음 (Low)
8. Import 정리 (3)
9. Gradle 설정 최적화 (7)
10. 성능 최적화 (11)
11. 기타 아키텍처 개선 사항

---

**Note**: 각 항목은 독립적으로 진행 가능하며, 팀의 상황과 요구사항에 따라 우선순위를 조정하세요.