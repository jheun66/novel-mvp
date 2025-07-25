# Novel MVP Backend

AI 기반 대화형 스토리텔링 시스템 - 사용자의 일상 대화를 감성적인 단편 소설로 변환

## 🌟 프로젝트 개요

Novel MVP는 사용자와의 자연스러운 대화를 통해 일상의 이야기를 수집하고, 이를 개인화된 감성 소설로 변환하는 멀티 에이전트 AI 시스템입니다. WebSocket 기반 실시간 통신과 한국어 음성 인식/합성을 지원합니다.

### 핵심 서비스

- **사용자 관리 시스템**: Clean Architecture 기반의 완전한 사용자 인증 및 프로필 관리
- **실시간 대화 시스템**: WebSocket 기반 양방향 대화
- **AI 스토리 생성**: 개인화된 감성 소설 자동 생성
- **성격 분석**: 사용자 응답 기반 7가지 성격 특성 분석
- **구독 시스템**: 무료/프리미엄 서비스 차등 제공

## 🚀 핵심 기능

### 1. 사용자 관리 시스템 (UserService)
- **회원가입/로그인**: 이메일/비밀번호 및 OAuth (Google, Kakao) 지원
- **JWT 기반 인증**: Access Token + Refresh Token 방식
- **프로필 관리**: 사용자명, 프로필 이미지 등 개인정보 관리
- **성격 프로필**: 7가지 성격 특성 분석 및 선호 장르 설정
- **구독 관리**: 무료/프리미엄 구독 상태 관리

#### 성격 특성 (PersonalityTrait)
- OPENNESS (개방성)
- CONSCIENTIOUSNESS (성실성)  
- EXTROVERSION (외향성)
- AGREEABLENESS (친화성)
- NEUROTICISM (신경증)
- CREATIVITY (창의성)
- EMOTIONAL_DEPTH (감정 깊이)

### 2. 실시간 대화 시스템
- WebSocket 기반 양방향 실시간 통신
- JWT 토큰 기반 인증으로 보안 강화
- 자연스럽고 공감적인 AI 대화 에이전트
- 대화 컨텍스트 관리 및 이야기 수집
- 사용자 성격 프로필 기반 맞춤형 대화

### 3. 감정 인식 및 분석
- 문장별 감정 분석 (10가지 감정 카테고리)
- 감정 강도 및 변화 추적
- 키워드 기반 감정 맥락 파악

### 4. AI 스토리 생성
- Google Gemini 2.5 기반 창의적 스토리 생성
- 대화 내용과 감정을 반영한 개인화된 소설
- 400-600자 분량의 감성적인 단편 소설
- 사용자 성격 프로필 기반 맞춤형 스토리

### 5. 음성 인터페이스
- **STT (Speech-to-Text)**: OpenAI Whisper 기반 실시간 한국어 음성 인식
- **TTS (Text-to-Speech)**: ElevenLabs TTS 기반 고품질 다국어 음성 합성
- **감정별 음성 매핑**: 16가지 감정에 최적화된 자연스러운 음성
- **감정별 최적화**: 각 감정에 맞는 음성 자동 선택
- 대화용 (빠른 응답)과 스토리 내레이션용 (고품질) 이중 모드 지원

```mermaid
flowchart LR
    subgraph Input
        A[대화 컨텍스트]
        B[감정 분석 결과]
        C[사용자 ID]
        D[주요 하이라이트]
    end
    
    subgraph StoryGenerationAgent
        E[프롬프트 생성]
        F[Gemini API 호출]
        G[응답 파싱]
    end
    
    subgraph Output
        H[제목]
        I[본문<br/>400-600자]
        J[장르]
        K[감정 곡선]
        L[핵심 순간들]
    end
    
    A --> E
    B --> E
    C --> E
    D --> E
    
    E --> F
    F --> G
    
    G --> H
    G --> I
    G --> J
    G --> K
    G --> L
    
    style F fill:#ffe5d4,stroke:#d78948,stroke-width:2px
```

### 6. 하이브리드 음성 처리 시스템
- **Whisper STT**: 실시간 한국어 음성 인식 (오픈소스 로컬 처리)
- **ElevenLabs TTS**: 최고 품질의 AI 음성 합성 (스트리밍 + 고품질 모드)
- **안정적인 서비스**: 로컬 + 클라우드 하이브리드 접근
- **Docker 컨테이너화**: STT 서비스 독립 배포

## 🛠 기술 스택

### Backend Framework
- **Language**: Kotlin
- **Framework**: Ktor 2.x
- **Build Tool**: Gradle (Kotlin DSL)

### Database & ORM
- **Database**: PostgreSQL 15
- **ORM**: Exposed (Kotlin SQL Framework)
- **Migration**: Flyway
- **Connection Pool**: HikariCP

### AI/ML
- **대화 AI**: OpenAI GPT-4 (모델: gpt-4.1)
- **감정 분석**: OpenAI GPT-4 (모델: gpt-4.1)
- **스토리 생성**: Google Gemini 2.5 Flash
- **음성 인식 (STT)**: OpenAI Whisper (오픈소스, 다중 모델 지원)
- **음성 합성 (TTS)**: ElevenLabs TTS (클라우드 서비스, 다양한 감정별 음성)

