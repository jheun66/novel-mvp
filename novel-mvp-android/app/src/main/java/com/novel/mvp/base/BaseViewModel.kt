package com.novel.mvp.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<Intent : MviIntent, State : MviViewState, SideEffect : MviSideEffect> : ViewModel() {
    
    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<State> = _state.asStateFlow()
    
    private val _sideEffects = Channel<SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()
    
    protected val currentState: State
        get() = _state.value
    
    abstract fun createInitialState(): State
    
    abstract fun handleIntent(intent: Intent)
    
    protected fun setState(reducer: State.() -> State) {
        _state.value = currentState.reducer()
    }
    
    protected fun sendSideEffect(sideEffect: SideEffect) {
        viewModelScope.launch {
            _sideEffects.send(sideEffect)
        }
    }
    
    fun processIntent(intent: Intent) {
        handleIntent(intent)
    }
}