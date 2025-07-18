package com.novel.mvp.base

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
inline fun <Intent : MviIntent, State : MviViewState, SideEffect : MviSideEffect, reified VM : BaseViewModel<Intent, State, SideEffect>> BaseComposeScreen(
    viewModel: VM = viewModel(),
    crossinline onSideEffect: (SideEffect) -> Unit = {},
    crossinline content: @Composable (state: State, onIntent: (Intent) -> Unit) -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(viewModel) {
        viewModel.sideEffects.collectLatest { sideEffect ->
            onSideEffect(sideEffect)
        }
    }
    
    content(state) { intent ->
        viewModel.processIntent(intent)
    }
}