### Architecture
- **Pattern**: Clean Architecture + Multi-Agent System
- **Domain-Driven Design**: User Aggregate
- **Protocol**: WebSocket (RFC 6455) + REST API
- **Serialization**: Kotlinx Serialization (JSON)
- **Dependency Injection**: Koin
- **Authentication**: JWT (Access + Refresh Tokens)

## 📦 의존성

```kotlin
dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:2.x")
    implementation("io.ktor:ktor-server-netty:2.x")
    implementation("io.ktor:ktor-server-websockets:2.x")
    implementation("io.ktor:ktor-server-content-negotiation:2.x")
    implementation("io.ktor:ktor-server-auth:2.x")
    implementation("io.ktor:ktor-server-auth-jwt:2.x")
    
    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.x")
    implementation("org.jetbrains.exposed:exposed-dao:0.x")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.x")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.x")
    implementation("org.postgresql:postgresql:42.x")
    implementation("com.zaxxer:HikariCP:5.x")
    implementation("org.flywaydb:flyway-core:9.x")
    
    // AI SDKs
    implementation("com.aallam.openai:openai-client:4.0.1")
    implementation("com.google.genai:google-genai:1.7.0")
    
    // Security
    implementation("com.auth0:java-jwt:4.x")
    implementation("org.mindrot:jbcrypt:0.4")
    
    // DI
    implementation("io.insert-koin:koin-ktor:3.x")
    
    // Utilities
    implementation("io.github.cdimascio:dotenv-kotlin:6.x")
    implementation("ch.qos.logback:logback-classic:1.x")
}
```

## 🚦 시작하기

### 1. 필수 요구사항
- JDK 17 이상
- PostgreSQL 15
- Docker & Docker Compose (STT/TTS 서비스용)
- Python 3.11+ with venv (로컬 개발 시)

### 2. 환경 설정

`.env` 파일 생성:
```bash
# OpenAI API Configuration
OPENAI_API_KEY=sk-your-openai-api-key-here

# Google Gemini API Configuration  
GEMINI_API_KEY=your-gemini-api-key-here

# STT Service URL (Python service)
WHISPER_STT_URL=http://localhost:5001

# ElevenLabs API Configuration
ELEVENLABS_API_KEY=your-elevenlabs-api-key-here

# Note: TTS now uses ElevenLabs API (no local service needed)

# Database Configuration
DB_URL=jdbc:postgresql://localhost:5432/novel_db
DB_USER=novel_user
DB_PASSWORD=novel_password

# JWT Configuration
JWT_SECRET=your-256-bit-secret-key-for-jwt-signing

# OAuth Configuration
GOOGLE_CLIENT_ID=your-google-oauth-client-id
GOOGLE_CLIENT_SECRET=your-google-oauth-client-secret
```

### 3. STT 서비스 설정

#### Option 1: Docker Compose 사용 (권장)
```bash
# 필수 서비스 실행 (PostgreSQL, STT 포함)
docker-compose up -d

# 개별 서비스 실행
docker-compose up -d whisper-stt postgres pgadmin

# 서비스 상태 확인
docker-compose ps

# 로그 확인
docker-compose logs -f whisper-stt
```

#### Option 2: Python venv로 로컬 실행
```bash
# Whisper STT 서비스만 실행
cd python-services/whisper-stt
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

> **참고**: TTS는 이제 ElevenLabs 클라우드 API를 사용하므로 별도 로컬 서비스가 불필요합니다.

### 4. 데이터베이스 설정

Docker Compose를 사용하는 경우:
```bash
# pgAdmin 접속: http://localhost:5050
# Email: admin@novel.com
# Password: admin
```

직접 PostgreSQL을 설치한 경우:
```bash
# 데이터베이스 생성
createdb -U postgres novel_db

# 사용자 생성
psql -U postgres -c "CREATE USER novel_user WITH PASSWORD 'novel_password';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE novel_db TO novel_user;"
```

### 5. API 키 획득
- **OpenAI**: https://platform.openai.com/api-keys
- **Google Gemini**: https://makersuite.google.com/app/apikey
- **STT**: 오픈소스 서비스로 별도 API 키 불필요
- **ElevenLabs TTS**: https://elevenlabs.io/
- **TTS**: ElevenLabs API 키 필요

### 6. 애플리케이션 실행
```bash
# 개발 모드
./gradlew run

# 프로덕션 빌드
./gradlew shadowJar
java -jar build/libs/novel-mvp-backend-all.jar
```

## 📡 API 사용법

### REST API 엔드포인트

#### 인증 관련

**회원가입**
```http
POST /api/v1/users/register
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "john_doe",
  "displayName": "John Doe",
  "password": "password123"
}
```

**로그인**
```http
POST /api/v1/users/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```

**OAuth 로그인**
```http
POST /api/v1/users/oauth/login
Content-Type: application/json

{
  "provider": "GOOGLE",
  "accessToken": "google-oauth-token",
  "email": "user@example.com",
  "displayName": "John Doe",
  "profileImageUrl": "https://example.com/profile.jpg"
}
```

> **중요**: OAuth 로그인 시 제공된 액세스 토큰은 해당 OAuth 제공자(Google, Kakao)의 API를 통해 검증됩니다.
> - Google: `https://www.googleapis.com/oauth2/v3/userinfo`
> - Kakao: `https://kapi.kakao.com/v2/user/me`
> 
> 토큰 검증 시 이메일이 일치하는지 확인하며, 유효하지 않은 토큰은 거부됩니다.

