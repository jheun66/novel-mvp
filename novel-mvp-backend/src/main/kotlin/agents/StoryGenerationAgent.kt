package com.novel.agents

import com.novel.agents.base.Agent
import com.novel.agents.base.AgentCommunicator
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class StoryGenerationInput(
    val conversationContext: String,
    val emotionAnalysis: EmotionAnalysisOutput,
    val userId: String,
    val conversationHighlights: List<String> = emptyList(),
    val userPreferences: StoryPreferences? = null
)

@Serializable
data class StoryPreferences(
    val preferredGenres: List<String> = emptyList(),
    val personalityTraits: Map<String, Int> = emptyMap()
)

@Serializable
data class StoryGenerationOutput(
    val title: String,
    val story: String,
    val genre: String,
    val narrativeStyle: String,
    val emotionalArc: String,
    val keyMoments: List<String> = emptyList()
)

class StoryGenerationAgent(
    private val geminiApiKey: String,
    private val communicator: AgentCommunicator
) : Agent<StoryGenerationInput, StoryGenerationOutput> {

    private val logger = LoggerFactory.getLogger(StoryGenerationAgent::class.java)
    private val client: Client = Client.builder()
        .apiKey(geminiApiKey)
        .build()
    
    // 최신 Gemini 모델 사용
    private val modelName = "gemini-2.5-flash"

    override val name = "story-generation"

    override suspend fun process(input: StoryGenerationInput): StoryGenerationOutput {
        val prompt = buildStoryPrompt(input)
        
        // System instruction 설정
        val systemInstruction = Content.fromParts(
            Part.fromText("당신은 감성적이고 창의적인 소설가입니다. 사용자의 일상 대화를 바탕으로 깊이 있고 개인화된 단편 소설을 작성합니다.")
        )
        
        // GenerateContentConfig 설정
        val config = GenerateContentConfig.builder()
            .candidateCount(1)
            .maxOutputTokens(2048)
            .temperature(0.8f)
            .systemInstruction(systemInstruction)
            .build()

        try {
            val response = client.models.generateContent(
                modelName,
                prompt,
                config
            )
            
            val generatedText = response.text() 
                ?: throw RuntimeException("Failed to generate story")

            return parseStoryResponse(generatedText, input)
        } catch (e: Exception) {
            logger.error("Story generation failed", e)
            throw RuntimeException("Story generation failed: ${e.message}")
        }
    }

    private fun buildStoryPrompt(input: StoryGenerationInput): String {
        val emotionKorean = when(input.emotionAnalysis.primaryEmotion) {
            "HAPPY" -> "행복"
            "SAD" -> "슬픔"
            "EXCITED" -> "설렘"
            "CALM" -> "평온"
            "ANGRY" -> "화남"
            "GRATEFUL" -> "감사"
            "ANXIOUS" -> "불안"
            "NOSTALGIC" -> "그리움"
            else -> "중립"
        }
        
        val genrePreferences = input.userPreferences?.preferredGenres?.let { genres ->
            if (genres.isNotEmpty()) {
                """
                === 사용자 선호 장르 ===
                ${genres.joinToString(", ") { translateGenre(it) }}
                (위 장르 중 하나를 선택하여 스토리를 작성해주세요)
                """
            } else null
        } ?: ""
        
        val personalityContext = input.userPreferences?.personalityTraits?.let { traits ->
            if (traits.isNotEmpty()) {
                """
                === 사용자 성격 특성 ===
                ${traits.entries.mapNotNull { (trait, score) ->
                    when(trait) {
                        "OPENNESS" -> "개방성: ${score}점 - ${if (score > 60) "새로운 경험과 모험을 즐김" else "익숙하고 안정적인 것을 선호"}"
                        "EMOTIONAL_DEPTH" -> "감정 깊이: ${score}점 - ${if (score > 60) "깊고 복잡한 감정 표현 선호" else "간결하고 명확한 표현 선호"}"
                        "CREATIVITY" -> "창의성: ${score}점 - ${if (score > 60) "독특하고 예술적인 표현 선호" else "전통적이고 이해하기 쉬운 표현 선호"}"
                        else -> null
                    }
                }.joinToString("\n")}
                (위 성격 특성을 고려하여 스토리 스타일을 조정해주세요)
                """
            } else null
        } ?: ""

        return """
            사용자와의 대화 내용과 감정 분석을 바탕으로 깊이 있고 개인화된 단편 소설을 작성해주세요.
            
            === 대화 내용 ===
            ${input.conversationContext}
            
            === 감정 분석 ===
            주요 감정: $emotionKorean (${input.emotionAnalysis.confidence})
            감정 강도: ${input.emotionAnalysis.emotionalIntensity}
            키워드: ${input.emotionAnalysis.keywords.joinToString(", ")}
            감정 변화: ${input.emotionAnalysis.emotionalProgression ?: "일정함"}
            
            $genrePreferences
            
            $personalityContext
            
            === 작성 지침 ===
            - 400-600자 분량의 단편 소설
            - 1인칭 시점으로 작성
            - 대화에서 나온 실제 경험을 바탕으로 재구성
            - 감정의 변화와 흐름을 자연스럽게 표현
            - 일상 속에서 특별한 의미를 발견하는 순간 포착
            - 독자가 공감할 수 있는 보편적인 감정 표현
            - 사용자의 성격과 선호도를 반영한 스타일과 톤
            
            다음 형식으로 작성해주세요:
            
            제목: [감정과 경험을 담은 시적인 제목]
            
            장르: [이야기의 장르 - 예: 일상, 로맨스, 성장, 치유 등]
            
            감정곡선: [이야기 속 감정의 변화를 한 문장으로 표현]
            
            ---
            
            [본문 내용 - 400-600자]
            
            ---
            
            핵심순간:
            - [이야기의 핵심 순간 1]
            - [이야기의 핵심 순간 2]
            - [이야기의 핵심 순간 3]
        """.trimIndent()
    }
    
    private fun translateGenre(genre: String): String {
        return when(genre) {
            "HEALING" -> "치유"
            "ROMANCE" -> "로맨스"
            "MYSTERY" -> "미스터리"
            "FANTASY" -> "판타지"
            "SLICE_OF_LIFE" -> "일상"
            "ADVENTURE" -> "모험"
            "COMEDY" -> "코미디"
            else -> genre
        }
    }

    private fun parseStoryResponse(response: String, input: StoryGenerationInput): StoryGenerationOutput {
        val lines = response.lines()
        var title = "무제"
        var genre = "일상"
        var emotionalArc = "감정의 여정"
        val keyMoments = mutableListOf<String>()
        val storyLines = mutableListOf<String>()

        var isInStory = false
        var isInKeyMoments = false

        lines.forEach { line ->
            when {
                line.startsWith("제목:") -> {
                    title = line.substringAfter(":").trim()
                }
                line.startsWith("장르:") -> {
                    genre = line.substringAfter(":").trim()
                }
                line.startsWith("감정곡선:") -> {
                    emotionalArc = line.substringAfter(":").trim()
                }
                line.contains("---") -> {
                    if (!isInStory && !isInKeyMoments) {
                        isInStory = true
                    } else if (isInStory) {
                        isInStory = false
                    }
                }
                line.startsWith("핵심순간:") -> {
                    isInKeyMoments = true
                    isInStory = false
                }
                isInKeyMoments && line.trim().startsWith("-") -> {
                    val moment = line.trim().substring(1).trim()
                    if (moment.isNotEmpty()) {
                        keyMoments.add(moment)
                    }
                }
                isInStory && line.isNotBlank() -> {
                    storyLines.add(line)
                }
            }
        }

        val storyContent = storyLines.joinToString("\n").trim()

        // 스토리가 비어있는 경우 기본값 설정
        if (storyContent.isEmpty()) {
            logger.warn("Story content is empty, using fallback")
            return StoryGenerationOutput(
                title = title.ifEmpty { "무제" },
                story = "이야기를 생성하는 중 오류가 발생했습니다.",
                genre = genre.ifEmpty { "일상" },
                narrativeStyle = "감성적이고 서정적인",
                emotionalArc = emotionalArc.ifEmpty { "감정의 여정" },
                keyMoments = keyMoments.ifEmpty { listOf("순간의 발견", "감정의 전환", "새로운 시작") }
            )
        }

        return StoryGenerationOutput(
            title = title,
            story = storyContent,
            genre = genre,
            narrativeStyle = "감성적이고 서정적인",
            emotionalArc = emotionalArc,
            keyMoments = keyMoments
        )
    }
}