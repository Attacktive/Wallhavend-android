package xyz.attacktive.wallhavend.domain.model

import android.view.Surface

data class ScreenInfo(val aspectRatio: String, val width: Int, val height: Int)

/**
 * Android reports the display's size in its current rotation, but wallpapers are for the natural orientation, so a measurement taken a quarter-turn sideways is swapped back rather than trusted.
 * Deliberately not min/max: a landscape-natural display reports landscape at [Surface.ROTATION_0], and wide is the right thing to ask for there.
 */
fun naturalDimensions(width: Int, height: Int, rotation: Int) = if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
	height to width
} else {
	width to height
}
