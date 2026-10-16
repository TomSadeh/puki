package com.olamkatan.ui.animals.components

import com.olamkatan.ui.components.hintGlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import com.olamkatan.ui.components.idleBob
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
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.components.DragTarget

@Composable
fun FoodItem(
    food: FoodDef,
    onDropped: (dropTargetId: String?) -> Unit,
    modifier: Modifier = Modifier,
    hint: Boolean = false,  // wiggle and glow: this is the one
    tapToZone: String? = null  // a tap sends it there (the hungry animal)
) {
    val language = LocalLanguage.current
    val voicePlayer = LocalVoicePlayer.current
    DragTarget(
        itemId = food.id,
        onDragEnd = onDropped,
        onDragStart = { voicePlayer.play("voice_food_${food.id}", language) },
        tapToZone = tapToZone,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            if (food.drawableRes != 0) {
                // On a plate, so it reads as something to pick up
                Box(
                    modifier = Modifier
                        .hintGlow(hint)
                        .idleBob(food.id.length)
                        .size(165.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.9f))
                        .border(6.dp, Color(0xFFFFE0B2), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = food.drawableRes),
                        contentDescription = food.name.en,
                        modifier = Modifier.size(135.dp)
                    )
                }
            } else {
                Text(text = food.emoji, fontSize = 56.sp)
            }
            Text(
                text = food.name.resolve(language),
                fontSize = 16.sp,
                color = Color(0xFF33691E),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
