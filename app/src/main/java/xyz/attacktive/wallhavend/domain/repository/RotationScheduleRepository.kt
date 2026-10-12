package xyz.attacktive.wallhavend.domain.repository

import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RotationScheduleDataStore

internal data class RotationScheduleState(
	val nextDueElapsedRealtime: Long? = null,
	val intervalMinutes: Int? = null,
	val bootCount: Int? = null,
	val generation: Long = 0,
	val inFlightDueElapsedRealtime: Long? = null,
	val inFlightWorkId: String? = null,
	val lastCompletedDueElapsedRealtime: Long? = null
)

@Singleton
class RotationScheduleRepository @Inject constructor(@RotationScheduleDataStore private val dataStore: DataStore<Preferences>) {
	private object Keys {
		val NEXT_DUE_ELAPSED_REALTIME = longPreferencesKey("next_due_elapsed_realtime")
		val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
		val BOOT_COUNT = intPreferencesKey("boot_count")
		val GENERATION = longPreferencesKey("generation")
		val IN_FLIGHT_DUE_ELAPSED_REALTIME = longPreferencesKey("in_flight_due_elapsed_realtime")
		val IN_FLIGHT_WORK_ID = stringPreferencesKey("in_flight_work_id")
		val LAST_COMPLETED_DUE_ELAPSED_REALTIME = longPreferencesKey("last_completed_due_elapsed_realtime")
	}

	internal suspend fun read(): RotationScheduleState = dataStore.data.first()
		.run {
			RotationScheduleState(
				nextDueElapsedRealtime = get(Keys.NEXT_DUE_ELAPSED_REALTIME),
				intervalMinutes = get(Keys.INTERVAL_MINUTES),
				bootCount = get(Keys.BOOT_COUNT),
				generation = get(Keys.GENERATION) ?: 0,
				inFlightDueElapsedRealtime = get(Keys.IN_FLIGHT_DUE_ELAPSED_REALTIME),
				inFlightWorkId = get(Keys.IN_FLIGHT_WORK_ID),
				lastCompletedDueElapsedRealtime = get(Keys.LAST_COMPLETED_DUE_ELAPSED_REALTIME)
			)
		}

	internal suspend fun write(state: RotationScheduleState) {
		dataStore.edit { preferences ->
			preferences[Keys.GENERATION] = state.generation

			writeOptional(preferences, Keys.NEXT_DUE_ELAPSED_REALTIME, state.nextDueElapsedRealtime)
			writeOptional(preferences, Keys.INTERVAL_MINUTES, state.intervalMinutes)
			writeOptional(preferences, Keys.BOOT_COUNT, state.bootCount)
			writeOptional(preferences, Keys.IN_FLIGHT_DUE_ELAPSED_REALTIME, state.inFlightDueElapsedRealtime)
			writeOptional(preferences, Keys.IN_FLIGHT_WORK_ID, state.inFlightWorkId)
			writeOptional(preferences, Keys.LAST_COMPLETED_DUE_ELAPSED_REALTIME, state.lastCompletedDueElapsedRealtime)
		}
	}

	private fun <T: Any> writeOptional(preferences: MutablePreferences, key: Preferences.Key<T>, value: T?) {
		if (value != null) {
			preferences[key] = value
		} else {
			preferences.remove(key)
		}
	}
}
