# Novel MVP Backend

일상을 AI 소설로 변환하는 서비스의 백엔드 MVP입니다.

## 🚀 빠른 시작

### 1. 환경 설정

#### 방법 1: .env 파일 사용 (권장)
```bash
# .env 파일 생성
cp .env.example .env

# .env 파일 편집하여 Gemini API 키 입력
# GEMINI_API_KEY=your-actual-gemini-api-key
```

#### 방법 2: 환경변수 직접 설정
```bash
export GEMINI_API_KEY="your-gemini-api-key"
```

Gemini API 키는 [Google AI Studio](https://aistudio.google.com/apikey)에서 무료로 발급받을 수 있습니다.

### 2. 서버 실행
```bash
./gradlew run
```

서버가 http://localhost:8080 에서 실행됩니다.

## 📡 API 엔드포인트

### 스토리 생성
```
POST /api/generate-story
Content-Type: application/json

{
  "content": "오늘은 카페에서 친구를 만났다. 오랜만에 만나서 정말 반가웠다.",
  "emotion": "HAPPY"
}
```

### 응답 예시
```json
{
  "title": "다시 만난 우정",
  "content": "카페 문을 열고 들어서는 순간, 그 익숙한 미소가 나를 반겼다...",
  "emotion": "HAPPY",
  "createdAt": 1704355200000
}
```

### 지원 감정
- HAPPY (행복)
- SAD (슬픔)
- EXCITED (설렘)
- CALM (평온)
- ANGRY (화남)
- GRATEFUL (감사)

## 🧪 테스트

### cURL
```bash
curl -X POST http://localhost:8080/api/generate-story \
  -H "Content-Type: application/json" \
  -d '{
    "content": "오늘은 비가 와서 우산 없이 집에 왔다.",
    "emotion": "SAD"
  }'
```

### 건강 체크
```bash
curl http://localhost:8080/api/health
```

## 🛠 기술 스택
- Kotlin + Ktor
- LangChain4j 1.1.0 (최신 버전)
  - LangChain4j-google-ai-gemini 1.1.0-rc1 (최신 버전)
- Google Gemini API (gemini-2.5-flash-preview-04-17)
- dotenv-kotlin 6.5.1 (환경변수 관리)
- Kotlinx Serialization

## 📝 특징
- 로그인 없음
- DB 없음 (상태 저장 안함)
- 단일 API 엔드포인트
- 무료 Gemini API 사용
- .env 파일로 간편한 환경 설정
- 3-5일 내 개발 가능한 MVP

## 🔧 Gemini 모델 옵션
StoryService.kt에서 다음 모델 중 선택 가능:
- `gemini-2.0-flash`
- `gemini-1.5-flash`
- `gemini-1.5-pro`
- `gemini-pro`
- `gemini-pro-vision`
- `gemini-2.5-pro-preview-03-25`
- `gemini-2.5-flash-preview-04-17`

## 📂 프로젝트 구조
```
novel-mvp-backend/
├── src/
│   └── main/
│       └── kotlin/
│           ├── Application.kt
│           ├── Routing.kt
│           ├── HTTP.kt
│           ├── Serialization.kt
│           └── services/
│               └── StoryService.kt
├── .env                    # API 키 설정 (git에 포함되지 않음)
├── build.gradle.kts       # 빌드 설정
└── README.md             # 이 파일
```
