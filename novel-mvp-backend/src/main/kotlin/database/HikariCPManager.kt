package com.novel.database

import com.novel.config.DBConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.Serializable

/**
 * HikariCP connection pool configuration and management
 */
class HikariCPManager {
    private lateinit var hikariDataSource: HikariDataSource

    /**
     * Create and configure HikariCP data source using DatabaseConfig
     */
    fun createDataSource(databaseConfig: DBConfig): HikariDataSource {
        // Create HikariCP with configuration
        hikariDataSource = createHikariDataSource(
            dbConfig = DatabaseConnectionConfig(
                jdbcUrl = databaseConfig.url,
                username = databaseConfig.user,
                password = databaseConfig.password,
                driverClassName = databaseConfig.driver
            ),
            maxPoolSize = databaseConfig.hikariConfig.maxPoolSize,
            minIdle = databaseConfig.hikariConfig.minIdle,
            isAutoCommit = databaseConfig.hikariConfig.autoCommit,
            transactionIsolation = databaseConfig.hikariConfig.transactionIsolation,
            connectionTimeout = databaseConfig.hikariConfig.connectionTimeout,
            idleTimeout = databaseConfig.hikariConfig.idleTimeout,
            maxLifetime = databaseConfig.hikariConfig.maxLifetime
        )
        return hikariDataSource
    }
    
    /**
     * Build HikariCP configuration
     */
    private fun createHikariDataSource(
        dbConfig: DatabaseConnectionConfig,
        maxPoolSize: Int,
        minIdle: Int,
        isAutoCommit: Boolean,
        transactionIsolation: String,
        connectionTimeout: Long,
        idleTimeout: Long,
        maxLifetime: Long
    ): HikariDataSource {
        val config = HikariConfig().apply {
            this.jdbcUrl = dbConfig.jdbcUrl
            this.username = dbConfig.username
            this.password = dbConfig.password
            this.driverClassName = dbConfig.driverClassName
            this.maximumPoolSize = maxPoolSize
            this.minimumIdle = minIdle
            this.isAutoCommit = isAutoCommit
            this.transactionIsolation = transactionIsolation
            this.connectionTimeout = connectionTimeout
            this.idleTimeout = idleTimeout
            this.maxLifetime = maxLifetime
            
            // Pool name
            this.poolName = "YourStoryHikariCP"
            
            // PostgreSQL specific optimizations
            this.addDataSourceProperty("cachePrepStmts", "true")
            this.addDataSourceProperty("prepStmtCacheSize", "250")
            this.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            this.addDataSourceProperty("useServerPrepStmts", "true")
        }
        
        return HikariDataSource(config)
    }
    
    /**
     * Close connection pool
     */
    fun close() {
        if (::hikariDataSource.isInitialized && !hikariDataSource.isClosed) {
            hikariDataSource.close()
        }
    }
    
    /**
     * Get pool statistics
     */
    fun getPoolStats(): PoolStats {
        return if (::hikariDataSource.isInitialized) {
            PoolStats(
                activeConnections = hikariDataSource.hikariPoolMXBean?.activeConnections ?: 0,
                idleConnections = hikariDataSource.hikariPoolMXBean?.idleConnections ?: 0,
                totalConnections = hikariDataSource.hikariPoolMXBean?.totalConnections ?: 0,
                threadsAwaitingConnection = hikariDataSource.hikariPoolMXBean?.threadsAwaitingConnection ?: 0
            )
        } else {
            PoolStats(0, 0, 0, 0)
        }
    }
    
    private data class DatabaseConnectionConfig(
        val jdbcUrl: String,
        val username: String,
        val password: String,
        val driverClassName: String
    )
    
    @Serializable
    data class PoolStats(
        val activeConnections: Int,
        val idleConnections: Int,
        val totalConnections: Int,
        val threadsAwaitingConnection: Int
    )
} 
