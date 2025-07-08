package com.novel.agents.base

import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple implementation of A2A (Agent-to-Agent) communication
 */
class SimpleAgentCommunicator : AgentCommunicator {
    private val channels = ConcurrentHashMap<String, Channel<AgentMessage<*>>>()
    private val subscribers = ConcurrentHashMap<String, MutableList<suspend (AgentMessage<*>) -> Unit>>()
    
    init {
        // Create channels for known agents
        listOf("conversation", "emotion-analysis", "story-generation", "fish-speech").forEach { agent ->
            channels[agent] = Channel(Channel.BUFFERED)
        }
    }
    
    override suspend fun <T> send(message: AgentMessage<T>) {
        // Send to specific agent channel
        channels[message.to]?.send(message)
        
        // Notify subscribers
        subscribers[message.to]?.forEach { handler ->
            @Suppress("UNCHECKED_CAST")
            handler(message as AgentMessage<*>)
        }
    }
    
    override suspend fun <T> subscribe(agentName: String, handler: suspend (AgentMessage<T>) -> Unit) {
        subscribers.computeIfAbsent(agentName) { mutableListOf() }
            .add(handler as suspend (AgentMessage<*>) -> Unit)
    }
    
    fun getChannel(agentName: String): Channel<AgentMessage<*>>? = channels[agentName]
}
