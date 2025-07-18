package com.novel.agents

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.core.Role
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.novel.agents.base.Agent
import com.novel.agents.base.AgentCommunicator
import com.novel.agents.base.AgentMessage
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Duration.Companion.seconds

@Serializable
data class ConversationInput(
    val userId: String,
    val message: String,
    val conversationId: String = UUID.randomUUID().toString(),
    val isVoiceInput: Boolean = false,
    val userProfile: UserProfile? = null
)

@Serializable
data class UserProfile(
    val name: String,
    val personalityTraits: Map<String, Int> = emptyMap(),
    val preferredGenres: Set<String> = emptySet()
)

@Serializable
data class ConversationOutput(
    val response: String,
    val emotion: String? = null,
    val shouldGenerateStory: Boolean = false,
    val collectedContext: String? = null,
    val suggestedQuestions: List<String> = emptyList()
)

@Serializable
data class ConversationContext(
    val userId: String,
    val conversationId: String,
    val messages: MutableList<ChatMessage> = mutableListOf(),
    val collectedStoryElements: MutableList<String> = mutableListOf(),
    val detectedEmotions: MutableList<String> = mutableListOf(),
    var turnCount: Int = 0
)

/**
 * 대화 에이전트 - 사용자와 자연스러운 대화를 나누며 일상 수집
 */
