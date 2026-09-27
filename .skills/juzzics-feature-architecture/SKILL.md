---
name: juzzics-feature-architecture
description: Enforces the feature module architecture, BaseViewModel pattern, custom State/Action extensions, and Koin DI setup used in the Juzzics app (as seen in features/lyrics). Use this skill whenever creating or refactoring a feature module in Juzzics.
license: MIT
metadata:
  author: Juzzics
  keywords:
  - juzzics
  - baseviewmodel
  - mvi
  - koin
  - compose
---

## Feature Package Structure

Every feature module in `com.example.juzzics.features.<feature_name>` MUST follow this strict layer separation:

```
com.example.juzzics.features.<feature_name>/
├── data/
│   ├── dto/             # Network/API DTO models
│   ├── repo/            # Repository implementation (e.g., <Feature>RepoImpl)
│   └── service/         # Retrofit API interface
├── domain/
│   ├── model/           # Clean Domain data models
│   ├── repo/            # Repository interface definition
│   └── usecase/         # Domain use cases extending BaseUseCase or raw suspending functions
├── ui/
│   ├── <Feature>Screen.kt
│   └── vm/
│       ├── <Feature>VM.kt
│       └── logics/      # Extracted VM extension logics (e.g., <LogicName>Logic.kt)
└── di/                  # Koin DI Modules (ServiceModule, RepoModule, UseCasesModule, ViewModelsModule)
```

---

## 1. ViewModel & BaseViewModel Conventions

All ViewModels MUST extend `BaseViewModel` and initialize state using string keys mapped to `State<T>()`.

### Key Rules:
1. **Companion Keys**: Define all state keys as `const val` inside the `companion object`.
2. **State Map**: Pass `mutableMapOf` with initial `State<T>()` or `State("initial")` to `BaseViewModel`.
3. **Actions**: Define screen actions as `Action` implementations (`data object` or `data class`) inside or alongside the ViewModel.
4. **`onAction` Handling**: Delegate complex operations to VM logic extension functions located in `ui/vm/logics/`.

### Canonical ViewModel (`FetchLyricsVM.kt`):
```kotlin
package com.example.juzzics.features.lyrics.ui.vm

import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseViewModel
import com.example.juzzics.common.base.viewModel.State
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.domain.usecase.FetchLyricsUseCase
import com.example.juzzics.features.lyrics.ui.vm.logics.fetchLyrics

class FetchLyricsVM(
    val fetchLyricsUseCase: FetchLyricsUseCase
) : BaseViewModel(
    mutableMapOf(
        LYRICS to State<LyricsDomain>(),
        ARTIST to State("nightwish"),
        TITLE to State("ghost love score")
    )
) {
    companion object {
        const val LYRICS = "Lyrics"
        const val ARTIST = "Artist"
        const val TITLE = "Title"
    }

    override fun onAction(action: Action) {
        when (action) {
            is FetchLyricsAction -> fetchLyrics()
            is UpdateArtistAction -> ARTIST(action.value)
            is UpdateTitleAction -> TITLE(action.value)
        }
    }

    data object FetchLyricsAction : Action
    data class UpdateArtistAction(val value: String) : Action
    data class UpdateTitleAction(val value: String) : Action
}
```

---

## 2. VM Logic Extensions (`ui/vm/logics/`)

Separate execution logic from the ViewModel file by creating Kotlin extension functions on the ViewModel.

### Key Rules:
- Place functions in `com.example.juzzics.features.<feature>.ui.vm.logics`.
- Use `launch(emitErrorMsgAction = true)` helper from `BaseViewModel`.
- Use `call(useCaseResult, STATE_KEY)` to execute calls and automatically update the corresponding `State`.
- Use `!STATE_KEY` (not operator) to retrieve string state values or fallback to `""`.

### Canonical VM Logic (`FetchLyricsLogic.kt`):
```kotlin
package com.example.juzzics.features.lyrics.ui.vm.logics

import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM.Companion.ARTIST
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM.Companion.LYRICS
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM.Companion.TITLE

fun FetchLyricsVM.fetchLyrics() =
    launch(emitErrorMsgAction = true) {
        call(fetchLyricsUseCase(!ARTIST, !TITLE), LYRICS)
    }
```

---

## 3. UI / Compose Screen Conventions

Screens MUST use `with2` extension to bind both `BaseState` and the ViewModel's `Companion` object into scope.

### Key Extensions Reference:
- `with2(first = states, second = <Feature>VM)`: Grants Composable context to `BaseState` and the VM's companion keys.
- `!ARTIST`: Gets String state value directly using the `not()` operator on companion key string.
- `LYRICS<LyricsDomain>()`: Gets generic typed model from `BaseState` using `invoke()`.
- `uiEvent.BaseHandler`: Automatically handles loading overlays and toast error messages.

### Canonical Screen (`FetchLyricsScreen.kt`):
```kotlin
package com.example.juzzics.features.lyrics.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.juzzics.common.base.BaseHandler
import com.example.juzzics.common.base.extensions.with2
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.UiEvent
import com.example.juzzics.common.base.viewModel.invoke
import com.example.juzzics.common.base.viewModel.not
import com.example.juzzics.features.lyrics.domain.model.LyricsDomain
import com.example.juzzics.features.lyrics.ui.vm.FetchLyricsVM
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun FetchLyricsScreen(
    states: BaseState,
    uiEvent: SharedFlow<UiEvent>,
    onAction: (Action) -> Unit
) {
    with2(first = states, second = FetchLyricsVM) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            uiEvent.BaseHandler(
                content = {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextField(
                            value = !ARTIST,
                            onValueChange = { onAction(FetchLyricsVM.UpdateArtistAction(it)) }
                        )
                        Button(onClick = { onAction(FetchLyricsVM.FetchLyricsAction) }) {
                            Text("Search")
                        }
                        Text(text = LYRICS<LyricsDomain>()?.lyrics.orEmpty())
                    }
                }
            )
        }
    }
}
```

---

## 4. Koin Dependency Injection Setup

Each feature MUST define clean, separate Koin modules under `di/`:

1. **`ServiceModule.kt`**: Retrofit service definition.
2. **`RepoModule.kt`**: Repository binding (`single { RepoImpl(get()) } bind Repo::class`).
3. **`UseCasesModule.kt`**: Factory definitions (`factory { MyUseCase(get()) }`).
4. **`ViewModelsModule.kt`**: ViewModel definition (`viewModel { MyVM(get()) }`).

---

## Checklist

When implementing or reviewing a feature module in Juzzics, ensure:
- [ ] Folder hierarchy follows `data`, `domain`, `ui`, `di`.
- [ ] ViewModel extends `BaseViewModel` with a `mutableMapOf` of initial `State`s.
- [ ] State keys are defined in the `companion object`.
- [ ] ViewModel logic is extracted into `ui/vm/logics/<Name>Logic.kt`.
- [ ] Composable UI uses `with2(first = states, second = <Feature>VM)` to access state keys.
- [ ] `!KEY` is used for String state reading and `KEY<Type>()` for object state reading.
- [ ] `uiEvent.BaseHandler` handles side effects (loading/messages).
- [ ] Koin DI modules are created under `di/`.
