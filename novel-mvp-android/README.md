# Novel MVP Android App

An AI-powered storytelling Android application built with modern Android development practices and Jetpack Compose.

## 📱 Overview

This Android app allows users to have conversations with an AI and generate personalized novels based on their interactions. The app supports both text and voice input, with real-time speech-to-text transcription and text-to-speech capabilities.

## 🏗️ Architecture

### MVI (Model-View-Intent) Pattern

This app follows the **MVI (Model-View-Intent)** architecture pattern, which is a unidirectional data flow pattern that makes state management predictable and testable.

```mermaid
graph LR
    A[User Action] --> B[Intent]
    B --> C[ViewModel]
    C --> D[ViewState]
    D --> E[UI Update]
    C --> F[SideEffect]
    F --> G[One-time Actions]
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#e1f5fe
    style F fill:#fff9c4
    style G fill:#fce4ec
```

### Project Structure

```mermaid
graph TD
    A[Novel MVP Android] --> B[app/src/main/java/com/novel/mvp/]
    B --> C[data/]
    B --> D[di/]
    B --> E[navigation/]
    B --> F[presentation/]
    B --> G[utils/]
    
    C --> C1[local/ - TokenStorage]
    C --> C2[model/ - WebSocketMessage]
    C --> C3[remote/ - ApiService]
    C --> C4[repository/ - AuthRepository, StoryRepository]
    C --> C5[websocket/ - StoryWebSocketService]
    
    D --> D1[AppModule.kt - Manual DI]
    
    E --> E1[AppNavigation.kt - Navigation Setup]
    
    F --> F1[login/ - Login Screen & ViewModel]
    F --> F2[story/ - Story Screen & ViewModel]
    F --> F3[components/ - Reusable UI Components]
    
    G --> G1[AudioRecorder.kt - Voice Recording]
    G --> G2[GoogleCredentialManager.kt - Auth Helper]
    
    style A fill:#e3f2fd
    style B fill:#f1f8e9
    style C fill:#fff3e0
    style D fill:#fce4ec
    style E fill:#e8eaf6
    style F fill:#e0f2f1
    style G fill:#fff8e1
```

## 🛠️ Technology Stack

### Core Technologies
- **Kotlin**: Primary programming language
- **Jetpack Compose**: Modern declarative UI toolkit
- **Coroutines & Flow**: Asynchronous programming and reactive streams
- **Ktor Client**: HTTP client for API calls and WebSocket communication

### Key Libraries
- **Kotlinx Serialization**: JSON serialization/deserialization
- **Google Credential Manager**: Google Sign-In integration
- **Android AudioRecord**: Voice recording functionality

### Architecture Patterns
- **MVI (Model-View-Intent)**: Unidirectional data flow
- **Repository Pattern**: Data access abstraction
- **Dependency Injection**: Manual DI for simplicity

## 🎯 Key Features

### 1. Authentication Flow

```mermaid
sequenceDiagram
    participant U as User
    participant L as LoginScreen
    participant G as GoogleCredentialManager
    participant A as AuthRepository
    participant S as Server
    
    U->>L: Tap Google Sign In
    L->>G: Start Google Sign In
    G->>U: Show Google Auth
    U->>G: Complete Auth
    G->>L: Return ID Token
    L->>A: Send Token to Backend
    A->>S: Verify Google Token
    S->>A: Return JWT Access Token
    A->>L: Login Success
    L->>U: Navigate to Main Screen
```

### 2. Voice Input Flow

```mermaid
sequenceDiagram
    participant U as User
    participant V as VoiceInputButton
    participant A as AudioRecorder
    participant VM as StoryViewModel
    participant W as WebSocketService
    participant B as Backend
    
    U->>V: Press Mic Button
    V->>VM: StartRecording Intent
    VM->>A: Start Audio Recording
    A->>V: Real-time Amplitude
    V->>U: Visual Feedback (Rings)
    U->>V: Release Button
    V->>A: Stop Recording
    A->>VM: Audio Data (WAV)
    VM->>W: Send Audio Message
    W->>B: Base64 Audio + Metadata
    B->>W: Transcribed Text
    W->>VM: Text Response
    VM->>U: Show Transcription
```

### 3. Real-time Communication

```mermaid
graph LR
    A[Android App] <--> B[WebSocket Connection]
    B <--> C[Backend Server]
    C <--> D[Whisper STT]
    C <--> E[Fish Speech TTS]
    C <--> F[AI Chat Model]
    
    style A fill:#e1f5fe
    style B fill:#f3e5f5
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#fff9c4
    style F fill:#fce4ec
```

