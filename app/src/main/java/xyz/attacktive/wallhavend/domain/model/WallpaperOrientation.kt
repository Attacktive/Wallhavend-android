package xyz.attacktive.wallhavend.domain.model

enum class WallpaperOrientation { AUTOMATIC, PORTRAIT, LANDSCAPE }

fun ScreenInfo.forWallpaperOrientation(orientation: WallpaperOrientation): ScreenInfo {
	val shouldSwap = when (orientation) {
		WallpaperOrientation.AUTOMATIC -> false
		WallpaperOrientation.PORTRAIT -> width > height
		WallpaperOrientation.LANDSCAPE -> width < height
	}

	if (!shouldSwap) {
		return this
	}

	val swappedWidth = height
	val swappedHeight = width

	return ScreenInfo(closestAspectRatio(swappedWidth, swappedHeight), swappedWidth, swappedHeight)
}
