package xyz.attacktive.wallhavend.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WallpaperMetadata(
	val sourceUrl: String? = null,
	val thumbnailUrl: String? = null,
	val upstreamSource: String? = null,
	val licenseType: String? = null,
	val photographerName: String? = null,
	val photographerUsername: String? = null,
	val photographerUrl: String? = null,
	val attributionText: String? = null,
	val attributionHtml: String? = null
) {
	val displayCredit: String? get() {
		if (!attributionText.isNullOrBlank()) {
			return attributionText
		}

		val name = photographerName?.takeIf { it.isNotBlank() }
			?: photographerUsername?.takeIf { it.isNotBlank() }
		val source = upstreamSource?.takeIf { it.isNotBlank() }

		return when {
			name != null && source != null -> "Photo by $name on $source"
			name != null -> "Photo by $name"
			source != null -> "Photo on $source"
			else -> null
		}
	}

	val galleryDescription: String? get() {
		val parts = mutableListOf<String>()
		displayCredit?.let { parts += it }
		sourceUrl?.takeIf { it.isNotBlank() }?.let { parts += it }
		licenseType?.takeIf { it.isNotBlank() }?.let { parts += "License: $it" }

		return parts.joinToString("\n").takeIf { it.isNotBlank() }
	}
}
