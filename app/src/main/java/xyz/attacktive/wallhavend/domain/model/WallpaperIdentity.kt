package xyz.attacktive.wallhavend.domain.model

/**
 * A wallpaper, qualified by the source that served it.
 * [qualified] is the single encoded form: it names the file on disk and keys the pinned and blocked sets, so ids from two sources can never collide.
 */
data class WallpaperIdentity(val source: WallpaperSource, val id: String) {
	val qualified get() = "${source.key}$SEPARATOR$id"

	/** The origin site's page for this wallpaper. */
	val pageUrl get() = when (source) {
		WallpaperSource.WALLHAVEN -> "https://wallhaven.cc/w/$id"
	}

	/** A remote thumbnail, for wallpapers with no local file left to show — a blocked one, say. */
	val thumbnailUrl get() = when (source) {
		WallpaperSource.WALLHAVEN -> "https://th.wallhaven.cc/lg/${id.take(2)}/$id.jpg"
	}

	/**
	 * Every form this wallpaper can appear as in a persisted id set.
	 * Installs predating source-qualified ids stored bare Wallhaven ids, so lookups have to accept that form and removals have to clear it.
	 */
	val persistedForms get() = when (source) {
		WallpaperSource.WALLHAVEN -> setOf(qualified, id)
	}

	fun toFileName(extension: String) = "$qualified.$extension"

	/** Whether [rawIds] — qualified or legacy bare — holds this wallpaper. */
	fun matches(rawIds: Set<String>) = persistedForms.any { it in rawIds }

	companion object {
		private const val SEPARATOR = '_'

		/**
		 * Accepts the qualified form and the bare Wallhaven ids that older installs persisted.
		 * That leniency *is* the migration: existing files, pins and blocks keep working with no DataStore migration and nothing renamed on disk, which would have invalidated the persisted current-wallpaper path.
		 */
		fun parse(raw: String): WallpaperIdentity {
			val separatorIndex = raw.indexOf(SEPARATOR)
			if (separatorIndex > 0) {
				val source = WallpaperSource.fromKey(raw.take(separatorIndex))
				val id = raw.substring(separatorIndex + 1)

				if (source != null && id.isNotEmpty()) {
					return WallpaperIdentity(source, id)
				}
			}

			return WallpaperIdentity(WallpaperSource.WALLHAVEN, raw)
		}
	}
}
