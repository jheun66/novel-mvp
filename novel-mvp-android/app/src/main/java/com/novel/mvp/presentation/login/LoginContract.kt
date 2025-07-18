package com.novel.mvp.presentation.login

import com.novel.mvp.base.MviIntent
import com.novel.mvp.base.MviSideEffect
import com.novel.mvp.base.MviViewState
import com.novel.mvp.data.model.UserResponse

sealed class LoginIntent : MviIntent {
    data class EmailChanged(val email: String) : LoginIntent()
    data class PasswordChanged(val password: String) : LoginIntent()
    object LoginClicked : LoginIntent()
    object GoogleLoginClicked : LoginIntent()
    object RegisterClicked : LoginIntent()
    object ClearError : LoginIntent()
}

data class LoginViewState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmailValid: Boolean = true,
    val isPasswordValid: Boolean = true
) : MviViewState

sealed class LoginSideEffect : MviSideEffect {
    data class NavigateToMain(val user: UserResponse) : LoginSideEffect()
    object NavigateToRegister : LoginSideEffect()
    object ShowGoogleSignIn : LoginSideEffect()
    data class ShowError(val message: String) : LoginSideEffect()
    data class ShowSuccess(val message: String) : LoginSideEffect()
}