package com.novel.infrastructure.services

import com.novel.application.user.PaymentResult
import com.novel.application.user.PaymentService
import java.util.UUID

class PaymentServiceImpl : PaymentService {
    
    override suspend fun processPayment(token: String, amount: Double): PaymentResult {
        // Mock payment processing
        // In production, integrate with actual payment gateway (Stripe, PayPal, etc.)
        
        return if (token.isNotEmpty() && amount > 0) {
            // Simulate successful payment
            PaymentResult(
                success = true,
                transactionId = UUID.randomUUID().toString(),
                error = null
            )
        } else {
            // Simulate failed payment
            PaymentResult(
                success = false,
                transactionId = null,
                error = "Invalid payment token or amount"
            )
        }
    }
}

// For development/testing
class MockPaymentService : PaymentService {
    override suspend fun processPayment(token: String, amount: Double): PaymentResult {
        // Always succeed in development
        return PaymentResult(
            success = true,
            transactionId = "mock-${UUID.randomUUID()}",
            error = null
        )
    }
}
