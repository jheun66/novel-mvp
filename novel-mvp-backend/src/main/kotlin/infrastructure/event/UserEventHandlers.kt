package com.novel.infrastructure.event

import com.novel.application.user.DomainEventPublisher
import com.novel.domain.user.UserDomainEvent
import com.novel.domain.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

class UserEventHandlers : KoinComponent {
    private val logger = LoggerFactory.getLogger(UserEventHandlers::class.java)
    private val userRepository: UserRepository by inject()
    private val eventPublisher: DomainEventPublisher by inject()
    
    private val eventScope = CoroutineScope(Dispatchers.IO)
    
    fun startListening() {
        logger.info("Starting user event handlers")
        
        eventScope.launch {
            eventPublisher.events.collect { event ->
                try {
                    handleEvent(event)
                } catch (e: Exception) {
                    logger.error("Error handling event ${event::class.simpleName}", e)
                }
            }
        }
    }
    
    private suspend fun handleEvent(event: UserDomainEvent) {
        when (event) {
            is UserDomainEvent.StoryGenerated -> handleStoryGenerated(event)
            is UserDomainEvent.UserCreated -> handleUserCreated(event)
            is UserDomainEvent.PersonalityProfileUpdated -> handlePersonalityProfileUpdated(event)
            is UserDomainEvent.SubscriptionUpgraded -> handleSubscriptionUpgraded(event)
        }
    }
    
    private suspend fun handleStoryGenerated(event: UserDomainEvent.StoryGenerated) {
        logger.info("Handling story generated event for user: ${event.userId.value}")
        
        val user = userRepository.findById(event.userId)
        if (user != null) {
            user.incrementStoryCount()
            userRepository.update(user)
            logger.info("Updated story count for user ${event.userId.value}: ${user.dailyStoryCount} daily, ${user.totalStoriesGenerated} total")
        } else {
            logger.error("User not found for story generation event: ${event.userId.value}")
        }
    }
    
    private suspend fun handleUserCreated(event: UserDomainEvent.UserCreated) {
        logger.info("User created: ${event.email.value} via ${event.authProvider}")
        // Could send welcome email, create default preferences, etc.
    }
    
    private suspend fun handlePersonalityProfileUpdated(event: UserDomainEvent.PersonalityProfileUpdated) {
        logger.info("Personality profile updated for user: ${event.userId.value}")
        // Could trigger personalized content recommendations
    }
    
    private suspend fun handleSubscriptionUpgraded(event: UserDomainEvent.SubscriptionUpgraded) {
        logger.info("Subscription upgraded for user: ${event.userId.value} to ${event.newStatus}")
        // Could send confirmation email, unlock features, etc.
    }
}

// Daily reset job for story counts
class DailyStoryCountResetJob : KoinComponent {
    private val logger = LoggerFactory.getLogger(DailyStoryCountResetJob::class.java)
    private val userRepository: UserRepository by inject()
    
    suspend fun resetDailyCounts() {
        logger.info("Resetting daily story counts for all users")
        try {
            userRepository.resetDailyStoryCounts()
            logger.info("Daily story counts reset successfully")
        } catch (e: Exception) {
            logger.error("Failed to reset daily story counts", e)
        }
    }
}