## 📋 Understanding MVI in This Project

### MVI Data Flow Example

```mermaid
graph TD
    A[User taps Voice Button] --> B[StoryIntent.StartRecording]
    B --> C[ViewModel.handleIntent()]
    C --> D[Update ViewState.isRecording = true]
    C --> E[Emit SideEffect.StartAudioRecording]
    D --> F[UI recomposes with recording state]
    E --> G[LaunchedEffect handles side effect]
    G --> H[AudioRecorder starts recording]
    H --> I[Real-time amplitude updates]
    I --> J[UI shows animated rings]
    
    style A fill:#e3f2fd
    style B fill:#f1f8e9
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#fff9c4
    style F fill:#e1f5fe
    style G fill:#fce4ec
    style H fill:#e8eaf6
    style I fill:#e0f2f1
    style J fill:#fff8e1
```

### WebSocket Message Types

```mermaid
classDiagram
    class WebSocketMessage {
        <<sealed class>>
    }
    
    class TextInput {
        +String text
        +String conversationId
    }
    
    class AudioInput {
        +String audioData
        +String conversationId
        +String format
        +Int sampleRate
        +Boolean isStreaming
    }
    
    class TextOutput {
        +String text
        +String emotion
        +List~String~ suggestedQuestions
        +Boolean readyForStory
    }
    
    class AudioOutput {
        +String audioData
        +Float duration
        +String audioType
    }
    
    class StoryOutput {
        +String title
        +String content
        +String genre
        +String emotion
        +String emotionalArc
    }
    
    WebSocketMessage <|-- TextInput
    WebSocketMessage <|-- AudioInput
    WebSocketMessage <|-- TextOutput
    WebSocketMessage <|-- AudioOutput
    WebSocketMessage <|-- StoryOutput
```

## 🗂️ Data Layer Architecture

```mermaid
graph TD
    A[Presentation Layer] --> B[Repository Layer]
    B --> C[Data Sources]
    
    A1[StoryViewModel] --> B1[StoryRepository]
    A2[LoginViewModel] --> B2[AuthRepository]
    
    B1 --> C1[StoryWebSocketService]
    B1 --> C2[ApiService]
    B2 --> C2
    B2 --> C3[TokenStorage]
    
    C1 --> D1[WebSocket Connection]
    C2 --> D2[HTTP Client - Ktor]
    C3 --> D3[SharedPreferences]
    
    style A fill:#e3f2fd
    style B fill:#f1f8e9
    style C fill:#fff3e0
    style D1 fill:#e8f5e8
    style D2 fill:#fff9c4
    style D3 fill:#fce4ec
```

## 🎨 UI Component Hierarchy

```mermaid
graph TD
    A[StoryScreen] --> B[TopAppBar]
    A --> C[TranscriptionDisplay]
    A --> D[StoryDisplayCard]
    A --> E[ConversationSection]
    A --> F[MessageInputSection]
    
    E --> E1[LazyColumn]
    E1 --> E2[ConversationMessageItem]
    
    F --> F1[OutlinedTextField]
    F --> F2[VoiceInputButton]
    F --> F3[Send Button]
    
    F2 --> F2A[Animated FAB]
    F2 --> F2B[Amplitude Rings]
    F2 --> F2C[AudioRecorder Integration]
    
    C --> C1[Animated Visibility]
    C --> C2[Transcription Text]
    C --> C3[Loading Indicator]
    
    style A fill:#e3f2fd
    style B fill:#f1f8e9
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#fff9c4
    style F fill:#fce4ec
```

## 🔧 Setup and Running

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Kotlin 1.8+

### Setup Steps

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd novel-mvp/novel-mvp-android
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to `novel-mvp-android` folder

3. **Sync project**:
   - Android Studio will automatically prompt to sync Gradle
   - Wait for sync to complete

4. **Run the app**:
   - Connect an Android device or start an emulator
   - Click "Run" button or press `Ctrl+R` (Windows/Linux) or `Cmd+R` (Mac)

### Configuration

The app connects to a backend server running on `10.0.2.2:8080` (Android emulator localhost). 

To change the server URL, modify:
```kotlin
// StoryWebSocketService.kt
private const val WS_URL = "ws://YOUR_SERVER_IP:8080/ws/novel"

// ApiService.kt  
private const val BASE_URL = "http://YOUR_SERVER_IP:8080"
```

