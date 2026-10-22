package xyz.attacktive.wallhavend.domain.model

/** A restored or legacy Pexafy selection without its separately stored key is pending setup, not active. */
val AppSettings.configuredSources: Set<WallpaperSource>
	get() = enabledSources.filterTo(linkedSetOf()) { it != WallpaperSource.PEXAFY || pexafyApiKey.isNotBlank() }

val AppSettings.requiresPexafySetup
	get() = WallpaperSource.PEXAFY in enabledSources && pexafyApiKey.isBlank()

/** Saving a draft is explicit; removing the last source requires an explicit replacement first. */
fun AppSettings.withPexafyKey(key: String, enable: Boolean): AppSettings {
	val trimmedKey = key.trim()
	require(!enable || trimmedKey.isNotBlank()) { "Pexafy requires an API key" }

	val sources = when {
		trimmedKey.isBlank() -> enabledSources - WallpaperSource.PEXAFY
		enable -> enabledSources + WallpaperSource.PEXAFY
		else -> enabledSources
	}

	require(sources.isNotEmpty()) { "Select another source before removing Pexafy" }

	return copy(pexafyApiKey = trimmedKey, enabledSources = sources)
}

val AppError.providerSource: WallpaperSource?
	get() = when (this) {
		is AppError.ApiError -> source
		is AppError.NetworkError -> source
		is AppError.MissingApiKey -> source
		is AppError.InvalidApiKey -> source
		is AppError.QuotaExceeded -> source
		is AppError.RateLimited -> source
		else -> null
	}
