package com.example.juzzics.common.base.viewModel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

/** All states of a ViewModel, by key. Passed from the ViewModel to its Screen. */
typealias BaseState = Map<StateKey<*>, MutableState<*>>

/** reads a state's value (outside of a [BaseState] context) */
@Suppress("UNCHECKED_CAST")
fun <T> BaseState.valueOf(key: StateKey<T>): T =
    (this[key] ?: error("State '$key' isn't registered in this ViewModel")).value as T


// ---------------------- Composable State Getters (in context of BaseState) ----------------------

/** `KEY()` - reads the state; the Composable recomposes when it changes */
context(baseState: BaseState)
@Composable
operator fun <T> StateKey<T>.invoke(): T = baseState.valueOf(this)

/** `!STRING_KEY` - reads a String state */
context(baseState: BaseState)
@Composable
operator fun StateKey<String>.not(): String = baseState.valueOf(this)


// ---------------------- Non-Composable State Getter (in context of BaseState) ----------------------

/** reads the state in normal functions/lambdas (e.g. click handlers) */
context(baseState: BaseState)
fun <T> StateKey<T>.stateValue(): T = baseState.valueOf(this)
