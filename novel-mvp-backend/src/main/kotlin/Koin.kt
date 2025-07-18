package com.novel

import com.novel.di.authModule
import com.novel.di.configModule
import com.novel.di.databaseModule
import com.novel.di.serviceModule
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(
            // Application 인스턴스를 먼저 등록
            module {
                single<Application> { this@configureKoin }
            },
            // 설정 모듈 (다른 모듈들이 의존하므로 먼저 로드)
            configModule,
            authModule,
            serviceModule,
            databaseModule
        )
    }
}