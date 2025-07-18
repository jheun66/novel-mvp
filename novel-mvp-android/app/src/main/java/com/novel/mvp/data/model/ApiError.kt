package com.novel.mvp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
    val details: Map<String, String>? = null
)

@Serializable
data class SuccessResponse(
    val message: String
)

sealed class ApiResult<T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error<T>(val error: ErrorResponse) : ApiResult<T>()
    data class Loading<T>(val isLoading: Boolean = true) : ApiResult<T>()
}

class UserNotFoundException(message: String) : Exception(message)
class UserAlreadyExistsException(message: String) : Exception(message)
class InvalidCredentialsException(message: String) : Exception(message)
class PaymentFailedException(message: String) : Exception(message)