package com.novel.agents

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.novel.agents.base.Agent
import com.novel.agents.base.AgentCommunicator
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

@Serializable
data class EmotionAnalysisInput(
    val text: String,
    val context: String? = null,
    val previousEmotions: List<String> = emptyList()
)

@Serializable
data class EmotionAnalysisOutput(
    val primaryEmotion: String,
    val confidence: Float,
    val emotionalIntensity: Float,
    val subEmotions: List<String>,
    val keywords: List<String>,
    val sentiment: String,
    val emotionalProgression: String? = null,  // 감정의 변화 추이
    val sentenceEmotions: List<SentenceEmotion> = emptyList()  // 문장별 감정
)

@Serializable
data class SentenceEmotion(
    val sentence: String,
    val emotion: String,
    val intensity: Float
)

/**
 * 감정 분석 에이전트 - 텍스트의 감정을 깊이 있게 분석
 */
class EmotionAnalysisAgent(
    private val openAiApiKey: String,
    private val communicator: AgentCommunicator
) : Agent<EmotionAnalysisInput, EmotionAnalysisOutput> {
    
    private val logger = LoggerFactory.getLogger(EmotionAnalysisAgent::class.java)
    
    // OpenAI 4.0.1 configuration
    private val openAI = OpenAI(
        token = openAiApiKey,
        timeout = com.aallam.openai.api.http.Timeout(socket = 60.seconds)
    )
    
    override val name = "emotion-analysis"
    
    override suspend fun process(input: EmotionAnalysisInput): EmotionAnalysisOutput {
        val prompt = buildPrompt(input)
        
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("o4-mini"),
            messages = listOf(
                ChatMessage(
                    role = Role.User,
                    content = prompt
                )
            ),
            temperature = 0.3,
            responseFormat = ChatResponseFormat.JsonObject
        )
        
        val completion = openAI.chatCompletion(chatCompletionRequest)
        
        val response = completion.choices.first().message.content ?: "{}"
        logger.debug("Emotion analysis response: $response")
        
        return parseEmotionAnalysis(response, input.text)
    }
    
    private fun buildPrompt(input: EmotionAnalysisInput): String {
        return """
            당신은 전문 감정 분석가입니다. 주어진 텍스트의 감정을 깊이 있게 분석해주세요.
            
            텍스트: "${input.text}"
            ${input.context?.let { "맥락: $it" } ?: ""}
            ${if (input.previousEmotions.isNotEmpty()) "이전 감정들: ${input.previousEmotions.joinToString(", ")}" else ""}
            
            다음 항목들을 분석해주세요:
            1. 주요 감정: HAPPY(행복), SAD(슬픔), EXCITED(설렘), CALM(평온), ANGRY(화남), GRATEFUL(감사), ANXIOUS(불안), NOSTALGIC(그리움), PROUD(자랑스러움), DISAPPOINTED(실망) 중 하나
            2. 확신도: 0.0-1.0
            3. 감정 강도: 0.0-1.0
            4. 부가 감정들: 느껴지는 다른 감정들
            5. 핵심 키워드: 감정과 관련된 주요 단어/구문
            6. 감정톤: 긍정적/부정적/중립적/복합적
            7. 감정 변화: 텍스트 내에서 감정이 어떻게 변화하는지
            8. 문장별 감정: 각 문장의 주요 감정과 강도 (최대 5개 주요 문장)
            
            JSON 형식으로 응답해주세요:
            {
                "primaryEmotion": "주요감정",
                "confidence": 0.95,
                "intensity": 0.8,
                "subEmotions": ["부가감정1", "부가감정2"],
                "keywords": ["키워드1", "키워드2"],
                "sentiment": "긍정적",
                "emotionalProgression": "처음에는 불안했지만 점차 안정되고 희망적으로 변화",
                "sentenceEmotions": [
                    {"sentence": "문장1", "emotion": "감정", "intensity": 0.7},
                    {"sentence": "문장2", "emotion": "감정", "intensity": 0.9}
                ]
            }
        """.trimIndent()
    }
    
    private fun parseEmotionAnalysis(jsonResponse: String, originalText: String): EmotionAnalysisOutput {
        return try {
            val json = Json.parseToJsonElement(jsonResponse).jsonObject
            
            EmotionAnalysisOutput(
                primaryEmotion = json["primaryEmotion"]?.jsonPrimitive?.content ?: "NEUTRAL",
                confidence = json["confidence"]?.jsonPrimitive?.float ?: 0.5f,
                emotionalIntensity = json["intensity"]?.jsonPrimitive?.float ?: 0.5f,
                subEmotions = json["subEmotions"]?.jsonArray?.map { 
                    it.jsonPrimitive.content 
                } ?: emptyList(),
                keywords = json["keywords"]?.jsonArray?.map { 
                    it.jsonPrimitive.content 
                } ?: emptyList(),
                sentiment = json["sentiment"]?.jsonPrimitive?.content ?: "중립적",
                emotionalProgression = json["emotionalProgression"]?.jsonPrimitive?.content,
                sentenceEmotions = json["sentenceEmotions"]?.jsonArray?.map { element ->
                    val obj = element.jsonObject
                    SentenceEmotion(
                        sentence = obj["sentence"]?.jsonPrimitive?.content ?: "",
                        emotion = obj["emotion"]?.jsonPrimitive?.content ?: "NEUTRAL",
                        intensity = obj["intensity"]?.jsonPrimitive?.float ?: 0.5f
                    )
                } ?: analyzeSentenceEmotions(originalText)
            )
        } catch (e: Exception) {
            logger.error("Failed to parse emotion analysis", e)
            EmotionAnalysisOutput(
                primaryEmotion = "NEUTRAL",
                confidence = 0.5f,
                emotionalIntensity = 0.5f,
                subEmotions = emptyList(),
                keywords = emptyList(),
                sentiment = "중립적"
            )
        }
    }
    
    private fun analyzeSentenceEmotions(text: String): List<SentenceEmotion> {
        // 간단한 문장 분리 (실제로는 더 정교한 방법 필요)
        return text.split(Regex("[.!?]+"))
            .filter { it.isNotBlank() }
            .take(5)
            .map { sentence ->
                SentenceEmotion(
                    sentence = sentence.trim(),
                    emotion = "NEUTRAL",
                    intensity = 0.5f
                )
            }
    }
}