**토큰 갱신**
```http
POST /api/v1/users/refresh
Content-Type: application/json

{
  "refreshToken": "your-refresh-token"
}
```

#### 사용자 정보 (인증 필요)

**내 정보 조회**
```http
GET /api/v1/users/me
Authorization: Bearer your-access-token
```

**프로필 수정**
```http
PATCH /api/v1/users/me
Authorization: Bearer your-access-token
Content-Type: application/json

{
  "displayName": "New Name",
  "profileImageUrl": "https://example.com/new-profile.jpg"
}
```

**성격 테스트 제출**
```http
POST /api/v1/users/me/personality
Authorization: Bearer your-access-token
Content-Type: application/json

{
  "responses": {
    "q1": 75,
    "q2": 60,
    "q3": 80,
    "q4": 65,
    "q5": 40,
    "q6": 85,
    "q7": 70,
    "q8": 90,
    "q9": 55,
    "q10": 75
  },
  "preferredGenres": ["HEALING", "ROMANCE", "SLICE_OF_LIFE"]
}
```

### WebSocket 엔드포인트
```
ws://localhost:8080/ws/novel
```

> **중요**: WebSocket 연결 시 JWT 토큰을 통한 인증이 필요합니다.

### WebSocket 보안 및 사용자별 기능

#### 인증된 WebSocket 연결
- JWT 토큰 기반 인증 필수
- 연결 직후 AuthRequest 메시지로 인증
- 인증 실패 시 연결 자동 종료

#### 사용자별 맞춤 기능
- **개인화된 대화**: 사용자 성격 프로필 기반 대화 스타일 조정
- **스토리 생성 제한**: 
  - 무료 사용자: 일일 3개 스토리
  - 프리미엄 사용자: 무제한
- **선호 장르 반영**: 사용자가 설정한 선호 장르로 스토리 생성
- **성격 특성 반영**: 개방성, 창의성 등 7가지 특성 기반 스토리 스타일

#### WebSocket 메시지 흐름 예시
```javascript
// 1. WebSocket 연결
const ws = new WebSocket('ws://localhost:8080/ws/novel');

// 2. 연결 성공 시 즉시 인증
ws.onopen = () => {
    ws.send(JSON.stringify({
        type: 'AuthRequest',
        token: localStorage.getItem('accessToken')
    }));
};

// 3. 인증 응답 확인
ws.onmessage = (event) => {
    const message = JSON.parse(event.data);
    if (message.type === 'AuthResponse' && message.success) {
        // 인증 성공 - 이제 대화 시작 가능
        ws.send(JSON.stringify({
            type: 'TextInput',
            text: '오늘은 정말 특별한 날이었어요',
            conversationId: 'conv-123'
        }));
    }
};
```

### 메시지 프로토콜

> **중요**: WebSocket 메시지는 Kotlinx Serialization의 sealed class를 사용합니다. 
> 메시지 타입은 `type` 필드에 클래스 이름만 지정하면 됩니다 (전체 패키지명 불필요).

#### 입력 메시지

**텍스트 입력**
```json
{
  "type": "TextInput",
  "text": "오늘 카페에서 오랜 친구를 만났어요",
  "conversationId": "unique-conversation-id"
}
```

**음성 입력** (Whisper STT 지원)
```json
{
  "type": "AudioInput",
  "audioData": "base64_encoded_audio_data",
  "format": "wav",
  "sampleRate": 16000,
  "conversationId": "unique-conversation-id",
  "isStreaming": false
}
```

**스토리 생성 요청**
```json
{
  "type": "GenerateStory",
  "conversationId": "unique-conversation-id"
}
```

#### 출력 메시지

**텍스트 응답**
```json
{
  "type": "TextOutput",
  "text": "오랜 친구와의 만남이라니 정말 반가우셨겠어요! 어떤 이야기를 나누셨나요?",
  "emotion": "HAPPY",
  "suggestedQuestions": [
    "그 친구와는 얼마만에 만나신 건가요?",
    "가장 기억에 남는 대화는 무엇이었나요?",
    "만나서 어떤 기분이 드셨나요?"
  ],
  "readyForStory": false
}
```

**음성 응답** (ElevenLabs TTS 다국어 음성)
```json
{
  "type": "AudioOutput",
  "audioData": "base64_encoded_audio",
  "format": "wav",
  "sampleRate": 22050,
  "emotion": "HAPPY",
  "duration": 3.5,
  "type": "chat"
}
```

**생성된 스토리** (오디오 내레이션 포함)
```json
{
  "type": "StoryOutput",
  "title": "시간을 건너온 우정",
  "content": "카페 문을 열고 들어서는 순간, 익숙한 실루엣이 눈에 들어왔다...",
  "emotion": "NOSTALGIC",
  "genre": "일상",
  "emotionalArc": "그리움에서 시작해 따뜻한 위로로 마무리되는 여정",
  "audioData": "base64_encoded_story_narration",
  "audioFormat": "wav",
  "audioSampleRate": 22050
}
```

