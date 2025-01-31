package com.example.juzzics.features.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ExperimentalMotionApi
import androidx.constraintlayout.compose.MotionLayout
import androidx.constraintlayout.compose.MotionScene
import androidx.constraintlayout.compose.layoutId
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import com.example.juzzics.common.base.viewModel.Action
import com.example.juzzics.common.base.viewModel.BaseState
import com.example.juzzics.common.base.viewModel.UiEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

@Preview
@Composable
fun homePreview() {
    HomeScreen(states = mapOf(), uiEvent = MutableSharedFlow(), onAction = {})
}

@OptIn(ExperimentalMotionApi::class, ExperimentalWearMaterialApi::class)
@Composable
fun HomeScreen(
    states: BaseState,
    uiEvent: SharedFlow<UiEvent>,
    onAction: (Action) -> Unit
) {
//    with2(first = states, second = HomeVM) {
//        uiEvent.BaseHandler {
//            val context = LocalContext.current
//            uiEvent.listen {
//                when (it) {
//                    is HomeVM.ToastMsg -> {
//                        Toast.makeText(context, it.msg, Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//            Surface {
//                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
//                    Column {
//                        Text(text = !TEST, modifier = Modifier.clickable {
//                            onAction(HomeVM.CallAction)
//                        })
//                        Spacer(modifier = Modifier.height(30.dp))
//                        TextField(value = TEXT_FIELD_VALUE.stateOrBlank(), onValueChange = {
//                            onAction(HomeVM.UpdateTextFieldValueAction(it))
//                        })
//                    }
//                }
//            }
//        }
//    }

    /**this works with clicks*/
    var sceneName by remember("sceneName") { mutableStateOf("0") }
    MotionLayout(
        motionScene = MotionScene {
            val tFirst = createRefFor("text_first")
            val tSecond = createRefFor("text_second")
            val tThird = createRefFor("text_Third")
            val firstSet = constraintSet("0") {
                constrain(tFirst) {
                    bottom.linkTo(parent.bottom)
                    centerHorizontallyTo(parent)
                }
                constrain(tThird) {
                    top.linkTo(parent.bottom)
                    centerHorizontallyTo(parent)
                }
                constrain(tSecond) {
                    top.linkTo(parent.bottom)
                    centerHorizontallyTo(parent)
                }
            }
            val secondSet = constraintSet("1") {
                constrain(tFirst) {
                    top.linkTo(parent.top)
                    centerHorizontallyTo(parent)
                }
                constrain(tSecond) {
                    bottom.linkTo(parent.bottom)
                    centerHorizontallyTo(parent)
                }
                constrain(tThird) {
                    top.linkTo(parent.bottom)
                    centerHorizontallyTo(parent)
                }
            }
            constraintSet("2") {
                constrain(tFirst) {
                    top.linkTo(parent.top)
                    centerHorizontallyTo(parent)
                }
                constrain(tSecond) {
                    top.linkTo(parent.top)
                    centerHorizontallyTo(parent)
                }
                constrain(tThird) {
                    bottom.linkTo(parent.bottom)
                    centerHorizontallyTo(parent)
                }
            }
            defaultTransition(firstSet, secondSet)
        },
        constraintSetName = sceneName,
        animationSpec = tween(1200),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(top = 40.dp)
    ) {
        Text(
            "Text First",
            Modifier
                .layoutId("text_first")
                .clip(RoundedCornerShape(200.dp))
                .background(Color.Blue)
                .padding(16.dp)
                .clickable { sceneName = if (sceneName == "1") "0" else "1" }
        )
        Text(
            "Text Second",
            Modifier
                .layoutId("text_second")
                .clip(RoundedCornerShape(200.dp))
                .background(Color.Blue)
                .padding(16.dp)
                .clickable { sceneName = if (sceneName == "2") "1" else "2" }
        )
        Text(
            "Text Third",
            Modifier
                .layoutId("text_Third")
                .clip(RoundedCornerShape(200.dp))
                .background(Color.Blue)
                .padding(16.dp)
        )
    }

    /** trying swipes */

}