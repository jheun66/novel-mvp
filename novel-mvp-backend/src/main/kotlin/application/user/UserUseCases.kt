package com.novel.application.user

import com.novel.domain.user.*
import com.novel.services.JWTService
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.mindrot.jbcrypt.BCrypt
import java.util.*

// External Service Interfaces
interface DomainEventPublisher {
    val events: SharedFlow<UserDomainEvent>
    suspend fun publish(event: UserDomainEvent)
}

interface PersonalityAnalyzer {
    suspend fun analyzeResponses(responses: Map<String, Int>): Map<PersonalityTrait, Int>
}

interface PaymentService {
    suspend fun processPayment(token: String, amount: Double): PaymentResult
}

data class PaymentResult(
    val success: Boolean,
    val transactionId: String? = null,
    val error: String? = null
)

// Use Cases
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JWTService,
    private val eventPublisher: DomainEventPublisher
) {
    suspend fun execute(request: CreateUserRequest): AuthResponse {
        val email = Email(request.email)
        val username = Username(request.username)
        
        // Check if user already exists
        if (userRepository.existsByEmail(email)) {
            throw UserAlreadyExistsException("User with email ${email.value} already exists")
        }
        
        if (userRepository.existsByUsername(username)) {
            throw UserAlreadyExistsException("Username ${username.value} is already taken")
        }
        
        // Hash password for local auth
        val passwordHash = request.password?.let { 
            BCrypt.hashpw(it, BCrypt.gensalt()) 
        }
        
        val user = User(
            id = UserId(),
            email = email,
            username = username,
            displayName = request.displayName,
            profileImageUrl = request.profileImageUrl,
            authProvider = AuthProvider.valueOf(request.authProvider),
            passwordHash = passwordHash
        )
        
        val savedUser = userRepository.save(user)
        
        // Generate tokens
        val tokens = jwtService.generateTokenPair(
            userId = savedUser.id.value.toString(),
            email = savedUser.email.value
        )
        
        // Update refresh token
        savedUser.updateRefreshToken(tokens.refreshToken)
        userRepository.update(savedUser)
        
        // Publish domain event
        eventPublisher.publish(
            UserDomainEvent.UserCreated(
                userId = savedUser.id,
                email = savedUser.email,
                authProvider = savedUser.authProvider
            )
        )
        
        return AuthResponse(
            user = savedUser.toResponse(),
            tokens = TokensResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken
            )
        )
    }
}

class LoginUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JWTService
) {
    suspend fun execute(request: LoginRequest): AuthResponse {
        val email = Email(request.email)
        val user = userRepository.findByEmail(email)
            ?: throw InvalidCredentialsException("Invalid email or password")
        
        // Check if user is active
        if (!user.isActive) {
            throw InvalidCredentialsException("Account is disabled")
        }
        
        // Verify password for local auth
        if (user.authProvider == AuthProvider.LOCAL) {
            val passwordValid = user.passwordHash?.let {
                BCrypt.checkpw(request.password, it)
            } ?: false
            
            if (!passwordValid) {
                throw InvalidCredentialsException("Invalid email or password")
            }
        } else {
            throw InvalidCredentialsException("Please use ${user.authProvider} login")
        }
        
        // Generate tokens
        val tokens = jwtService.generateTokenPair(
            userId = user.id.value.toString(),
            email = user.email.value
        )
        
        // Update last login and refresh token
        user.updateLastLogin()
        user.updateRefreshToken(tokens.refreshToken)
        userRepository.update(user)
        
        return AuthResponse(
            user = user.toResponse(),
            tokens = TokensResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken
            )
        )
    }
}

class OAuthLoginUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JWTService,
    private val eventPublisher: DomainEventPublisher
) {
    suspend fun execute(request: OAuthLoginRequest): AuthResponse {
        val email = Email(request.email)
        val authProvider = AuthProvider.valueOf(request.provider.uppercase())
        
        // Check if user exists
        var user = userRepository.findByEmail(email)
        
        if (user == null) {
            // Create new user from OAuth
            val username = generateUniqueUsername(request.email)
            
            user = User(
                id = UserId(),
                email = email,
                username = username,
                displayName = request.displayName,
                profileImageUrl = request.profileImageUrl,
                authProvider = authProvider,
                passwordHash = null // OAuth users don't have passwords
            )
            
            user = userRepository.save(user)
            
            // Publish domain event for new user
            eventPublisher.publish(
                UserDomainEvent.UserCreated(
                    userId = user.id,
                    email = user.email,
                    authProvider = user.authProvider
                )
            )
        } else {
            // Update existing user
            if (user.authProvider != authProvider) {
                throw InvalidCredentialsException("Account exists with different provider: ${user.authProvider}")
            }
            
            // Update profile if needed
            if (request.profileImageUrl != null && user.profileImageUrl != request.profileImageUrl) {
                user.profileImageUrl = request.profileImageUrl
            }
            
            user.updateLastLogin()
            user = userRepository.update(user)
        }
        
        // Generate tokens
        val tokens = jwtService.generateTokenPair(
            userId = user.id.value.toString(),
            email = user.email.value
        )
        
        // Update refresh token
        user.updateRefreshToken(tokens.refreshToken)
        userRepository.update(user)
        
        return AuthResponse(
            user = user.toResponse(),
            tokens = TokensResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken
            )
        )
    }
    
    private suspend fun generateUniqueUsername(email: String): Username {
        val baseUsername = email.substringBefore('@')
            .replace(".", "_")
            .replace("-", "_")
            .take(40) // Leave room for suffix
        
        var username = Username(baseUsername)
        var suffix = 1
        
        while (userRepository.existsByUsername(username)) {
            username = Username("${baseUsername}_$suffix")
            suffix++
        }
        
        return username
    }
}

