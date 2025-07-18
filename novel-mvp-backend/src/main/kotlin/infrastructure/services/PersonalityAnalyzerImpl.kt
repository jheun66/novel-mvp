package com.novel.infrastructure.services

import com.novel.application.user.PersonalityAnalyzer
import com.novel.domain.user.PersonalityTrait
import kotlin.math.roundToInt

class PersonalityAnalyzerImpl : PersonalityAnalyzer {
    
    override suspend fun analyzeResponses(responses: Map<String, Int>): Map<PersonalityTrait, Int> {
        // Simple personality analysis algorithm
        // In production, use more sophisticated ML model or psychological framework
        
        val traits = mutableMapOf<PersonalityTrait, Int>()
        
        // Map questions to traits (simplified example)
        val questionTraitMap = mapOf(
            "q1" to PersonalityTrait.OPENNESS,
            "q2" to PersonalityTrait.CONSCIENTIOUSNESS,
            "q3" to PersonalityTrait.EXTROVERSION,
            "q4" to PersonalityTrait.AGREEABLENESS,
            "q5" to PersonalityTrait.NEUROTICISM,
            "q6" to PersonalityTrait.CREATIVITY,
            "q7" to PersonalityTrait.EMOTIONAL_DEPTH,
            "q8" to PersonalityTrait.OPENNESS,
            "q9" to PersonalityTrait.CONSCIENTIOUSNESS,
            "q10" to PersonalityTrait.EXTROVERSION
        )
        
        // Calculate trait scores
        val traitScores = mutableMapOf<PersonalityTrait, MutableList<Int>>()
        
        responses.forEach { (questionId, score) ->
            questionTraitMap[questionId]?.let { trait ->
                traitScores.getOrPut(trait) { mutableListOf() }.add(score)
            }
        }
        
        // Average scores for each trait
        PersonalityTrait.entries.forEach { trait ->
            val scores = traitScores[trait] ?: listOf(50) // Default to middle value
            val average = if (scores.isNotEmpty()) {
                scores.average().roundToInt()
            } else {
                50
            }
            
            // Normalize to 0-100 scale
            traits[trait] = average.coerceIn(0, 100)
        }
        
        return traits
    }
}
