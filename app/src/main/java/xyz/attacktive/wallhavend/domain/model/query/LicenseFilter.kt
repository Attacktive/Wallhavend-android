package xyz.attacktive.wallhavend.domain.model.query

import androidx.annotation.StringRes
import xyz.attacktive.wallhavend.R

/**
 * How permissive an Openverse result's licence may be, narrowest first.
 * Each tier names its licences outright instead of leaning on Openverse's `license_type` grouping, so the tiers stay strictly nested — widening the filter can only ever add licences, never swap one out.
 */
enum class LicenseFilter(val apiValue: String, @get:StringRes val nameRes: Int) {
	PUBLIC_DOMAIN("cc0,pdm", R.string.license_public_domain),
	PERMISSIVE("cc0,pdm,by", R.string.license_permissive),
	ANY_COMMERCIAL("cc0,pdm,by,by-sa,by-nd", R.string.license_any_commercial)
}
