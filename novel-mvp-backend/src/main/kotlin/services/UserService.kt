package com.novel.services

import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

class UserService {
    private val logger = LoggerFactory.getLogger(UserService::class.java)

    fun createUser(email: String, username: String) {
        logger.info("Creating new user - email: {}, username: {}", email, username)

        try {
            transaction {
                // Database operations will be logged with context
                logger.debug("Inserting user into database")
                // ... actual insert
            }

            logger.info("User created successfully - email: {}", email)
        } catch (e: Exception) {
            logger.error("Failed to create user - email: {}, error: {}", email, e.message, e)
            throw BusinessException(
                code = "USER_CREATION_FAILED",
                message = "Unable to create user account",
                statusCode = HttpStatusCode.InternalServerError,
                details = mapOf("email" to email)
            )
        }
    }
}

class BusinessException(
    val code: String,
    override val message: String,
    val statusCode: HttpStatusCode = HttpStatusCode.BadRequest,
    val details: Map<String, String>? = null
) : Exception(message)