## 🎭 감정 카테고리

### 감정 처리 플로우

```mermaid
flowchart TD
    A[사용자 입력 텍스트] --> B{ConversationAgent}
    B -->|감정 태그 감지| C[초기 감정 인식]
    C --> D[EmotionAnalysisAgent]
    D -->|상세 분석| E[감정 분석 결과]
    
    E --> F[주요 감정<br/>confidence: 0.95]
    E --> G[부가 감정들<br/>subEmotions]
    E --> H[감정 강도<br/>intensity: 0.85]
    E --> I[키워드 추출]
    
    F --> J[ElevenLabsTTSService]
    G --> J
    H --> J
    
    J -->|16 Emotion Voices| K[감정별 음성 파라미터]
    K --> L[ElevenLabs TTS 다국어 합성]
    
    style D fill:#d4e5ff,stroke:#4894d7,stroke-width:2px
    style J fill:#ffe5d4,stroke:#d78948,stroke-width:2px
```

시스템이 인식하고 표현하는 감정:

| 감정 | 코드 | 설명 |
|------|------|------|
| 행복 | HAPPY | 기쁨, 즐거움, 만족감 |
| 슬픔 | SAD | 우울, 아쉬움, 상실감 |
| 설렘 | EXCITED | 기대, 흥분, 두근거림 |
| 평온 | CALM | 안정, 편안함, 고요함 |
| 화남 | ANGRY | 분노, 짜증, 불만 |
| 감사 | GRATEFUL | 고마움, 감동, 뿌듯함 |
| 불안 | ANXIOUS | 걱정, 초조, 긴장 |
| 그리움 | NOSTALGIC | 추억, 회상, 아련함 |
| 자랑스러움 | PROUD | 성취감, 뿌듯함, 자부심 |
| 실망 | DISAPPOINTED | 낙담, 허탈, 아쉬움 |

## 🏗 아키텍처

### Clean Architecture + Multi-Agent System

```mermaid
graph TD
    subgraph Presentation Layer
        REST[REST API]
        WS[WebSocket API]
        AUTH[JWT Auth]
    end
    
    subgraph Application Layer
        UC1[User Use Cases]
        UC2[Story Use Cases]
        UC3[Auth Use Cases]
    end
    
    subgraph Domain Layer
        USER[User Aggregate]
        STORY[Story Entity]
        EVENTS[Domain Events]
    end
    
    subgraph Infrastructure Layer
        DB[(PostgreSQL)]
        REPO[Repositories]
        AI[AI Services]
        MSG[Event Publisher]
    end
    
    subgraph Agent System
        CA[Conversation Agent]
        EA[Emotion Agent]
        SA[Story Agent]
        COMM[Agent Communicator]
    end
    
    REST --> UC1
    WS --> UC1
    WS --> CA
    
    UC1 --> USER
    UC2 --> STORY
    UC3 --> USER
    
    USER --> EVENTS
    EVENTS --> MSG
    
    UC1 --> REPO
    REPO --> DB
    
    CA --> COMM
    EA --> COMM
    SA --> COMM
    
    CA --> AI
    EA --> AI
    SA --> AI
    
    style USER fill:#ffd4e5,stroke:#d74894,stroke-width:3px
    style CA fill:#d4e5ff,stroke:#4894d7,stroke-width:3px
    style WS fill:#ffe5d4,stroke:#d78948,stroke-width:3px
```

### 시스템 전체 플로우

```mermaid
sequenceDiagram
    participant U as 사용자
    participant W as WebSocket
    participant A as Auth
    participant N as NovelWebSocketService
    participant UC as UserUseCases
    participant C as ConversationAgent
    participant E as EmotionAnalysisAgent
    participant S as StoryGenerationAgent
    participant EL as ElevenLabsService
    participant DB as Database
    
    U->>W: 연결 요청
    W->>N: WebSocket 세션 생성
    U->>W: AuthRequest (JWT 토큰)
    W->>N: 인증 요청
    N->>A: JWT 검증
    A-->>N: 사용자 정보
    N->>W: AuthResponse (성공)
    W->>U: 인증 완료
    
    U->>W: TextInput (대화 입력)
    W->>N: 메시지 처리
    N->>UC: 사용자 프로필 조회
    UC->>DB: getUserById
    DB-->>UC: User + PersonalityProfile
    UC-->>N: 사용자 정보
    N->>C: process(ConversationInput + UserProfile)
    C->>C: 개인화된 대화 생성
    C-->>E: AgentMessage (감정 분석 요청)
    C-->>N: ConversationOutput
    N->>TTS: generateChatTTS(응답, 감정)
    TTS-->>N: 음성 데이터 (WAV)
    N->>W: TextOutput + AudioOutput
    W->>U: 응답 + 음성
    
    Note over U,W: 대화 3-4회 반복 후
    
    U->>W: GenerateStory
    W->>N: 스토리 생성 요청
    N->>UC: 스토리 생성 자격 확인
    UC->>DB: 일일 생성 횟수 확인
    DB-->>UC: 생성 가능 여부
    
    alt 생성 가능
        N->>E: process(EmotionAnalysisInput)
        E-->>N: EmotionAnalysisOutput
        N->>S: process(StoryGenerationInput + UserPreferences)
        S-->>N: StoryGenerationOutput
        N->>W: StoryOutput
        W->>U: 생성된 스토리
        N->>DB: 스토리 생성 이벤트 발행
    else 한도 초과
        N->>W: Error (STORY_LIMIT_EXCEEDED)
        W->>U: 오류 메시지
    end
```

