package xyz.attacktive.wallhavend.domain.model

/**
 * Where a wallpaper came from.
 * [key] is the persisted discriminator, so renaming one orphans every id already stored on the device.
 */
enum class WallpaperSource(val key: String) {
	WALLHAVEN("wallhaven");

	companion object {
		fun fromKey(key: String) = entries.firstOrNull { it.key == key }
	}
}
