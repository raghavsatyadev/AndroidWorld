package io.github.raghavsatyadev.androidworld.desktop.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/*
 * The app's transitions. Every duration and curve comes from the theme's M3 Expressive
 * [MotionScheme] (spatial springs for movement, effects springs for fades), so screens never
 * hard-code timings and all motion changes together when the scheme does.
 *
 * - fadeThrough: switching between peers (rail areas, section tabs) — no spatial relation.
 * - sharedAxisX: moving forward/back along a hierarchy (list → detail, step → step).
 * - reveal / conceal: a block appearing or disappearing inside a screen (banners, editors).
 */

private const val ENTER_SCALE = 0.94f
private const val AXIS_FRACTION = 8 // slide by 1/8 of the width

/** Peer-to-peer switch: the old content fades out quickly, the new one fades and settles in. */
@Composable
@ReadOnlyComposable
fun <S> fadeThrough(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val motion = MaterialTheme.motionScheme
    return { fadeThroughTransform(motion) }
}

private fun fadeThroughTransform(motion: MotionScheme): ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = ENTER_SCALE),
        initialContentExit = fadeOut(motion.fastEffectsSpec()),
        sizeTransform = SizeTransform(clip = false),
    )

/** Hierarchy move: forward slides in from the end, back from the start. [forward] decides per change. */
@Composable
@ReadOnlyComposable
fun <S> sharedAxisX(forward: (initial: S, target: S) -> Boolean): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val motion = MaterialTheme.motionScheme
    return {
        val sign = if (forward(initialState, targetState)) 1 else -1
        (slideInHorizontally(motion.defaultSpatialSpec()) { sign * it / AXIS_FRACTION } + fadeIn(motion.defaultEffectsSpec()))
            .togetherWith(slideOutHorizontally(motion.fastSpatialSpec()) { -sign * it / AXIS_FRACTION } + fadeOut(motion.fastEffectsSpec()))
    }
}

/** A block entering inside a screen: grows open and fades in. */
@Composable
@ReadOnlyComposable
fun reveal(): EnterTransition {
    val motion = MaterialTheme.motionScheme
    return expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
}

/** The reverse of [reveal]. */
@Composable
@ReadOnlyComposable
fun conceal(): ExitTransition {
    val motion = MaterialTheme.motionScheme
    return shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
}
