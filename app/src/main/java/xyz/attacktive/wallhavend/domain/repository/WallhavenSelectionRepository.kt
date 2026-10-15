package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import xyz.attacktive.wallhavend.data.api.dto.WallhavenWallpaperDto

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallhavenSelectionDataStore

@Serializable
data class WallhavenSelectionState(
	val searchKeyFingerprint: String? = null,
	val cache: List<WallhavenWallpaperDto> = emptyList(),
	val currentPage: Int = 1,
	val lastPage: Int = 1
)

interface WallhavenSelectionStore {
	suspend fun read(): WallhavenSelectionState
	suspend fun write(state: WallhavenSelectionState)
}

@Singleton
class WallhavenSelectionRepository @Inject constructor(
	@WallhavenSelectionDataStore private val dataStore: DataStore<Preferences>,
	private val json: Json
): WallhavenSelectionStore {
	private object Keys {
		val STATE = stringPreferencesKey("state")
	}

	override suspend fun read() = dataStore.data.first()[Keys.STATE]
		?.let { encoded ->
			runCatching { json.decodeFromString<WallhavenSelectionState>(encoded) }
				.getOrNull()
		}
		?: WallhavenSelectionState()

	override suspend fun write(state: WallhavenSelectionState) {
		dataStore.edit { preferences ->
			preferences[Keys.STATE] = json.encodeToString(state)
		}
	}
}

internal class InMemoryWallhavenSelectionStore: WallhavenSelectionStore {
	private var state = WallhavenSelectionState()

	override suspend fun read() = state

	override suspend fun write(state: WallhavenSelectionState) {
		this.state = state
	}
}
