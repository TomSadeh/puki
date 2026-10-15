package com.olamkatan.ui.myday

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.olamkatan.R
import com.olamkatan.data.models.BilingualText
import com.olamkatan.navigation.Routes
import com.olamkatan.theme.EarthBrown
import com.olamkatan.ui.common.LocalLanguage
import com.olamkatan.ui.components.BackArrowIcon

data class RoomDef(
    val route: String,
    val name: BilingualText,
    @DrawableRes val icon: Int,
    val color: Color
)

private val rooms = listOf(
    RoomDef(Routes.MY_DAY_BATH, BilingualText("אמבטיה", "Bath Time"), R.drawable.ic_myday_bathtub, Color(0xFF81D4FA)),
    RoomDef(Routes.MY_DAY_BRUSH, BilingualText("צחצוח שיניים", "Brushy Brushy"), R.drawable.ic_myday_toothbrush, Color(0xFF80CBC4)),
    RoomDef(Routes.MY_DAY_TIDY, BilingualText("סידור חדר", "Tidy Up"), R.drawable.ic_tidy_teddy, Color(0xFFFFCC80)),
    RoomDef(Routes.MY_DAY_DRESS, BilingualText("להתלבש", "Getting Dressed"), R.drawable.ic_clothing_shirt, Color(0xFFEF9A9A)),
    RoomDef(Routes.MY_DAY_TABLE, BilingualText("ארוחה", "Table Time"), R.drawable.ic_table_plate, Color(0xFFA5D6A7)),
    RoomDef(Routes.MY_DAY_BEDTIME, BilingualText("שינה", "Bedtime"), R.drawable.ic_bedtime_moon, Color(0xFF7E57C2))
)

// Upstairs are the bedroom rooms, downstairs the bathroom and kitchen
private val upstairs = listOf(rooms[2], rooms[3], rooms[5])
private val downstairs = listOf(rooms[0], rooms[1], rooms[4])

// The house occupies this part of the screen (fractions of width / height)
private const val HOUSE_LEFT = 0.12f
private const val HOUSE_RIGHT = 0.88f
private const val WALL_TOP = 0.30f
private const val WALL_BOTTOM = 0.94f

@Composable
fun MyDayScreen(
    onRoomClick: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF81D4FA), Color(0xFFE1F5FE))))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) { drawHouse() }

        // Rooms, laid out inside the house walls as a 2 x 3 cutaway
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val left = maxWidth * HOUSE_LEFT
            val top = maxHeight * WALL_TOP
            val width = maxWidth * (HOUSE_RIGHT - HOUSE_LEFT)
            val height = maxHeight * (WALL_BOTTOM - WALL_TOP)
            Column(
                modifier = Modifier
                    .offset(left, top)
                    .size(width, height)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                for (floor in listOf(upstairs, downstairs)) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        floor.forEach { room ->
                            RoomButton(
                                room = room,
                                onClick = { onRoomClick(room.route) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(64.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(EarthBrown.copy(alpha = 0.85f))
                .clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            BackArrowIcon()
        }
    }
}

/** Grass, a roof with chimney, and the brick-colored walls the rooms sit in. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHouse() {
    val w = size.width
    val h = size.height
    val l = w * HOUSE_LEFT
    val r = w * HOUSE_RIGHT
    val top = h * WALL_TOP
    val bottom = h * WALL_BOTTOM

    // Sun and grass
    drawCircle(Color(0x55FFE082), h * 0.11f, Offset(w * 0.07f, h * 0.12f))
    drawCircle(Color(0xFFFFD54F), h * 0.07f, Offset(w * 0.07f, h * 0.12f))
    drawRect(Color(0xFF81C784), Offset(0f, h * 0.88f), Size(w, h * 0.12f))
    drawRect(Color(0xFF66BB6A), Offset(0f, h * 0.94f), Size(w, h * 0.06f))

    // Chimney behind the roof
    drawRect(Color(0xFF8D6E63), Offset(r - w * 0.14f, h * 0.06f), Size(w * 0.05f, h * 0.18f))
    drawRect(Color(0xFF6D4C41), Offset(r - w * 0.145f, h * 0.05f), Size(w * 0.06f, h * 0.025f))

    // Roof
    val roof = Path().apply {
        moveTo(l - w * 0.04f, top + h * 0.01f)
        lineTo(w / 2, h * 0.04f)
        lineTo(r + w * 0.04f, top + h * 0.01f)
        close()
    }
    drawPath(roof, Color(0xFFE53935))
    val roofShade = Path().apply {
        moveTo(l - w * 0.04f, top + h * 0.01f)
        lineTo(r + w * 0.04f, top + h * 0.01f)
        lineTo(r + w * 0.02f, top - h * 0.015f)
        lineTo(l - w * 0.02f, top - h * 0.015f)
        close()
    }
    drawPath(roofShade, Color(0xFFC62828))
    // Round attic window
    drawCircle(Color(0xFFFFF8E1), h * 0.05f, Offset(w / 2, h * 0.18f))
    drawCircle(Color(0xFF90CAF9), h * 0.038f, Offset(w / 2, h * 0.18f))

    // Walls with a floor between the two stories
    drawRoundRect(
        Color(0xFFFFE0B2),
        topLeft = Offset(l, top),
        size = Size(r - l, bottom - top),
        cornerRadius = CornerRadius(12f)
    )
    drawRect(Color(0xFFBC8F5F), Offset(l, (top + bottom) / 2 - h * 0.008f), Size(r - l, h * 0.016f))
}

@Composable
private fun RoomButton(room: RoomDef, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val language = LocalLanguage.current
    val transition = rememberInfiniteTransition(label = "room_${room.route}")
    val bounce by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1600 + (room.route.hashCode() and 0x1FF)), RepeatMode.Reverse),
        label = "room_bounce"
    )
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(room.color.copy(alpha = 0.75f), room.color)))
            .border(BorderStroke(6.dp, Color.White), shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(room.icon),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 30.dp)
                .size(120.dp)
                .graphicsLayer { scaleX = bounce; scaleY = bounce }
        )
        Text(
            text = room.name.resolve(language),
            style = MaterialTheme.typography.labelLarge.copy(shadow = null),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5D4037),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF8E1))
                .border(BorderStroke(2.dp, Color(0xFFBC8F5F)), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}
