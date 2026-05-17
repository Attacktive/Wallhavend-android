package xyz.attacktive.wallhavend.domain.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.Purity
import xyz.attacktive.wallhavend.domain.model.WallhavenCategory
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
	private object Keys {
		val SEARCH_QUERY = stringPreferencesKey("search_query")
		val CATEGORIES = stringSetPreferencesKey("categories")
		val PURITY = stringSetPreferencesKey("purity")
		val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
		val UPDATE_INTERVAL_MINUTES = intPreferencesKey("update_interval_minutes")
		val WALLPAPER_TARGET = stringPreferencesKey("wallpaper_target")
		val UNMETERED_ONLY = booleanPreferencesKey("unmetered_only")
		val POOL_SIZE = intPreferencesKey("pool_size")
		val API_KEY = stringPreferencesKey("api_key")
		val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
		val LAST_UPDATED_MS = longPreferencesKey("last_updated_ms")
		val CURRENT_WALLPAPER_PATH = stringPreferencesKey("current_wallpaper_path")
		val PREVIOUS_WALLPAPER_PATH = stringPreferencesKey("previous_wallpaper_path")
	}

	val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
		AppSettings(
			searchQuery = prefs[Keys.SEARCH_QUERY] ?: "",
			categories = (prefs[Keys.CATEGORIES] ?: setOf("GENERAL"))
				.mapNotNull { runCatching { WallhavenCategory.valueOf(it) }.getOrNull() }
				.toSet()
				.ifEmpty { setOf(WallhavenCategory.GENERAL) },
			purity = (prefs[Keys.PURITY] ?: setOf("SFW"))
				.mapNotNull { runCatching { Purity.valueOf(it) }.getOrNull() }
				.toSet()
				.ifEmpty { setOf(Purity.SFW) },
			aspectRatio = prefs[Keys.ASPECT_RATIO] ?: "",
			updateIntervalMinutes = prefs[Keys.UPDATE_INTERVAL_MINUTES] ?: 60,
			wallpaperTarget = prefs[Keys.WALLPAPER_TARGET]
				?.let { runCatching { WallpaperTarget.valueOf(it) }.getOrNull() }
				?: WallpaperTarget.HOME,
			unmeteredOnly = prefs[Keys.UNMETERED_ONLY] ?: true,
			poolSize = prefs[Keys.POOL_SIZE] ?: 10,
			apiKey = prefs[Keys.API_KEY] ?: "",
			autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: true
		)
	}

	suspend fun save(settings: AppSettings) {
		dataStore.edit { prefs ->
			prefs[Keys.SEARCH_QUERY] = settings.searchQuery
			prefs[Keys.CATEGORIES] = settings.categories.map { it.name }.toSet()
			prefs[Keys.PURITY] = settings.purity.map { it.name }.toSet()
			prefs[Keys.ASPECT_RATIO] = settings.aspectRatio
			prefs[Keys.UPDATE_INTERVAL_MINUTES] = settings.updateIntervalMinutes
			prefs[Keys.WALLPAPER_TARGET] = settings.wallpaperTarget.name
			prefs[Keys.UNMETERED_ONLY] = settings.unmeteredOnly
			prefs[Keys.POOL_SIZE] = settings.poolSize
			prefs[Keys.API_KEY] = settings.apiKey
			prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
		}
	}

	suspend fun saveServiceState(lastUpdatedMs: Long, currentPath: String?, previousPath: String?) {
		dataStore.edit { prefs ->
			prefs[Keys.LAST_UPDATED_MS] = lastUpdatedMs
			if (currentPath != null) prefs[Keys.CURRENT_WALLPAPER_PATH] = currentPath
			else prefs.remove(Keys.CURRENT_WALLPAPER_PATH)
			if (previousPath != null) prefs[Keys.PREVIOUS_WALLPAPER_PATH] = previousPath
			else prefs.remove(Keys.PREVIOUS_WALLPAPER_PATH)
		}
	}

	val persistedLastUpdatedMs: Flow<Long?> = dataStore.data.map { it[Keys.LAST_UPDATED_MS] }
	val persistedCurrentPath: Flow<String?> = dataStore.data.map { it[Keys.CURRENT_WALLPAPER_PATH] }
	val persistedPreviousPath: Flow<String?> = dataStore.data.map { it[Keys.PREVIOUS_WALLPAPER_PATH] }
}
