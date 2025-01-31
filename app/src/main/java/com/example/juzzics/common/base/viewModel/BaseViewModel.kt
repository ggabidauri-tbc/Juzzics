package com.example.juzzics.common.base.viewModel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juzzics.common.base.extensions.isNotNull
import com.example.juzzics.common.base.extensions.postChange
import com.example.juzzics.common.base.extensions.takeAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** every responseModel Type that a serviceCall returns and/or is used in the current ViewModel,
 *  this [BaseViewModel] class takes it as a parameter a map of empty ViewStates for each model.
 *
 *  then it creates mutableStates for each viewState from the map.
 *  @see BaseViewModel.stateList
 *
 *  also has helper functions to make requestCall and it handles to save received data in the
 *  corresponding state and also emit actions of loading and showingMessage(or error).
 *
 *  use [launch] in combination with [call] like this :
 *  launch { call(someRepository.getFirstTestData(), TEST) }
 *  ------
 *  to get State manually use invoke() operator on a StateKeyString without passing a parameter
 *  to set State manually do the same but this time pass a value as a parameter
 *
 *  also you can use for getters: [state],[typeOf]
 *  or for setters: [setValue], [saveIn] [saveInStateOf]
 *  ------
 *  to get state outside a ViewModel use same invoke operator on a StateKeyString [com.example.juzzics.common.base.viewModel.invoke]
 *  but only if you are in Context of [BaseState]
 *
 *  see other getters by remembering value in Composable, or outside a Composable context here: [BaseState]
 *  ------
 *  also you can emit [UiEvent]s to the Screens and collect them with [listen]
 *  -----
 *  use [Action] to send an Actions From Screen to the ViewModel
 *  use [UiEvent] to send an UiEvents from ViewModel to the Screen
 * */
abstract class BaseViewModel(
    val states: Map<String, Any>
) : ViewModel() {
    val uiEvent = MutableSharedFlow<UiEvent>()

    /** for Compose */
    val stateList = states.map { it.key to it.value.createState() }.toMap()

    abstract fun onAction(action: Action)

    /** creates MutableState<ViewState<EachModel>> for each element of the states.
     * for Compose */
    private inline fun <reified T : Any?> T.createState(): MutableState<State<T>> =
        mutableStateOf(State(if (this.isNotNull()) (this as State<T>).data else null))

    /** launches coroutine in viewModelScope. used in combination with call function to make requestCalls.
     *  also handles to emit message(or Error) and loading Actions.
     *  @see call
     * */
    fun launch(
        emitLoadingAction: Boolean = true,
        emitErrorMsgAction: Boolean = false,
        propagateCancellationException: Boolean = false,
        onStart: (CoroutineScope.() -> Unit)? = null,
        onFinish: (() -> Unit)? = null,
        onException: ((Exception) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        return viewModelScope.launch {
            onStart?.invoke(this)
            if (emitLoadingAction) emitEvent(UiEvent.Loading())
            try {
                block.invoke(this)
            } catch (e: Exception) {
                onException?.invoke(e)
                if (emitLoadingAction) emitEvent(UiEvent.Loading(false))
                handleException(e, emitErrorMsgAction, propagateCancellationException)
            }
            if (emitLoadingAction) emitEvent(UiEvent.Loading(false))
        }.apply {
            invokeOnCompletion { onFinish?.invoke() }
        }
    }

    private fun handleException(
        e: Exception,
        emitErrorMsgAction: Boolean,
        propagateCancellationException: Boolean
    ) {
        e.printStackTrace()
        fun emitMsg(msg: String) {
            if (emitErrorMsgAction) UiEvent.Message(msg).emit()
        }
        when (e) {
            is CancellationException -> emitMsg("Cancellation Exception")
            is IOException -> emitMsg(e.message ?: "network Error")
            else -> emitMsg(e.message ?: "some error occurred")
        }
        if (propagateCancellationException) throw CancellationException()
    }

    /** makes serviceCall and updates corresponding State.
     *
     *  parameter: [response] - takes serviceCall that returns Result<YourServiceCallResponseModel>.
     *  parameter: [stateKey] - takes index of corresponding ViewState from [states].
     *  @see states
     * */
    suspend inline fun <reified T : Any?> CoroutineScope.call(
        response: Result<T>, stateKey: String,
        onError: (Throwable?) -> Unit = {},
        onSuccess: (Result<T>) -> Unit = {},
    ) {
        if (isActive) {
            if (response.isSuccess) {
                onSuccess.invoke(response)
                response.getOrNull().saveIn(stateKey)
            } else {
                onError.invoke(response.exceptionOrNull())
                stateKey.setValue(null)
                response.exceptionOrNull()?.message?.let { emitEvent(UiEvent.Message(it)) }
            }
        }
    }

    fun <T> updateState(keyString: String, value: T) {
        stateList[keyString]?.postChange { copy(value) }
    }


    // ---------------------- String Extension - State Setters ----------------------

    /** set value to a state by calling this function on "StateKey" String */
    infix fun <T> String.setValue(value: T) {
        updateState(this, value)
    }

    /** set value to a state by calling invoke() operator on "StateKey" String */
    operator fun <T> String.invoke(value: T) {
        updateState(this, value)
    }


    // ---------------------- String Extension - State Getters ----------------------

    /** returns state data by stateKey */
    fun <T> String.typeOf() = stateList[this]?.takeAs<T>()

    /** returns state data by stateKey */
    fun <T> String.state() = stateList[this]?.takeAs<T>()

    /** returns state data by calling invoke() operator on a stateKey */
    operator fun <T> String.invoke() = stateList[this]?.takeAs<T>()

    /** gets State<String> by calling [!] or - not() operator on a stateKey if in context of [BaseState]
     * @return value or Blank string if value is null
     *
     * @sample !STATE_KEY_STRING
     * @exception DOES_NOT use with invoke() or any state getter*/
    operator fun String.not(): String = stateList[this@not]?.takeAs<String>() ?: ""

    /** gets state by calling on a stateKey in Composable functions if in context of [BaseState]
     * @return value or Blank string if value is null
     *
     * @exception DOES_NOT use with invoke() or any state getter*/
    fun String.stateOrBlank(): String = stateList[this@stateOrBlank]?.takeAs<String>() ?: ""


    // ----------------------Infix - State Setters ----------------------

    /** set value to a state with corresponding "stateKey" by calling this function on value itself */
    infix fun <T> T.saveIn(stateKey: String) {
        updateState(stateKey, this)
    }

    /** set value to a state with corresponding "stateKey" by calling this function on value itself */
    infix fun <T> T.saveInStateOf(stateKey: String) = this.saveIn(stateKey)


    // ---------------------- Emit UiEvents ----------------------

    /** launches a coroutine and emits Action */
    fun <T : UiEvent> T.emit() = viewModelScope.launch { uiEvent.emit(this@emit) }

    /** use inside coroutineScope to emit Action */
    suspend fun emitEvent(uiEvent: UiEvent) = this.uiEvent.emit(uiEvent)
}