package com.novel.infrastructure.event

import com.novel.application.user.DomainEventPublisher
import com.novel.domain.user.UserDomainEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory

class DomainEventPublisherImpl : DomainEventPublisher {
    private val logger = LoggerFactory.getLogger(DomainEventPublisherImpl::class.java)

    private val _events = MutableSharedFlow<UserDomainEvent>()
    override val events = _events.asSharedFlow()

    override suspend fun publish(event: UserDomainEvent) {
        logger.info("Publishing domain event: ${event::class.simpleName} for user: ${event.userId.value}")
        _events.emit(event)
    }
}

// Simple in-memory implementation
// In production, use message queue like RabbitMQ
class EventBus {
    private val subscribers = mutableListOf<suspend (UserDomainEvent) -> Unit>()
    
    fun subscribe(handler: suspend (UserDomainEvent) -> Unit) {
        subscribers.add(handler)
    }
    
    suspend fun publish(event: UserDomainEvent) {
        subscribers.forEach { handler ->
            try {
                handler(event)
            } catch (e: Exception) {
                // Log error but don't fail
                println("Error handling event: ${e.message}")
            }
        }
    }
}
