package com.xipian.chatxp_android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val ChatPillShape = RoundedCornerShape(percent = 50)
val ChatDialogShape = RoundedCornerShape(24.dp)

val ChatShapes = Shapes(
    extraSmall = ChatPillShape,
    small = ChatPillShape,
    medium = ChatPillShape,
    large = ChatPillShape,
    extraLarge = ChatPillShape
)

object ChatCorner {
    val Pill: Shape = ChatPillShape
    val Circle: Shape = ChatPillShape
    val Dialog: Shape = ChatDialogShape
    val Drawer: Shape = RectangleShape
}