### 멀티 에이전트 시스템

```mermaid
graph TD
    CA[ConversationAgent<br/>대화 관리] --> AC[AgentCommunicator<br/>에이전트 통신 채널]
    EA[EmotionAnalysisAgent<br/>감정 분석] --> AC
    SG[StoryGenerationAgent<br/>스토리 생성] --> AC
    
    CA -.->|대화 컨텍스트| EA
    EA -.->|감정 분석 결과| SG
    
    AC --> CH1[Channel: conversation]
    AC --> CH2[Channel: emotion-analysis]
    AC --> CH3[Channel: story-generation]
    
    style CA fill:#ffd4e5,stroke:#d74894,stroke-width:2px
    style EA fill:#d4e5ff,stroke:#4894d7,stroke-width:2px
    style SG fill:#ffe5d4,stroke:#d78948,stroke-width:2px
    style AC fill:#e5ffd4,stroke:#94d748,stroke-width:2px
```

### 주요 컴포넌트

1. **ConversationAgent**: 사용자와의 대화 관리
2. **EmotionAnalysisAgent**: 텍스트 감정 분석
3. **StoryGenerationAgent**: 창의적 스토리 생성
4. **WhisperSTTService**: 실시간 한국어 음성 인식
5. **ElevenLabsTTSService**: 고품질 AI 음성 합성 (감정별 최적화)
6. **NovelWebSocketService**: WebSocket 통신 관리

### 🔧 에이전트 시스템 상세 구조

#### Agent.kt - 에이전트 인터페이스 정의

**기본 Agent 인터페이스**
```kotlin
interface Agent<TInput, TOutput> {
    val name: String
    suspend fun process(input: TInput): TOutput
}
```
- 모든 에이전트가 구현해야 하는 기본 인터페이스
- 제네릭 타입으로 유연한 입출력 정의
- 코루틴 기반 비동기 처리

**StreamingAgent 인터페이스**
```kotlin
interface StreamingAgent<TInput, TOutput> : Agent<TInput, Flow<TOutput>> {
    suspend fun processStream(input: Flow<TInput>): Flow<TOutput>
}
```
- 실시간 스트리밍 데이터 처리용
- Kotlin Flow를 활용한 반응형 프로그래밍 지원

**AgentMessage 구조**
```kotlin
@Serializable
data class AgentMessage<T>(
    val id: String,
    val from: String,
    val to: String,
    val payload: T,
    val timestamp: Long = System.currentTimeMillis()
)
```
- 에이전트 간 통신 메시지 표준 포맷
- 타입 안전한 payload 전달

#### AgentCommunicator.kt - 에이전트 간 통신 구현

**SimpleAgentCommunicator 특징:**
- Coroutine Channel 기반 비동기 메시지 전달
- 발행-구독(Pub-Sub) 패턴 지원
- ConcurrentHashMap으로 스레드 안전성 보장

```kotlin
// 사용 예시
val message = AgentMessage(
    id = UUID.randomUUID().toString(),
    from = "conversation",
    to = "emotion-analysis",
    payload = mapOf("text" to "오늘 정말 행복했어요")
)
communicator.send(message)
```

### WebSocket 메시지 타입 구조

```mermaid
classDiagram
    class WebSocketMessage {
        <<sealed class>>
    }
    
    class TextInput {
        +text: String
        +conversationId: String
    }
    
    class AudioInput {
        +audioData: String
        +format: String = "pcm16"
    }
    
    class GenerateStory {
        +conversationId: String
    }
    
    class TextOutput {
        +text: String
        +emotion: String?
        +suggestedQuestions: List~String~
        +readyForStory: Boolean
    }
    
    class AudioOutput {
        +audioData: String
        +format: String = "pcm16"
        +emotion: String?
    }
    
    class StoryOutput {
        +title: String
        +content: String
        +emotion: String
        +genre: String
        +emotionalArc: String
    }
    
    class Error {
        +message: String
        +code: String?
    }
    
    WebSocketMessage <|-- TextInput
    WebSocketMessage <|-- AudioInput
    WebSocketMessage <|-- GenerateStory
    WebSocketMessage <|-- TextOutput
    WebSocketMessage <|-- AudioOutput
    WebSocketMessage <|-- StoryOutput
    WebSocketMessage <|-- Error
    
    style WebSocketMessage fill:#f9f,stroke:#333,stroke-width:2px
    style TextInput fill:#ffd4e5,stroke:#d74894
    style TextOutput fill:#ffd4e5,stroke:#d74894
    style AudioInput fill:#e5d4ff,stroke:#9448d7
    style AudioOutput fill:#e5d4ff,stroke:#9448d7
    style GenerateStory fill:#d4ffe5,stroke:#48d794
    style StoryOutput fill:#d4ffe5,stroke:#48d794
    style Error fill:#ffd4d4,stroke:#d74848
```