class ConversationAgent(
    private val openAiApiKey: String,
    private val communicator: AgentCommunicator
) : Agent<ConversationInput, ConversationOutput> {
    
    private val logger = LoggerFactory.getLogger(ConversationAgent::class.java)
    
    private val openAI = OpenAI(
        token = openAiApiKey,
        timeout = com.aallam.openai.api.http.Timeout(socket = 60.seconds)
    )
    
    private val contexts = mutableMapOf<String, ConversationContext>()
    
    override val name = "conversation"
    
    private fun buildSystemPrompt(userProfile: UserProfile?): String {
        val basePrompt = """
        당신은 따뜻하고 공감적인 대화 상대입니다. 사용자의 하루 이야기를 자연스럽게 들어주고 공감하면서, 
        더 깊은 이야기를 이끌어내는 역할을 합니다.
        
        대화 원칙:
        1. 진심 어린 공감과 관심을 표현하세요
        2. 사용자의 감정을 인정하고 반영해주세요
        3. 자연스럽게 구체적인 디테일을 물어보세요
        4. 압박감을 주지 않고 편안한 분위기를 유지하세요
        5. 사용자가 말한 내용을 기억하고 연결지어 대화하세요
        """.trimIndent()
        
        val personalityContext = userProfile?.let { profile ->
            """
            
            사용자 정보:
            - 이름: ${profile.name}
            ${if (profile.personalityTraits.isNotEmpty()) {
                "- 성격 특성: " + profile.personalityTraits.entries.joinToString(", ") { 
                    "${it.key}: ${it.value}점" 
                }
            } else ""}
            ${if (profile.preferredGenres.isNotEmpty()) {
                "- 선호 장르: " + profile.preferredGenres.joinToString(", ")
            } else ""}
            
            사용자의 성격 특성을 고려하여 대화 스타일을 조정하세요:
            ${profile.personalityTraits.entries.mapNotNull { (trait, score) ->
                when (trait) {
                    "OPENNESS" -> if (score > 60) "- 새로운 경험에 대해 깊이 있게 물어보세요" else "- 익숙한 일상에 대해 편안하게 대화하세요"
                    "EXTROVERSION" -> if (score > 60) "- 활발하고 에너지 넘치는 대화를 나누세요" else "- 차분하고 사려 깊은 대화를 나누세요"
                    "EMOTIONAL_DEPTH" -> if (score > 60) "- 감정적인 측면을 깊이 탐구하세요" else "- 가볍고 실용적인 대화를 유지하세요"
                    else -> null
                }
            }.joinToString("\n")}
            """.trimIndent()
        } ?: ""
        
        val instructionPrompt = """
        
        충분한 이야기가 모였다고 판단되면 (최소 3-4번의 대화 턴 후):
        - [READY_FOR_STORY] 태그를 응답에 포함시키세요
        - 수집된 이야기 요약을 [CONTEXT: ...] 형식으로 포함시키세요
        
        사용자의 감정이 강하게 드러나면:
        - [EMOTION: 감정명] 태그를 포함시키세요
        """.trimIndent()
        
        return basePrompt + personalityContext + instructionPrompt
    }
    
    override suspend fun process(input: ConversationInput): ConversationOutput {
        val context = contexts.getOrPut(input.conversationId) {
            ConversationContext(input.userId, input.conversationId)
        }
        
        // Add user message to context
        context.messages.add(ChatMessage(
            role = Role.User,
            content = input.message
        ))
        context.turnCount++
        
        // Generate response with personalized system prompt
        val systemPrompt = buildSystemPrompt(input.userProfile)
        val messages = listOf(
            ChatMessage(role = Role.System, content = systemPrompt)
        ) + context.messages
        
        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-4.1"),
            messages = messages,
            temperature = 0.8,
            maxTokens = 500
        )
        
        val completion = openAI.chatCompletion(chatCompletionRequest)
        
        val response = completion.choices.first().message
        val responseContent = response.content ?: ""
        
        // Add assistant response to context
        context.messages.add(ChatMessage(
            role = Role.Assistant,
            content = responseContent
        ))
        
        // Parse response for special tags
        val (cleanResponse, metadata) = parseResponse(responseContent)
        
        // Update context with collected elements
        metadata.context?.let { context.collectedStoryElements.add(it) }
        metadata.emotion?.let { 
            context.detectedEmotions.add(it)
            // Send emotion to emotion analysis agent for deeper analysis
            sendToEmotionAgent(input.message, it)
        }
        
        // Generate suggested questions if not ready for story
        val suggestedQuestions = if (!metadata.readyForStory && context.turnCount < 4) {
            generateSuggestedQuestions(context)
        } else emptyList()
        
        return ConversationOutput(
            response = cleanResponse,
            emotion = metadata.emotion,
            shouldGenerateStory = metadata.readyForStory,
            collectedContext = if (metadata.readyForStory) {
                context.collectedStoryElements.joinToString("\n")
            } else null,
            suggestedQuestions = suggestedQuestions
        )
    }
    
    private data class ResponseMetadata(
        val readyForStory: Boolean = false,
        val context: String? = null,
        val emotion: String? = null
    )
    
    private fun parseResponse(response: String): Pair<String, ResponseMetadata> {
        var cleanResponse = response
        val metadata = ResponseMetadata(
            readyForStory = response.contains("[READY_FOR_STORY]"),
            context = extractTag(response, "CONTEXT"),
            emotion = extractTag(response, "EMOTION")
        )
        
        // Remove tags from response
        cleanResponse = cleanResponse
            .replace(Regex("\\[READY_FOR_STORY\\]"), "")
            .replace(Regex("\\[CONTEXT:.*?\\]"), "")
            .replace(Regex("\\[EMOTION:.*?\\]"), "")
            .trim()
        
        return cleanResponse to metadata
    }
    
    private fun extractTag(text: String, tagName: String): String? {
        val regex = Regex("\\[$tagName:\\s*(.+?)\\]")
        return regex.find(text)?.groupValues?.get(1)?.trim()
    }
    
    private fun generateSuggestedQuestions(context: ConversationContext): List<String> {
        return when (context.turnCount) {
            1 -> listOf(
                "그 때 어떤 기분이 드셨나요?",
                "누구와 함께 있었나요?",
                "가장 기억에 남는 순간은 무엇이었나요?"
            )
            2 -> listOf(
                "그 일이 당신에게 어떤 의미인가요?",
                "비슷한 경험이 있으신가요?",
                "그 후에는 어떻게 되었나요?"
            )
            else -> listOf(
                "오늘 하루를 한 문장으로 표현한다면?",
                "내일은 어떤 하루가 되었으면 좋겠나요?",
                "오늘의 이야기를 누군가와 나누고 싶으신가요?"
            )
        }
    }
    
    private suspend fun sendToEmotionAgent(message: String, detectedEmotion: String) {
        val emotionMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            from = name,
            to = "emotion-analysis",
            payload = mapOf(
                "text" to message,
                "initialEmotion" to detectedEmotion
            )
        )
        communicator.send(emotionMessage)
    }
    
    fun clearContext(conversationId: String) {
        contexts.remove(conversationId)
    }
}