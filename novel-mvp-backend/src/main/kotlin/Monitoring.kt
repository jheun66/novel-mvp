package com.novel

import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import java.util.*

fun Application.configureMonitoring() {
    install(CallLogging) {
        // TODO : CallLogging 설정
        mdc("correlationId") { call ->
            call.request.header("X-Correlation-ID")
                ?: call.request.header("X-Request-ID")
                ?: UUID.randomUUID().toString()
        }
    }
}