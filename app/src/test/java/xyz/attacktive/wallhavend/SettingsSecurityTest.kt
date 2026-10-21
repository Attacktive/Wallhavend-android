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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

	@Test
	fun `Pexafy API key is stored outside the normal settings DataStore`() = runTest {
		val settingsStore = dataStore("pexafy_settings")
		val credentialStore = dataStore("pexafy_credentials")
		val repository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())
		val pexafyApiKey = stringPreferencesKey("pexafy_api_key")

		repository.save(AppSettings(pexafyApiKey = "pexafy_api_secret"))

		assertNull(settingsStore.data.first()[pexafyApiKey])
		assertEquals("pexafy_api_secret", credentialStore.data.first()[pexafyApiKey])
	}

	@Test
	fun `restoring a snapshot discards Pexafy API key`() = runTest {
		val pexafyApiKey = stringPreferencesKey("pexafy_api_key")
		val settingsStore = dataStore("restored_pexafy_settings")
		val credentialStore = dataStore("restored_pexafy_credentials")

		settingsStore.edit { preferences ->
			preferences[pexafyApiKey] = "restored-pexafy-secret"
		}

		credentialStore.edit { preferences ->
			preferences[pexafyApiKey] = "restored-pexafy-secret"
		}

		clearRestoredApiKeys(settingsStore, credentialStore)

		val repository = SettingsRepository(settingsStore, credentialStore, FakeAppLogger())
		val restored = repository.settings.first()

		assertEquals("", restored.pexafyApiKey)
		assertNull(settingsStore.data.first()[pexafyApiKey])
		assertNull(credentialStore.data.first()[pexafyApiKey])
	}

	@Test
	fun `logging redacts both Wallhaven and Pexafy API keys`() = runTest {
		val settingsStore = dataStore("log_settings")
		val credentialStore = dataStore("log_credentials")
		val logger = FakeAppLogger()
		val repository = SettingsRepository(settingsStore, credentialStore, logger)

		repository.save(AppSettings(apiKey = "wh-secret-123", pexafyApiKey = "pexafy-secret-456"))
		repository.settings.first()

		val logs = logger.entries.joinToString("\n") { it.message }
		assertFalse(logs.contains("wh-secret-123"))
		assertFalse(logs.contains("pexafy-secret-456"))
		assertTrue(logs.contains("pexafyApiKey=***"))
	}
}
