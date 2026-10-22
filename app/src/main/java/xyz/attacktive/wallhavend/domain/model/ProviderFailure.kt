package xyz.attacktive.wallhavend.domain.model

import java.security.MessageDigest

/** Runtime diagnostics belong to the attempted provider and credential, not the overall rotation result. */
class ProviderFailure(val source: WallpaperSource, val error: AppError, settings: AppSettings) {
	private val credentialFingerprint = fingerprint(settings)

	fun isCurrent(settings: AppSettings) = source in settings.configuredSources && credentialFingerprint.contentEquals(fingerprint(settings))

	private fun fingerprint(settings: AppSettings): ByteArray {
		val key = when (source) {
			WallpaperSource.WALLHAVEN -> settings.apiKey
			WallpaperSource.PEXAFY -> settings.pexafyApiKey
			WallpaperSource.OPENVERSE -> ""
		}

		return MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
	}
}
