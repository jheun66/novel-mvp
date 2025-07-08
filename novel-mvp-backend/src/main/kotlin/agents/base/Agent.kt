package com.novel.agents.base

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Base Agent interface for all agents in the system
 */
interface Agent<TInput, TOutput> {
    val name: String
    suspend fun process(input: TInput): TOutput
}

/**
 * Agent that can process streaming data
 */
interface StreamingAgent<TInput, TOutput> : Agent<TInput, Flow<TOutput>> {
    suspend fun processStream(input: Flow<TInput>): Flow<TOutput>
}

/**
 * Base message for agent communication
 */
@Serializable
data class AgentMessage<T>(
    val id: String,
    val from: String,
    val to: String,
    val payload: T,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Agent communication channel using A2A pattern
 */
interface AgentCommunicator {
    suspend fun <T> send(message: AgentMessage<T>)
    suspend fun <T> subscribe(agentName: String, handler: suspend (AgentMessage<T>) -> Unit)
}
