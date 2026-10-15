package com.olamkatan.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.olamkatan.R

/** The back arrow used on every screen; points left in English and right in Hebrew. */
@Composable
fun BackArrowIcon(modifier: Modifier = Modifier) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Image(
        painter = painterResource(R.drawable.ic_ui_back),
        contentDescription = null,
        modifier = modifier
            .size(28.dp)
            .graphicsLayer(scaleX = if (isRtl) -1f else 1f)
    )
}
