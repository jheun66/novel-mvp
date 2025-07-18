package com.novel

import com.novel.database.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log
import org.koin.ktor.ext.inject
import kotlin.getValue

fun Application.configureDatabase() {
    val databaseFactory by inject<DatabaseFactory>()
    databaseFactory
    // Database is already initialized through Koin
    log.info("Database configuration completed")

    // Ensure database connections are closed on application shutdown
    monitor.subscribe(ApplicationStopped) {
        databaseFactory.close()
        log.info("Database connection pool closed")
    }
}
