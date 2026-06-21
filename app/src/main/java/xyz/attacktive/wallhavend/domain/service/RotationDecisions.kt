package xyz.attacktive.wallhavend.domain.service

import xyz.attacktive.wallhavend.domain.model.RotationMode

/**
 * Whether the next rotation tick should fetch a fresh wallpaper rather than cycle the pool.
 * Downloading always requires connectivity; a forced request (the "Download now" button) overrides
 * the per-mode network rules but still cannot beat being offline.
 */
fun shouldDownload(mode: RotationMode, online: Boolean, onWifi: Boolean, forceDownload: Boolean): Boolean {
	if (!online) {
		return false
	}

	if (forceDownload) {
		return true
	}

	return when (mode) {
		RotationMode.FRESH_ANY -> true
		RotationMode.FRESH_WIFI -> onWifi
		RotationMode.PINNED_ONLY -> false
	}
}

/** The next path in a wrap-around ring walk, or null when the pool is empty. */
fun nextInCycle(pool: List<String>, current: String?): String? {
	val currentIndex = current?.let { pool.indexOf(it) } ?: -1

	return pool.getOrNull(currentIndex + 1) ?: pool.firstOrNull()
}
