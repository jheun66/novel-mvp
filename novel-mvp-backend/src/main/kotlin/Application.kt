package com.novel

import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureSerialization()
    configureHTTP()
    configureWebSocket()
    configureRouting()
    configureSecurity()
}
