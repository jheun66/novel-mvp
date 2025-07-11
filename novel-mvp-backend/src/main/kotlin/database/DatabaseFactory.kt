package com.novel.database

import com.novel.config.DBConfig
import com.novel.model.Users
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import javax.sql.DataSource

/**
 * Database initialization and management
 */
class DatabaseFactory(
    application: Application,
    private val databaseConfig: DBConfig
) {
    private val database: Database
    private val hikariCPManager: HikariCPManager
    
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    }

    init {
        // 애플리케이션 생명주기와 관련된 로그는 application.log 사용
        application.log.info("Initializing database...")

        // Create HikariCP manager instance
        hikariCPManager = HikariCPManager()
        
        // Create HikariCP data source
        val dataSource = hikariCPManager.createDataSource(databaseConfig)
        
        // Run Flyway migrations
        runFlywayMigrations(dataSource)
        
        // Connect Exposed to HikariCP
        database = Database.connect(dataSource)
        
        application.log.info("Database initialized successfully")
        
        // Seed development data if needed
        if (databaseConfig.isDevelopment) {
            seedDevelopmentData()
        }
    }
    
    /**
     * Run Flyway database migrations
     */
    private fun runFlywayMigrations(dataSource: DataSource) {
        try {
            // 내부 로직은 클래스 로거 사용
            logger.info("Starting Flyway migrations...")
            
            val flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(databaseConfig.flywayConfig.locations)
                .baselineOnMigrate(databaseConfig.flywayConfig.baselineOnMigrate)
                .validateMigrationNaming(databaseConfig.flywayConfig.validateMigrationNaming)
                .cleanDisabled(!databaseConfig.isDevelopment)
                .load()
            
            if (databaseConfig.isDevelopment) {
                logger.info("Development mode: Cleaning database before migration")
                flyway.clean()
            }
            
            val result = flyway.migrate()
            logger.info("Flyway migration completed. Applied ${result.migrationsExecuted} migrations")
            
        } catch (e: Exception) {
            logger.error("Flyway migration failed", e)
            throw e
        }
    }
    
    /**
     * Close database connections
     */
    fun close() {
        logger.info("Closing database connections...")
        hikariCPManager.close()
        logger.info("Database connections closed")
    }
    
    /**
     * Get connection pool statistics
     */
    fun getPoolStats() = hikariCPManager.getPoolStats()

    /**
     * Seed development data
     */
    private fun seedDevelopmentData() {
        logger.info("Development mode - checking for test data seeding")
        
        try {
            transaction {
                // Check if test user already exists
                val existingUser = Users.selectAll()
                    .where { Users.email eq "test@example.com" }
                    .singleOrNull()

                if (existingUser == null) {
                    // Create test user with generated UUID
                    val testUser = Users.insert {
                        it[id] = UUID.randomUUID()
                        it[email] = "test@example.com"
                        it[username] = "testuser"
                        it[passwordHash] = "placeholder_hash"
                    }

                    val testUserId = testUser[Users.id].value
                    logger.info("Created test user with ID: $testUserId")

                    logger.info("Successfully seeded development data with test user")
                } else {
                    logger.info("Test user already exists, skipping seed data")
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to seed development data", e)
            // Don't throw - development seeding failure shouldn't stop the app
        }
    }
} 