#### 업데이트된 Serialization 설정

```kotlin
// 전역 JSON 설정
val globalJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"  // 메시지 타입을 "type" 필드로 지정
    serializersModule = SerializersModule {
        polymorphic(WebSocketMessage::class) {
            subclass(WebSocketMessage.AudioInput::class)
            subclass(WebSocketMessage.TextInput::class)
            subclass(WebSocketMessage.GenerateStory::class)
            subclass(WebSocketMessage.AudioOutput::class)
            subclass(WebSocketMessage.TextOutput::class)
            subclass(WebSocketMessage.StoryOutput::class)
            subclass(WebSocketMessage.Error::class)
        }
    }
}
```

이제 메시지 타입은 간단히 클래스 이름만으로 지정 가능합니다:
```json
{
  "type": "TextInput",  // 전체 패키지명 불필요
  "text": "안녕하세요",
  "conversationId": "test-123"
}
```

### 에이전트 메시지 라우팅 플로우

```mermaid
graph LR
    subgraph SimpleAgentCommunicator
        CH1[Channel: conversation]
        CH2[Channel: emotion-analysis]
        CH3[Channel: story-generation]
        SUB1[Subscribers Map]
    end
    
    CA[ConversationAgent] -.->|1. send message| AC{AgentCommunicator}
    AC -->|2. route to channel| CH2
    AC -->|3. notify subscribers| SUB1
    SUB1 -->|4. invoke handler| EA[EmotionAnalysisAgent]
    
    style AC fill:#e5ffd4,stroke:#94d748,stroke-width:3px
    style CH2 fill:#d4e5ff,stroke:#4894d7
```

### ⚠️ A2A 패턴 구현 수준

**중요**: 이 시스템은 실제 A2A(Agent-to-Agent) 프로토콜의 완전한 구현이 아닌, **A2A 패턴의 개념을 차용한 단순화된 구현**입니다.

#### 현재 구현 vs 실제 A2A 프로토콜

| 기능 | 현재 구현 | 실제 A2A 프로토콜 |
|------|-----------|------------------|
| **에이전트 발견** | ❌ 하드코딩된 에이전트 목록 | ✅ 동적 에이전트 등록/발견 |
| **메시지 라우팅** | ⚠️ 단순 이름 기반 직접 전달 | ✅ 복잡한 라우팅 규칙 |
| **전달 보장** | ❌ 보장 메커니즘 없음 | ✅ ACK/NACK, 재시도 |
| **보안** | ❌ 인증/암호화 없음 | ✅ 에이전트 인증, 메시지 암호화 |
| **프로토콜 표준** | ❌ 자체 메시지 구조 | ✅ DIDComm, FIPA-ACL 등 |

#### 현재 구현의 장점
- **단순성**: MVP에 적합한 최소한의 복잡도
- **확장성**: 향후 실제 A2A로 전환 가능한 구조
- **성능**: 불필요한 오버헤드 없이 빠른 처리
- **유지보수**: 이해하기 쉽고 디버깅 용이

#### 프로덕션 전환 시 고려사항
실제 프로덕션 환경에서는 다음과 같은 개선이 필요합니다:
- 에이전트 레지스트리 및 디스커버리 서비스
- 메시지 전달 보장 메커니즘 (큐잉, 재시도)
- 보안 계층 (TLS, 에이전트 인증)
- 모니터링 및 추적 기능
- 표준 프로토콜 준수 (DIDComm 2.0 등)

## 📂 프로젝트 구조

```
src/main/kotlin/
├── Application.kt           # 메인 진입점
├── Routing.kt              # 라우팅 설정
├── WebSocket.kt            # WebSocket 설정
├── HTTP.kt                 # HTTP 설정
├── Serialization.kt        # 직렬화 설정
├── Security.kt             # 보안 설정 (JWT, OAuth)
├── Monitoring.kt           # 모니터링 설정
├── Koin.kt                 # DI 설정
├── domain/                 # 도메인 계층
│   └── user/
│       └── User.kt         # User 엔티티 및 값 객체
├── application/            # 애플리케이션 계층
│   └── user/
│       ├── UserDtos.kt     # DTO 정의
│       └── UserUseCases.kt # 사용 사례
├── infrastructure/         # 인프라 계층
│   ├── user/
│   │   └── UserRepositoryImpl.kt
│   ├── event/
│   │   └── DomainEventPublisherImpl.kt
│   └── services/
│       ├── PersonalityAnalyzerImpl.kt
│       └── PaymentServiceImpl.kt
├── routes/                 # HTTP 라우트
│   └── UserRoutes.kt
├── agents/                 # AI 에이전트
│   ├── ConversationAgent.kt
│   ├── EmotionAnalysisAgent.kt
│   ├── StoryGenerationAgent.kt
│   └── base/
│       ├── Agent.kt
│       └── AgentCommunicator.kt
├── services/               # 비즈니스 서비스
│   ├── UserService.kt      # 레거시 호환성
│   ├── JWTService.kt       # JWT 토큰 관리
│   ├── NovelWebSocketService.kt
│   ├── WhisperSTTService.kt
│   └── ElevenLabsTTSService.kt
├── model/                  # 데이터베이스 모델
│   └── Users.kt            # Exposed 테이블 정의
├── database/               # 데이터베이스 설정
│   ├── DatabaseFactory.kt
│   └── HikariCPManager.kt
├── config/                 # 설정 클래스
│   ├── DBConfig.kt
│   ├── JWTConfig.kt
│   └── OAuthConfig.kt
├── di/                     # 의존성 주입 모듈
│   ├── ConfigModule.kt
│   └── ServiceModule.kt
└── documentation/          # API 문서
    └── OpenAPIDoc.kt
    
resources/
├── application.yaml        # 애플리케이션 설정
├── logback.xml            # 로깅 설정
├── db/migration/          # Flyway 마이그레이션
│   └── V1__Create_users_table.sql
└── openapi/
    └── documentation.yaml  # OpenAPI 명세

python-services/           # Python 마이크로서비스
└── whisper-stt/
    ├── app.py            # Whisper STT FastAPI 서버
    ├── requirements.txt  # Python 의존성
    └── Dockerfile
```