## 🚀 Adding New Features

### State Management Flow

```mermaid
graph TD
    A[Define Intent] --> B[Add to Contract]
    B --> C[Handle in ViewModel]
    C --> D[Update ViewState]
    C --> E[Emit SideEffect if needed]
    D --> F[UI Recomposes]
    E --> G[Handle in LaunchedEffect]
    
    style A fill:#e3f2fd
    style B fill:#f1f8e9
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#fff9c4
    style F fill:#e1f5fe
    style G fill:#fce4ec
```

### Example: Adding a New Feature

1. **Create the contract** (define Intent, ViewState, SideEffect):
   ```kotlin
   // presentation/newfeature/NewFeatureContract.kt
   sealed class NewFeatureIntent {
       object LoadData : NewFeatureIntent()
   }
   
   data class NewFeatureViewState(
       val isLoading: Boolean = false,
       val data: List<String> = emptyList()
   )
   
   sealed class NewFeatureSideEffect {
       data class ShowError(val message: String) : NewFeatureSideEffect()
   }
   ```

2. **Create the ViewModel**:
   ```kotlin
   class NewFeatureViewModel : ViewModel() {
       private val _viewState = MutableStateFlow(NewFeatureViewState())
       val viewState: StateFlow<NewFeatureViewState> = _viewState.asStateFlow()
       
       private val _sideEffect = MutableSharedFlow<NewFeatureSideEffect>()
       val sideEffect: SharedFlow<NewFeatureSideEffect> = _sideEffect.asSharedFlow()
       
       fun handleIntent(intent: NewFeatureIntent) {
           when (intent) {
               is NewFeatureIntent.LoadData -> loadData()
           }
       }
   }
   ```

## 🐛 Common Issues and Solutions

### Debugging Flow

```mermaid
graph TD
    A[Issue Detected] --> B{Type of Issue?}
    B -->|Network| C[Check WebSocket Connection]
    B -->|UI| D[Check ViewState Updates]
    B -->|Audio| E[Check Permissions]
    B -->|Build| F[Clean & Rebuild]
    
    C --> C1[Verify server is running]
    C --> C2[Check network connectivity]
    C --> C3[Inspect WebSocket logs]
    
    D --> D1[Add logging to ViewModel]
    D --> D2[Check Intent handling]
    D --> D3[Verify StateFlow emissions]
    
    E --> E1[Check AndroidManifest.xml]
    E --> E2[Verify runtime permissions]
    E --> E3[Test on different devices]
    
    F --> F1[./gradlew clean]
    F --> F2[./gradlew build]
    F --> F3[Invalidate caches & restart]
    
    style A fill:#ffebee
    style B fill:#fff3e0
    style C fill:#e8f5e8
    style D fill:#e3f2fd
    style E fill:#f1f8e9
    style F fill:#fff9c4
```

## 📚 Learning Path for Beginners

### Recommended Learning Order

```mermaid
graph TD
    A[1. Kotlin Basics] --> B[2. Android Fundamentals]
    B --> C[3. Jetpack Compose]
    C --> D[4. Coroutines & Flow]
    D --> E[5. Architecture Patterns]
    E --> F[6. Networking with Ktor]
    F --> G[7. WebSocket Communication]
    G --> H[8. Audio Recording]
    H --> I[9. This Project!]
    
    style A fill:#e3f2fd
    style B fill:#f1f8e9
    style C fill:#fff3e0
    style D fill:#e8f5e8
    style E fill:#fff9c4
    style F fill:#fce4ec
    style G fill:#e8eaf6
    style H fill:#e0f2f1
    style I fill:#fff8e1
```

### Key Concepts to Understand

1. **Kotlin Coroutines**: How async operations work
2. **Jetpack Compose**: Declarative UI framework
3. **StateFlow vs SharedFlow**: State vs Events
4. **Lifecycle-aware Components**: ViewModels and their lifecycle
5. **Dependency Injection**: How components get their dependencies
6. **WebSocket vs HTTP**: Real-time vs request-response communication

## 🤝 Contributing

When contributing to this project:

1. Follow the existing MVI architecture pattern
2. Add proper documentation for new features
3. Write unit tests for new ViewModels
4. Follow Kotlin coding conventions
5. Update this README if you add new major features

## 📄 License

This project is part of the Novel MVP application suite.