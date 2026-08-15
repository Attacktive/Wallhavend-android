package xyz.attacktive.wallhavend.domain.model

import androidx.annotation.StringRes
import xyz.attacktive.wallhavend.R

/**
 * Where a wallpaper came from.
 * [key] is the persisted discriminator, so renaming one orphans every id already stored on the device.
 * [attributionRes] and [homeUrl] are what the source asks to be credited with — Openverse's terms require saying the app is not endorsed by them, so the credit is an obligation rather than a courtesy.
 */
enum class WallpaperSource(
	val key: String,
	@get:StringRes val nameRes: Int,
	@get:StringRes val attributionRes: Int,
	val homeUrl: String
) {
	WALLHAVEN("wallhaven", R.string.source_wallhaven, R.string.settings_powered_by_wallhaven, "https://wallhaven.cc"),
	OPENVERSE("openverse", R.string.source_openverse, R.string.settings_powered_by_openverse, "https://openverse.org");

	companion object {
		fun fromKey(key: String) = entries.firstOrNull { it.key == key }
	}
}