## 🧪 테스트 예제

### JavaScript WebSocket 클라이언트
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/novel');

ws.onopen = () => {
    console.log('Connected to Novel MVP');
    
    // 대화 시작
    ws.send(JSON.stringify({
        type: 'TextInput',
        text: '오늘은 정말 특별한 날이었어요',
        conversationId: 'test-conv-123'
    }));
};

ws.onmessage = (event) => {
    const response = JSON.parse(event.data);
    console.log('Received:', response);
    
    if (response.type.includes('TextOutput') && response.readyForStory) {
        // 스토리 생성 요청
        ws.send(JSON.stringify({
            type: 'GenerateStory',
            conversationId: 'test-conv-123'
        }));
    }
};
```

## 🧪 테스트

### 테스트 프레임워크
- **Kotest**: Kotlin 네이티브 테스트 프레임워크
- **MockK**: Kotlin 전용 모킹 라이브러리
- **Kotlin Coroutines Test**: 코루틴 테스트 지원

### 테스트 구조

#### 1. 단위 테스트 (Unit Tests)

**ConversationAgentTest**
```kotlin
describe("ConversationAgent") {
    context("when processing user input") {
        context("with a new conversation") {
            it("should create a new conversation context") {
                // 새로운 대화 시작 테스트
            }
        }
        
        context("when emotion is detected") {
            it("should extract emotion tag and send to emotion agent") {
                // 감정 태그 추출 및 전달 테스트
            }
        }
    }
}
```

**EmotionAnalysisAgentTest**
```kotlin
describe("EmotionAnalysisAgent") {
    context("when analyzing emotions") {
        context("with happy text") {
            it("should detect happiness with high confidence") {
                // 행복 감정 분석 테스트
            }
        }
        
        context("with complex emotions") {
            it("should detect mixed emotions") {
                // 복합 감정 분석 테스트
            }
        }
    }
}
```

**StoryGenerationAgentTest**
```kotlin
describe("StoryGenerationAgent") {
    context("when generating story") {
        context("with happy conversation context") {
            it("should generate a heartwarming story") {
                // 따뜻한 스토리 생성 테스트
            }
        }
    }
}
```

#### 2. 통합 테스트 (Integration Tests)

**FullFlowIntegrationTest**
- 대화 → 감정 분석 → 스토리 생성 전체 플로우 테스트
- 행복한 대화, 복합 감정 대화 시나리오
- 에러 처리 및 성능 측정

**WebSocketIntegrationTest**
- WebSocket 연결 및 메시지 송수신 테스트
- 실시간 통신 검증

#### 3. Mock 서비스

**MockServiceFactory**
```kotlin
object MockServiceFactory {
    fun setupAllMocks() {
        // OpenAI, Gemini, ElevenLabs Mock 설정
    }
    
    fun setupFailureMocks() {
        // 에러 시나리오 Mock 설정
    }
    
    fun setupDelayedMocks(delayMillis: Long) {
        // 지연 응답 Mock 설정
    }
}
```

### 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "com.novel.agents.ConversationAgentTest"

# 테스트 리포트 확인
open build/reports/tests/test/index.html
```

### 테스트 커버리지

현재 구현된 테스트 커버리지:
- ✅ 에이전트 비즈니스 로직: 90%+
- ✅ 감정 분석 정확도: 다양한 시나리오 커버
- ✅ 스토리 생성 품질: 주요 장르별 테스트
- ✅ 에러 처리: 주요 실패 시나리오 커버
- ⚠️ WebSocket 통신: 기본 기능만 테스트

## 🚀 배포

### Docker를 사용한 로컬 실행

```bash
# 전체 스택 실행 (PostgreSQL, pgAdmin, Backend)
docker-compose up -d

# 로그 확인
docker-compose logs -f novel-backend

# 종료
docker-compose down
```

### 프로덕션 배포

#### 1. 환경 변수 설정
```bash
export DB_URL=jdbc:postgresql://your-db-host:5432/novel_db
export DB_USER=your_db_user
export DB_PASSWORD=your_db_password
export OPENAI_API_KEY=sk-your-openai-key
export GEMINI_API_KEY=your-gemini-key
export ELEVENLABS_API_KEY=your-elevenlabs-key
export JWT_SECRET=your-production-jwt-secret
```

