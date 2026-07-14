package com.xipian.chatxp_android.ui.token

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val FastDurationMillis = 150
const val BaseDurationMillis = 220
const val TypingPulseDurationMillis = 1000
const val ToastVisibleDurationMillis = 1500

val StandardEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val FastTweenSpec: TweenSpec<Float> = tween(
    durationMillis = FastDurationMillis,
    easing = StandardEasing
)
val BaseTweenSpec: TweenSpec<Float> = tween(
    durationMillis = BaseDurationMillis,
    easing = StandardEasing
)

const val ButtonPressedScale = 0.96f
const val ButtonNormalScale = 1.0f
const val DisabledAlpha = 0.34f

const val DrawerEnterDurationMillis = 220
const val DrawerExitDurationMillis = 220
const val DrawerHiddenOffsetFraction = -1.04f
const val DrawerVisibleOffsetFraction = 0f
const val DrawerBackdropAlpha = 0.28f

const val ToastEnterDurationMillis = 220
const val ToastExitDurationMillis = 220
val ToastInitialYOffset: Dp = 18.dp
val ToastVisibleYOffset: Dp = 0.dp
const val ToastHiddenAlpha = 0f
const val ToastVisibleAlpha = 1f

const val MenuFadeDurationMillis = 150
const val DialogFadeDurationMillis = 220
const val DialogScaleInitial = 0.98f
const val DialogScaleVisible = 1.0f

const val MessageToolsFadeDurationMillis = 150
const val MessageToolsHiddenAlpha = 0f
const val MessageToolsVisibleAlpha = 1f

val TypingDotSize: Dp = 5.dp
val TypingDotSpacing: Dp = 4.dp
val TypingDotLiftDistance: Dp = 3.dp
const val TypingDotMinAlpha = 0.35f
const val TypingDotMaxAlpha = 1.0f
const val TypingDotDelayStepMillis = 120
