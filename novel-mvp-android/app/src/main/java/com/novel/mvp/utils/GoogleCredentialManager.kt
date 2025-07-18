package com.novel.mvp.utils

import android.content.Context
import android.util.Log
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

class GoogleCredentialManager(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)
    
    companion object {
        private const val TAG = "GoogleCredentialManager"
        private const val WEB_CLIENT_ID = "258416138249-4jn7b59krem5radf2viaagk7gh7qmegf.apps.googleusercontent.com"
    }

    suspend fun signIn(): GoogleSignInResult {
        return try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(WEB_CLIENT_ID)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            handleSignInResult(result)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Google Sign-In failed", e)
            GoogleSignInResult.Error(e.message ?: "Sign-in failed")
        } catch (e: CancellationException) {
            Log.d(TAG, "Google Sign-In cancelled")
            GoogleSignInResult.Cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            GoogleSignInResult.Error("Unexpected error occurred")
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse): GoogleSignInResult {
        return when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)
                        
                        GoogleSignInResult.Success(
                            idToken = googleIdTokenCredential.idToken,
                            email = googleIdTokenCredential.id,
                            displayName = googleIdTokenCredential.displayName ?: "",
                            profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()
                        )
                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "Failed to parse Google ID token", e)
                        GoogleSignInResult.Error("Failed to parse Google ID token")
                    }
                } else {
                    Log.e(TAG, "Unexpected credential type: ${credential.type}")
                    GoogleSignInResult.Error("Unexpected credential type")
                }
            }
            else -> {
                Log.e(TAG, "Unexpected credential type: ${credential.type}")
                GoogleSignInResult.Error("Unexpected credential type")
            }
        }
    }
}

sealed class GoogleSignInResult {
    data class Success(
        val idToken: String,
        val email: String,
        val displayName: String,
        val profilePictureUri: String?
    ) : GoogleSignInResult()
    
    data class Error(val message: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
}