package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.Purity
import xyz.attacktive.wallhavend.domain.model.WallhavenCategory
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.util.AppLogger

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>, private val logger: AppLogger) {
	private object Keys {
		val SEARCH_QUERY = stringPreferencesKey("search_query")
		val CATEGORIES = stringSetPreferencesKey("categories")
		val PURITY = stringSetPreferencesKey("purity")
		val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
		val UPDATE_INTERVAL_MINUTES = intPreferencesKey("update_interval_minutes")
		val WALLPAPER_TARGET = stringPreferencesKey("wallpaper_target")
		val WIFI_ONLY = booleanPreferencesKey("wifi_only")
		val POOL_SIZE = intPreferencesKey("pool_size")
		val API_KEY = stringPreferencesKey("api_key")
		val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
		val FILTER_COLOR = stringPreferencesKey("filter_color")
		val SORTING = stringPreferencesKey("sorting")
		val TOPLIST_RANGE = stringPreferencesKey("toplist_range")
		val LAST_UPDATED_MS = longPreferencesKey("last_updated_ms")
		val CURRENT_WALLPAPER_PATH = stringPreferencesKey("current_wallpaper_path")
		val PREVIOUS_WALLPAPER_PATH = stringPreferencesKey("previous_wallpaper_path")
	}

	val settings = dataStore.data
		.map { preferences -> AppSettings(
				searchQuery = preferences[Keys.SEARCH_QUERY] ?: "",
				categories = (preferences[Keys.CATEGORIES] ?: setOf("GENERAL"))
					.mapNotNull { runCatching { WallhavenCategory.valueOf(it) }.getOrNull() }
					.toSet()
					.ifEmpty { setOf(WallhavenCategory.GENERAL) },
				purity = (preferences[Keys.PURITY] ?: setOf("SFW"))
					.mapNotNull { runCatching { Purity.valueOf(it) }.getOrNull() }
					.toSet()
					.ifEmpty { setOf(Purity.SFW) },
				aspectRatio = preferences[Keys.ASPECT_RATIO] ?: "",
				updateIntervalMinutes = preferences[Keys.UPDATE_INTERVAL_MINUTES] ?: 60,
				wallpaperTarget = preferences[Keys.WALLPAPER_TARGET]
					?.let { runCatching { WallpaperTarget.valueOf(it) }.getOrNull() }
					?: WallpaperTarget.HOME,
				wifiOnly = preferences[Keys.WIFI_ONLY] ?: true,
				poolSize = preferences[Keys.POOL_SIZE] ?: 10,
				apiKey = preferences[Keys.API_KEY] ?: "",
				autoStartOnBoot = preferences[Keys.AUTO_START_ON_BOOT] ?: true,
				filterColor = preferences[Keys.FILTER_COLOR] ?: "",
				sorting = preferences[Keys.SORTING] ?: "random",
				toplistRange = preferences[Keys.TOPLIST_RANGE] ?: "1M"
			)
			.also { logger.debug(TAG, "read: ${it.redactedForLog()}") }
		}

	suspend fun save(settings: AppSettings) {
		logger.debug(TAG, "save: ${settings.redactedForLog()}")

		dataStore.edit { preferences ->
			preferences[Keys.SEARCH_QUERY] = settings.searchQuery
			preferences[Keys.CATEGORIES] = settings.categories
				.map { it.name }
				.toSet()

			preferences[Keys.PURITY] = settings.purity
				.map { it.name }
				.toSet()

			preferences[Keys.ASPECT_RATIO] = settings.aspectRatio
			preferences[Keys.UPDATE_INTERVAL_MINUTES] = settings.updateIntervalMinutes
			preferences[Keys.WALLPAPER_TARGET] = settings.wallpaperTarget.name
			preferences[Keys.WIFI_ONLY] = settings.wifiOnly
			preferences[Keys.POOL_SIZE] = settings.poolSize
			preferences[Keys.API_KEY] = settings.apiKey
			preferences[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
			preferences[Keys.FILTER_COLOR] = settings.filterColor
			preferences[Keys.SORTING] = settings.sorting
			preferences[Keys.TOPLIST_RANGE] = settings.toplistRange
		}

		logger.debug(TAG, "save() completed")
	}

	suspend fun loadServiceState() = dataStore.data.first()
		.run {
			Triple(get(Keys.LAST_UPDATED_MS),
				get(Keys.CURRENT_WALLPAPER_PATH),
				get(Keys.PREVIOUS_WALLPAPER_PATH)
			)
		}

	suspend fun saveServiceState(lastUpdatedMs: Long, currentPath: String?, previousPath: String?) {
		dataStore.edit { preferences ->
			preferences[Keys.LAST_UPDATED_MS] = lastUpdatedMs
			if (currentPath != null) {
				preferences[Keys.CURRENT_WALLPAPER_PATH] = currentPath
			} else {
				preferences.remove(Keys.CURRENT_WALLPAPER_PATH)
			}

			if (previousPath != null) {
				preferences[Keys.PREVIOUS_WALLPAPER_PATH] = previousPath
			} else {
				preferences.remove(Keys.PREVIOUS_WALLPAPER_PATH)
			}
		}
	}

	companion object {
		private const val TAG = "SettingsRepository"
	}
}

/** Renders settings for logging without exposing the API key. */
private fun AppSettings.redactedForLog(): AppSettings =
	if (apiKey.isEmpty()) {
		this
	} else {
		copy(apiKey = "***")
	}
