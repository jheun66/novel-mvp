package com.novel.mvp.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novel.mvp.di.AppModule
import com.novel.mvp.presentation.login.LoginScreen
import com.novel.mvp.presentation.websocket.WebSocketTestScreen
import com.novel.mvp.utils.GoogleSignInResult
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Initialize dependencies
    val httpClient = AppModule.provideHttpClient()
    val apiService = AppModule.provideApiService(httpClient)
    val tokenStorage = AppModule.provideTokenStorage(context)
    val authRepository = AppModule.provideAuthRepository(apiService, tokenStorage)
    val googleCredentialManager = AppModule.provideGoogleCredentialManager(context)
    val loginViewModel = AppModule.provideLoginViewModel(authRepository)

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                onNavigateToMain = { user ->
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate("register")
                },
                onGoogleSignIn = { viewModel ->
                    scope.launch {
                        when (val result = googleCredentialManager.signIn()) {
                            is GoogleSignInResult.Success -> {
                                // Send ID token directly as access token
                                viewModel.handleGoogleLoginResult(
                                    accessToken = result.idToken,
                                    email = result.email,
                                    displayName = result.displayName,
                                    profileImageUrl = result.profilePictureUri
                                )
                            }
                            is GoogleSignInResult.Error -> {
                                // Handle error - could show a toast or update UI
                            }
                            GoogleSignInResult.Cancelled -> {
                                // Handle cancellation
                            }
                        }
                    }
                },
                viewModel = loginViewModel
            )
        }
        
        composable("register") {
            // TODO : Implement RegisterScreen
        }
        
        composable("main") {
            // Initialize WebSocket test screen dependencies
            val httpClient = AppModule.provideHttpClient()
            val tokenStorage = AppModule.provideTokenStorage(context)
            val webSocketService = AppModule.provideStoryWebSocketService(httpClient, tokenStorage)
            val storyRepository = AppModule.provideStoryRepository(webSocketService)
            val webSocketTestViewModel = AppModule.provideWebSocketTestViewModel(storyRepository)
            
            WebSocketTestScreen(viewModel = webSocketTestViewModel)
        }
    }
}

