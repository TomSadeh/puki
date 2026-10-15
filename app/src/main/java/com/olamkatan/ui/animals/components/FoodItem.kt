package com.olamkatan.ui.animals.components

import com.olamkatan.ui.common.LocalVoicePlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.olamkatan.data.content.FoodDef
import com.olamkatan.theme.CloudWhite
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.components.DragTarget

@Composable
fun FoodItem(
    food: FoodDef,
    onDropped: (dropTargetId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    DragTarget(
        itemId = food.id,
        onDragEnd = onDropped,
        onDragStart = { voicePlayer.play("voice_food_${food.id}", language) },
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            if (food.drawableRes != 0) {
                Image(
                    painter = painterResource(id = food.drawableRes),
                    contentDescription = food.name.en,
                    modifier = Modifier.size(140.dp)
                )
            } else {
                Text(text = food.emoji, fontSize = 56.sp)
            }
            Text(
                text = food.name.resolve(language),
                fontSize = 14.sp,
                color = CloudWhite,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
