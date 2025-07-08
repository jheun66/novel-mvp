package com.novel.integration

import com.novel.mocks.MockServiceFactory
import com.novel.module
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.seconds

class WebSocketIntegrationTest : DescribeSpec({
    
    describe("WebSocket Connection") {
        
        beforeEach {
            MockServiceFactory.setupAllMocks()
        }
        
        afterEach {
            MockServiceFactory.teardownAllMocks()
        }
        
        context("when establishing connection") {
            
            it("should connect successfully") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        client.webSocket("/novel-ws/user123/conv123") {
                            // Connection established
                            close()
                        }
                    }
                }
            }
            
            it("should reject connection without proper path parameters") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        try {
                            client.webSocket("/novel-ws") {
                                // Should not reach here
                            }
                        } catch (e: Exception) {
                            // Expected to fail
                            e.message shouldContain "404"
                        }
                    }
                }
            }
        }
        
        context("when sending messages") {
            
            it("should handle text messages") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        client.webSocket("/novel-ws/user123/conv123") {
                            // Send a text message
                            val message = buildJsonObject {
                                put("type", "text")
                                put("content", "안녕하세요, 오늘 정말 행복한 일이 있었어요!")
                            }.toString()
                            
                            send(Frame.Text(message))
                            
                            // Wait for response
                            withTimeout(5.seconds) {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val response = frame.readText()
                                        val json = Json.parseToJsonElement(response).jsonObject
                                        
                                        json["type"]?.jsonPrimitive?.content shouldBe "response"
                                        json["content"]?.jsonPrimitive?.content?.shouldContain("행복")
                                        break
                                    }
                                }
                            }
                            
                            close()
                        }
                    }
                }
            }
            
            it("should handle story generation request") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        client.webSocket("/novel-ws/user123/conv123") {
                            // First, send some conversation
                            val conversationMessage = buildJsonObject {
                                put("type", "text")
                                put("content", "오늘 가족과 함께 정말 행복한 시간을 보냈어요. 완성된 것 같아요.")
                            }.toString()
                            
                            send(Frame.Text(conversationMessage))
                            
                            // Wait for the conversation response with story readiness
                            withTimeout(5.seconds) {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val response = frame.readText()
                                        val json = Json.parseToJsonElement(response).jsonObject
                                        
                                        if (json["shouldGenerateStory"]?.jsonPrimitive?.boolean == true) {
                                            // Send story generation request
                                            val storyRequest = buildJsonObject {
                                                put("type", "generateStory")
                                            }.toString()
                                            
                                            send(Frame.Text(storyRequest))
                                            break
                                        }
                                    }
                                }
                            }
                            
                            // Wait for story response
                            withTimeout(5.seconds) {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val response = frame.readText()
                                        val json = Json.parseToJsonElement(response).jsonObject
                                        
                                        if (json["type"]?.jsonPrimitive?.content == "story") {
                                            json["title"]?.jsonPrimitive?.content shouldContain "행복"
                                            json["story"]?.jsonPrimitive?.content?.shouldContain("가족")
                                            break
                                        }
                                    }
                                }
                            }
                            
                            close()
                        }
                    }
                }
            }
        }
        
        context("when handling errors") {
            
            it("should handle malformed JSON gracefully") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        client.webSocket("/novel-ws/user123/conv123") {
                            // Send malformed JSON
                            send(Frame.Text("{ invalid json }"))
                            
                            // Should receive error response
                            withTimeout(5.seconds) {
                                for (frame in incoming) {
                                    if (frame is Frame.Text) {
                                        val response = frame.readText()
                                        val json = Json.parseToJsonElement(response).jsonObject
                                        
                                        json["type"]?.jsonPrimitive?.content shouldBe "error"
                                        json["message"]?.jsonPrimitive?.content?.shouldContain("처리할 수 없는")
                                        break
                                    }
                                }
                            }
                            
                            close()
                        }
                    }
                }
            }
            
            it("should handle connection close gracefully") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        client.webSocket("/novel-ws/user123/conv123") {
                            // Send a message
                            val message = buildJsonObject {
                                put("type", "text")
                                put("content", "테스트 메시지")
                            }.toString()
                            
                            send(Frame.Text(message))
                            
                            // Close the connection
                            close(CloseReason(CloseReason.Codes.NORMAL, "Client closing"))
                            
                            // Connection should be closed cleanly
                            closeReason.await()?.code shouldBe CloseReason.Codes.NORMAL.code
                        }
                    }
                }
            }
        }
        
        context("when handling multiple clients") {
            
            it("should handle multiple concurrent connections") {
                testApplication {
                    application {
                        module()
                    }
                    
                    val client = createClient {
                        install(WebSockets)
                    }
                    
                    runTest {
                        // Create multiple connections
                        val connections = List(3) { index ->
                            launch {
                                client.webSocket("/novel-ws/user$index/conv$index") {
                                    // Send a message
                                    val message = buildJsonObject {
                                        put("type", "text")
                                        put("content", "사용자 $index 의 메시지")
                                    }.toString()
                                    
                                    send(Frame.Text(message))
                                    
                                    // Wait for response
                                    withTimeout(5.seconds) {
                                        for (frame in incoming) {
                                            if (frame is Frame.Text) {
                                                val response = frame.readText()
                                                val json = Json.parseToJsonElement(response).jsonObject
                                                
                                                json["type"]?.jsonPrimitive?.content shouldBe "response"
                                                break
                                            }
                                        }
                                    }
                                    
                                    close()
                                }
                            }
                        }
                        
                        // Wait for all connections to complete
                        connections.forEach { it.join() }
                    }
                }
            }
        }
    }
}) 