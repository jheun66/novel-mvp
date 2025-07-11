package com.novel

import com.novel.services.WebSocketMessage
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// 전역 JSON 설정
val globalJson = Json {
    prettyPrint = true
    isLenient = true
    ignoreUnknownKeys = true
    classDiscriminator = "type"
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

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(globalJson)
    }
}
