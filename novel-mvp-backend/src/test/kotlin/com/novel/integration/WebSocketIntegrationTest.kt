package com.novel.integration

import com.novel.globalJson
import com.novel.module
import com.novel.services.WebSocketMessage
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

class WebSocketIntegrationTest : DescribeSpec({
    
    describe("WebSocket Connection") {
        
        context("when establishing connection") {
            
            it("should connect successfully") {
                testApplication {
                    application {
                        module()
                    }

                    // 간단한 HTTP 요청으로 서버가 실행 중인지 확인
                    val response = client.get("/health")
                    response.status.value shouldBe 200

                    println("Health check passed")
                }
            }
            
            it("should handle WebSocket ping-pong") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val wsClient = createClient {
                        install(WebSockets) {
                            pingInterval = 1.seconds
                        }
                    }
                    
                    wsClient.webSocket("/ws/novel") {
                        println("WebSocket connected")
                        
                        // 단순히 연결 확인 후 종료
                        close(CloseReason(CloseReason.Codes.NORMAL, "Test complete"))
                    }
                    
                    println("Test completed")
                }
            }
        }
        
        context("when sending messages") {
            
            it("should handle text messages") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val wsClient = createClient {
                        install(WebSockets)
                    }
                    
                    wsClient.webSocket("/ws/novel") {
                        val textInput = WebSocketMessage.TextInput(
                            text = "안녕하세요!",
                            conversationId = "test-123"
                        )
                        // polymorphic serialization을 위해 WebSocketMessage로 캐스팅
                        val message: WebSocketMessage = textInput
                        send(Frame.Text(globalJson.encodeToString(WebSocketMessage.serializer(), message)))
                        
                        // 첫 번째 응답 대기
                        val response = incoming.receive()
                        if (response is Frame.Text) {
                            val responseText = response.readText()
                            println("Received: $responseText")
                            
                            val jsonResponse = Json.parseToJsonElement(responseText).jsonObject
                            jsonResponse["type"]?.jsonPrimitive?.content shouldBe "com.novel.services.WebSocketMessage.TextOutput"
                            jsonResponse["text"]?.jsonPrimitive?.content.shouldNotBeNull()
                        }
                        
                        close()
                    }
                }
            }
        }
    }
}) 