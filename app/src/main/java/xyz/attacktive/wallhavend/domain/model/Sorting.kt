package xyz.attacktive.wallhavend.domain.model

import androidx.annotation.StringRes
import xyz.attacktive.wallhavend.R

enum class Sorting(val apiValue: String, @get:StringRes val nameRes: Int) {
	RANDOM("random", R.string.settings_sorting_random),
	DATE_ADDED("date_added", R.string.settings_sorting_date_added),
	VIEWS("views", R.string.settings_sorting_views),
	FAVORITES("favorites", R.string.settings_sorting_favorites),
	TOPLIST("toplist", R.string.settings_sorting_toplist);

	companion object {
		fun fromApiValue(value: String) = entries.firstOrNull { it.apiValue == value } ?: RANDOM
	}
}
