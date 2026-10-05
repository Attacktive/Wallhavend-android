package xyz.attacktive.wallhavend

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.repository.SettingsRepository
import xyz.attacktive.wallhavend.domain.repository.clearRestoredApiKeys

class SettingsSecurityTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	private fun dataStore(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
		scope = scope,
		produceFile = { file }
	)

	private fun TestScope.dataStore(name: String) = dataStore(tmpFolder.newFile("$name.preferences_pb"), backgroundScope)

	@Test
	fun `API key is stored outside the normal settings DataStore`() = runTest {
		val settingsStore = dataStore("settings")
		val credentialStore = dataStore("credentials")
		val repository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())
		val apiKey = stringPreferencesKey("api_key")

		repository.save(AppSettings(apiKey = "secret"))

		assertNull(settingsStore.data.first()[apiKey])
		assertEquals("secret", credentialStore.data.first()[apiKey])
	}

	@Test
	fun `legacy API key moves into the credential DataStore`() = runTest {
		val settingsStore = dataStore("legacy_settings")
		val credentialStore = dataStore("legacy_credentials")
		val apiKey = stringPreferencesKey("api_key")

		settingsStore.edit { preferences ->
			preferences[apiKey] = "legacy-secret"
		}

		val repository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())

		assertEquals("legacy-secret", repository.settings.first().apiKey)
		assertNull(settingsStore.data.first()[apiKey])
		assertEquals("legacy-secret", credentialStore.data.first()[apiKey])
	}

	@Test
	fun `restoring a legacy settings snapshot discards API key`() = runTest {
		val apiKey = stringPreferencesKey("api_key")
		val searchQuery = stringPreferencesKey("search_query")
		val snapshotFile = tmpFolder.newFile("legacy_snapshot.preferences_pb")
		val legacyScope = CoroutineScope(backgroundScope.coroutineContext + Job())
		val legacyStore = dataStore(snapshotFile, legacyScope)

		legacyStore.edit { preferences ->
			preferences[apiKey] = "restored-secret"
			preferences[searchQuery] = "mountains"
		}

		legacyScope.coroutineContext.job.cancelAndJoin()

		val restoredSettingsFile = tmpFolder.newFile("restored_settings.preferences_pb")
		snapshotFile.copyTo(restoredSettingsFile, overwrite = true)

		val settingsStore = dataStore(restoredSettingsFile, backgroundScope)
		val credentialStore = dataStore("restored_credentials")

		clearRestoredApiKeys(settingsStore, credentialStore)

		val repository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())
		val restored = repository.settings.first()

		assertEquals("mountains", restored.searchQuery)
		assertEquals("", restored.apiKey)
		assertNull(settingsStore.data.first()[apiKey])
		assertNull(credentialStore.data.first()[apiKey])
	}
}
