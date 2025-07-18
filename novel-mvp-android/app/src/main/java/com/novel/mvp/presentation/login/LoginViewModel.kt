package com.novel.mvp.presentation.login

import androidx.lifecycle.viewModelScope
import com.novel.mvp.base.BaseViewModel
import com.novel.mvp.data.model.ApiResult
import com.novel.mvp.data.model.LoginRequest
import com.novel.mvp.data.model.OAuthLoginRequest
import com.novel.mvp.data.repository.AuthRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) : BaseViewModel<LoginIntent, LoginViewState, LoginSideEffect>() {

    override fun createInitialState(): LoginViewState = LoginViewState()

    override fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.EmailChanged -> handleEmailChanged(intent.email)
            is LoginIntent.PasswordChanged -> handlePasswordChanged(intent.password)
            is LoginIntent.LoginClicked -> handleLogin()
            is LoginIntent.GoogleLoginClicked -> handleGoogleLogin()
            is LoginIntent.RegisterClicked -> handleRegister()
            is LoginIntent.ClearError -> handleClearError()
        }
    }

    private fun handleEmailChanged(email: String) {
        setState {
            copy(
                email = email,
                isEmailValid = isValidEmail(email),
                error = null
            )
        }
    }

    private fun handlePasswordChanged(password: String) {
        setState {
            copy(
                password = password,
                isPasswordValid = password.length >= 6,
                error = null
            )
        }
    }

    private fun handleLogin() {
        if (!isValidForm()) return

        viewModelScope.launch {
            val request = LoginRequest(
                email = currentState.email,
                password = currentState.password
            )

            authRepository.login(request).collectLatest { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        setState { copy(isLoading = true, error = null) }
                    }
                    is ApiResult.Success -> {
                        setState { copy(isLoading = false) }
                        sendSideEffect(LoginSideEffect.NavigateToMain(result.data.user))
                    }
                    is ApiResult.Error -> {
                        setState { 
                            copy(
                                isLoading = false, 
                                error = result.error.message
                            ) 
                        }
                        sendSideEffect(LoginSideEffect.ShowError(result.error.message))
                    }
                }
            }
        }
    }

    private fun handleGoogleLogin() {
        sendSideEffect(LoginSideEffect.ShowGoogleSignIn)
    }

    fun handleGoogleLoginResult(accessToken: String, email: String, displayName: String, profileImageUrl: String?) {
        viewModelScope.launch {
            val request = OAuthLoginRequest(
                provider = "GOOGLE",
                accessToken = accessToken,
                email = email,
                displayName = displayName,
                profileImageUrl = profileImageUrl
            )

            authRepository.oAuthLogin(request).collectLatest { result ->
                when (result) {
                    is ApiResult.Loading -> {
                        setState { copy(isLoading = true, error = null) }
                    }
                    is ApiResult.Success -> {
                        setState { copy(isLoading = false) }
                        sendSideEffect(LoginSideEffect.NavigateToMain(result.data.user))
                    }
                    is ApiResult.Error -> {
                        setState { 
                            copy(
                                isLoading = false, 
                                error = result.error.message
                            ) 
                        }
                        sendSideEffect(LoginSideEffect.ShowError(result.error.message))
                    }
                }
            }
        }
    }

    private fun handleRegister() {
        sendSideEffect(LoginSideEffect.NavigateToRegister)
    }

    private fun handleClearError() {
        setState { copy(error = null) }
    }

    private fun isValidForm(): Boolean {
        val isEmailValid = isValidEmail(currentState.email)
        val isPasswordValid = currentState.password.length >= 6

        setState {
            copy(
                isEmailValid = isEmailValid,
                isPasswordValid = isPasswordValid,
                error = if (!isEmailValid || !isPasswordValid) {
                    "Please check your email and password"
                } else null
            )
        }

        return isEmailValid && isPasswordValid
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}