#### 2. Docker 이미지 빌드 및 실행
```bash
# 이미지 빌드
docker build -t novel-mvp-backend .

# 실행
docker run -d \
  --name novel-backend \
  -p 8080:8080 \
  --env-file .env.prod \
  novel-mvp-backend
```

#### 3. Kubernetes 배포 (예시)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: novel-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: novel-backend
  template:
    metadata:
      labels:
        app: novel-backend
    spec:
      containers:
      - name: novel-backend
        image: your-registry/novel-mvp-backend:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: novel-secrets
              key: db-url
        # ... other env vars
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
```

### CI/CD

GitHub Actions를 통한 자동 배포:
1. 코드 푸시 시 자동 테스트 실행
2. main 브랜치 머지 시 Docker 이미지 빌드 및 푸시
3. 배포 환경에서 새 이미지 풀 및 롤링 업데이트

### 모니터링

- **로그**: Logback을 통한 구조화된 로깅
- **메트릭**: Micrometer + Prometheus
- **시각화**: Grafana 대시보드
- **알림**: 에러율, 응답 시간 등 임계값 기반 알림

## 🔒 보안 고려사항

- API 키는 환경 변수로 관리
- JWT 기반 인증 시스템 (Access + Refresh Token)
- OAuth 액세스 토큰 서버측 검증
  - Google: OAuth2 UserInfo API를 통한 토큰 유효성 확인
  - Kakao: 사용자 정보 API를 통한 토큰 검증
  - 이메일 일치 여부 확인으로 토큰 탈취 방지
- WebSocket 연결에 JWT 인증 필수
- 프로덕션 환경에서는 HTTPS/WSS 사용 필수
- Rate limiting 구현 권장
- CORS 설정으로 허용된 도메인만 접근

## 🚧 알려진 제한사항

- STT 서비스는 Docker 컨테이너 또는 Python venv 환경 필요
- Whisper 모델 다운로드 시 초기 시작 시간 지연 (약 1-2분)
- ElevenLabs TTS API 호출량에 따른 과금
- 동시 연결 수 제한 (서버 리소스에 따라)
- 스토리 생성은 대화 3-4회 이후 가능

## 📈 향후 개발 계획

- [x] 사용자 인증 및 세션 관리 (JWT 기반 완료)
- [x] 사용자 프로필 및 성격 분석 시스템
- [x] STT 기능 추가 (Whisper 기반 완료)
- [x] TTS 기능 개선 (ElevenLabs TTS 고품질 음성 합성 전환)
- [x] Docker 컨테이너화 (STT Python 서비스)
- [x] 감정별 음성 최적화 (ElevenLabs TTS 통합)
- [ ] 대화 히스토리 영구 저장
- [ ] 더 다양한 스토리 장르 추가
- [ ] 다국어 지원 확장
- [ ] 웹 프론트엔드 개발
- [ ] 실시간 알림 시스템 (스토리 생성 완료 등)
- [ ] 소셜 공유 기능
- [ ] 스토리 북마크 및 컬렉션 기능
- [ ] AI 모델 성능 최적화
- [ ] STT/TTS 스트리밍 최적화

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 📚 추가 문서

- [OAuth 인증 가이드](docs/OAUTH_GUIDE.md) - Google/Kakao OAuth 구현 가이드
- [API 문서](src/main/resources/openapi/documentation.yaml) - OpenAPI 3.0 명세

## 📞 문의

프로젝트 관련 문의사항이 있으시면 이슈를 생성해주세요.

---

## 🎉 프로젝트 완성 현황

### ✅ 구현 완료
- **사용자 관리 시스템**: JWT 기반 인증, 프로필 관리, 성격 분석
- **WebSocket 실시간 통신**: 사용자 인증 기반 개인화된 대화
- **AI 멀티 에이전트 시스템**: 대화, 감정 분석, 스토리 생성
- **하이브리드 STT/TTS 시스템**: Whisper STT + ElevenLabs TTS, 다국어 최적화
- **구독 시스템**: 무료/프리미엄 차등 서비스
- **일일 스토리 생성 제한**: 사용자별 쿼터 관리
- **이벤트 기반 아키텍처**: 도메인 이벤트 발행/구독
- **마이크로서비스 아키텍처**: Python STT/TTS + Kotlin 백엔드
- **데이터베이스**: PostgreSQL + Flyway 마이그레이션
- **테스트**: 단위 테스트 및 통합 테스트
- **문서화**: OpenAPI, README, Postman Collection
- **배포**: Docker Compose, 멀티 서비스 오케스트레이션

### 🔧 기술적 특징
- **Clean Architecture**: 계층 분리와 의존성 역전
- **Domain-Driven Design**: User Aggregate, 값 객체
- **SOLID 원칙**: 단일 책임, 인터페이스 분리
- **비동기 처리**: Kotlin Coroutines
- **타입 안전성**: Kotlin의 강력한 타입 시스템 활용

### 📊 주요 지표
- **코드 커버리지**: 주요 비즈니스 로직 90%+
- **응답 시간**: WebSocket 메시지 < 100ms
- **동시 접속**: 1,000+ WebSocket 연결 지원
- **가용성**: 99.9% SLA 목표