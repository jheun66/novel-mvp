package com.novel.config

import io.github.cdimascio.dotenv.dotenv
import io.ktor.server.application.*

data class DBConfig(
    val url: String,
    val driver: String,
    val user: String,
    val password: String,
    val isDevelopment: Boolean,
    val hikariConfig: HikariConfig,
    val flywayConfig: FlywayConfig
) {
    data class HikariConfig(
        val maxPoolSize: Int,
        val minIdle: Int,
        val autoCommit: Boolean,
        val transactionIsolation: String,
        val connectionTimeout: Long,
        val idleTimeout: Long,
        val maxLifetime: Long
    )

    data class FlywayConfig(
        val baselineOnMigrate: Boolean,
        val locations: String,
        val validateMigrationNaming: Boolean
    )

    companion object {
        fun fromApplication(application: Application): DBConfig {
            val config = application.environment.config
            val isDevelopment = config.property("ktor.development").getString().toBoolean()

            // Load environment variables from .env file
            val dotenv = dotenv {
                ignoreIfMissing = true // 개발 환경에서 .env 파일이 없어도 에러 발생하지 않음
            }

            return DBConfig(
                url = dotenv["DB_URL"] ?: System.getenv("DB_URL") ?: config.property("database.postgres.url").getString(),
                driver = config.property("database.postgres.driver").getString(),
                user = dotenv["DB_USER"] ?: System.getenv("DB_USER") ?: config.property("database.postgres.user").getString(),
                password = dotenv["DB_PASSWORD"] ?: System.getenv("DB_PASSWORD") ?: config.property("database.postgres.password").getString(),
                isDevelopment = isDevelopment,
                hikariConfig = HikariConfig(
                    maxPoolSize = config.property("database.hikaricp.maxPoolSize").getString().toInt(),
                    minIdle = config.property("database.hikaricp.minIdle").getString().toInt(),
                    autoCommit = config.property("database.hikaricp.autoCommit").getString().toBoolean(),
                    transactionIsolation = config.property("database.hikaricp.transactionIsolation").getString(),
                    connectionTimeout = config.property("database.hikaricp.connectionTimeout").getString().toLong(),
                    idleTimeout = config.property("database.hikaricp.idleTimeout").getString().toLong(),
                    maxLifetime = config.property("database.hikaricp.maxLifetime").getString().toLong()
                ),
                flywayConfig = FlywayConfig(
                    baselineOnMigrate = config.property("database.flyway.baselineOnMigrate").getString().toBoolean(),
                    locations = config.property("database.flyway.locations").getString(),
                    validateMigrationNaming = config.property("database.flyway.validateMigrationNaming").getString().toBoolean()
                )
            )
        }
    }
}