class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val jwtService: JWTService
) {
    suspend fun execute(request: RefreshTokenRequest): TokensResponse {
        // Verify refresh token
        val decodedToken = jwtService.verifyToken(request.refreshToken)
            ?: throw InvalidCredentialsException("Invalid refresh token")
        
        val userId = decodedToken.getClaim("userId").asString()
            ?: throw InvalidCredentialsException("Invalid refresh token")
        
        val user = userRepository.findById(UserId(UUID.fromString(userId)))
            ?: throw UserNotFoundException("User not found")
        
        // Check if refresh token matches
        if (user.refreshToken != request.refreshToken) {
            throw InvalidCredentialsException("Invalid refresh token")
        }
        
        // Generate new tokens
        val tokens = jwtService.generateTokenPair(
            userId = user.id.value.toString(),
            email = user.email.value
        )
        
        // Update refresh token
        user.updateRefreshToken(tokens.refreshToken)
        userRepository.update(user)
        
        return TokensResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken
        )
    }
}

class GetUserUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(userId: String): UserResponse {
        val id = UserId(UUID.fromString(userId))
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException("User not found with id: $userId")
        
        // Check subscription expiry
        user.checkSubscriptionExpiry()
        
        return user.toResponse()
    }
}

class UpdateUserProfileUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(userId: String, request: UpdateUserProfileRequest): UserResponse {
        val id = UserId(UUID.fromString(userId))
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException("User not found with id: $userId")
        
        // Validate new username if provided
        request.username?.let {
            val newUsername = Username(it)
            if (newUsername != user.username && userRepository.existsByUsername(newUsername)) {
                throw UserAlreadyExistsException("Username ${newUsername.value} is already taken")
            }
        }
        
        user.updateProfile(
            username = request.username?.let { Username(it) },
            displayName = request.displayName,
            profileImageUrl = request.profileImageUrl
        )
        
        val updatedUser = userRepository.update(user)
        return updatedUser.toResponse()
    }
}

class UpdatePersonalityProfileUseCase(
    private val userRepository: UserRepository,
    private val personalityAnalyzer: PersonalityAnalyzer,
    private val eventPublisher: DomainEventPublisher
) {
    suspend fun execute(userId: String, request: PersonalityTestRequest): UserResponse {
        val id = UserId(UUID.fromString(userId))
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException("User not found with id: $userId")
        
        // Analyze personality based on test responses
        val traits = personalityAnalyzer.analyzeResponses(request.responses)
        
        val profile = PersonalityProfile(
            traits = traits,
            preferredGenres = request.preferredGenres.map { StoryGenre.valueOf(it) }.toSet()
        )
        
        user.updatePersonalityProfile(profile)
        val updatedUser = userRepository.update(user)
        
        // Publish domain event
        eventPublisher.publish(
            UserDomainEvent.PersonalityProfileUpdated(
                userId = user.id,
                profile = profile
            )
        )
        
        return updatedUser.toResponse()
    }
}

class UpgradeSubscriptionUseCase(
    private val userRepository: UserRepository,
    private val paymentService: PaymentService,
    private val eventPublisher: DomainEventPublisher
) {
    suspend fun execute(userId: String, paymentToken: String, months: Int = 1): UserResponse {
        val id = UserId(UUID.fromString(userId))
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException("User not found with id: $userId")
        
        // Process payment
        val paymentResult = paymentService.processPayment(paymentToken, calculateAmount(months))
        if (!paymentResult.success) {
            throw PaymentFailedException("Payment processing failed: ${paymentResult.error}")
        }

        val expiresAt = Clock.System.now().plus(months, DateTimeUnit.MONTH, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault())
        user.upgradeToPremium(expiresAt)
        
        val updatedUser = userRepository.update(user)
        
        // Publish domain event
        eventPublisher.publish(
            UserDomainEvent.SubscriptionUpgraded(
                userId = user.id,
                newStatus = SubscriptionStatus.PREMIUM,
                expiresAt = expiresAt
            )
        )
        
        return updatedUser.toResponse()
    }
    
    private fun calculateAmount(months: Int): Double {
        return months * 9.99 // $9.99 per month
    }
}

class CheckStoryGenerationEligibilityUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(userId: String): Boolean {
        val id = UserId(UUID.fromString(userId))
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException("User not found with id: $userId")
        
        return user.canGenerateStory()
    }
}

class LogoutUseCase(
    private val userRepository: UserRepository
) {
    suspend fun execute(userId: String) {
        val id = UserId(UUID.fromString(userId))
        val user = userRepository.findById(id)
            ?: throw UserNotFoundException("User not found with id: $userId")
        
        // Clear refresh token
        user.updateRefreshToken(null)
        userRepository.update(user)
    }
}
