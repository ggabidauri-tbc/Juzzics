package com.example.juzzics.common.base.viewModel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Base for all ViewModels. Pass every [StateKey] the ViewModel uses; each one gets a Compose
 * state that starts at the key's default.
 *
 * Inside the ViewModel (and its `logics/` extension functions):
 *  - read:  `IS_PLAYING()`, `!ARTIST` (String keys)
 *  - write: `IS_PLAYING(true)`, `value saveIn KEY`
 *  - load:  `launch { call(useCase(), KEY) }` (handles loading + error events)
 *  - flows: `flow.collectIn(KEY)`
 *
 * In Composables, in context of [BaseState] (see `with2(states, SomeVM)`):
 *  - `KEY()`, `!STRING_KEY`, `KEY.stateValue()` (non-Composable read)
 *
 * [Action]s go from Screen to ViewModel, [UiEvent]s from ViewModel to Screen.
 * Every ViewModel also has the [LOADING] state: true while any `launch(emitLoadingAction = true)` runs.
 */
abstract class BaseViewModel(keys: List<StateKey<*>>) : ViewModel() {
    /** buffered, so events sent while the screen isn't collecting (yet) aren't lost */
    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvent: Flow<UiEvent> = events.receiveAsFlow()

    /** for Compose */
    val stateList: BaseState = (keys + LOADING).distinct().associateWith { mutableStateOf(it.default) }

    /** how many loading jobs are running, so overlapping ones don't hide the loader early */
    private var activeLoads = 0

    abstract fun onAction(action: Action)

    /** launches coroutine in viewModelScope. used in combination with [call] to make requestCalls.
     *  also handles to emit message(or Error) and loading events. */
    fun launch(
        emitLoadingAction: Boolean = true,
        emitErrorMsgAction: Boolean = false,
        onStart: (CoroutineScope.() -> Unit)? = null,
        onFinish: (() -> Unit)? = null,
        onException: ((Exception) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch {
            onStart?.invoke(this)
            if (emitLoadingAction) loadingStarted()
            try {
                block.invoke(this)
            } catch (e: CancellationException) {
                throw e // cancelled (e.g. screen closed): not an error
            } catch (e: Exception) {
                onException?.invoke(e)
                handleException(e, emitErrorMsgAction)
            } finally {
                if (emitLoadingAction) loadingFinished()
            }
        }.apply {
            invokeOnCompletion { onFinish?.invoke() }
        }
    }

    private fun loadingStarted() {
        activeLoads++
        updateState(LOADING, true)
    }

    private fun loadingFinished() {
        activeLoads = (activeLoads - 1).coerceAtLeast(0)
        updateState(LOADING, activeLoads > 0)
    }

    private fun handleException(e: Exception, emitErrorMsgAction: Boolean) {
        e.printStackTrace()
        if (!emitErrorMsgAction) return
        val msg = when (e) {
            is IOException -> e.message ?: "network Error"
            else -> e.message ?: "some error occurred"
        }
        UiEvent.Message(msg).emit()
    }

    /** saves a successful [response] in [key]; on failure keeps the old value and emits the error message. */
    suspend fun <T> CoroutineScope.call(
        response: Result<T>,
        key: StateKey<T>,
        onError: (Throwable?) -> Unit = {},
        onSuccess: (T) -> Unit = {},
    ) {
        if (!isActive) return
        response
            .onSuccess {
                onSuccess(it)
                updateState(key, it)
            }
            .onFailure {
                onError(it)
                it.message?.let { msg -> emitEvent(UiEvent.Message(msg)) }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> stateOf(key: StateKey<T>): MutableState<T> =
        (stateList[key] ?: error("State '$key' isn't registered in ${this::class.simpleName}"))
                as MutableState<T>

    fun <T> updateState(key: StateKey<T>, value: T) {
        stateOf(key).value = value
    }


    // ---------------------- State Getters ----------------------

    /** returns the state's current value */
    operator fun <T> StateKey<T>.invoke(): T = stateOf(this).value

    /** returns a String state's value: `!ARTIST` */
    operator fun StateKey<String>.not(): String = stateOf(this).value


    // ---------------------- State Setters ----------------------

    /** sets the state's value: `IS_PLAYING(true)` */
    operator fun <T> StateKey<T>.invoke(value: T) = updateState(this, value)

    /** sets the state's value: `true saveIn IS_PLAYING` */
    infix fun <T> T.saveIn(key: StateKey<T>) = updateState(key, this)

    /** keeps [key] updated with every value of this flow while the ViewModel lives */
    fun <T> Flow<T>.collectIn(key: StateKey<T>): Job =
        viewModelScope.launch { collect { updateState(key, it) } }


    // ---------------------- Emit UiEvents ----------------------

    /** sends the event to the screen */
    fun <T : UiEvent> T.emit() {
        events.trySend(this)
    }

    /** use inside coroutineScope to send the event */
    suspend fun emitEvent(uiEvent: UiEvent) = events.send(uiEvent)
}
