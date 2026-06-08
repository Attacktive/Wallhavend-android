package xyz.attacktive.wallhavend.domain.model.query

import androidx.annotation.StringRes
import xyz.attacktive.wallhavend.R

enum class ToplistRange(val apiValue: String, @get:StringRes val nameRes: Int) {
	ONE_DAY("1d", R.string.settings_toplist_range_1d),
	THREE_DAYS("3d", R.string.settings_toplist_range_3d),
	ONE_WEEK("1w", R.string.settings_toplist_range_1w),
	ONE_MONTH("1M", R.string.settings_toplist_range_1m),
	THREE_MONTHS("3M", R.string.settings_toplist_range_3m),
	SIX_MONTHS("6M", R.string.settings_toplist_range_6m),
	ONE_YEAR("1y", R.string.settings_toplist_range_1y);

	companion object {
		fun fromApiValue(value: String) = entries.firstOrNull { it.apiValue == value } ?: ONE_MONTH
	}
}
