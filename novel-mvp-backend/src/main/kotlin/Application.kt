package com.novel

import com.novel.infrastructure.event.UserEventHandlers
import com.novel.scheduler.configureDailyResetScheduler
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureSerialization()
    configureDatabase()
    configureHTTP()
    configureWebSocket()
    configureSecurity()
    configureRouting()
    configureMonitoring()
    configureOpenAPI()
    startEventHandlers()
    configureDailyResetScheduler()
}

fun Application.startEventHandlers() {
    val userEventHandlers by inject<UserEventHandlers>()
    userEventHandlers.startListening()
    log.info("Domain event handlers started")
}
