package com.example.juzzics.common.uiComponents

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Round icon button of the player; [text] is read by screen readers. */
@Composable
fun SimpleFAB(modifier: Modifier, text: String, image: ImageVector, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape
    ) {
        Icon(image, contentDescription = text)
    }
}
