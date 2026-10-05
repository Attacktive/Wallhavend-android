package xyz.attacktive.wallhavend

import kotlinx.coroutines.flow.first
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

class SettingsSecurityTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	private fun TestScope.dataStore(name: String) = PreferenceDataStoreFactory.create(
		scope = backgroundScope,
		produceFile = { tmpFolder.newFile("$name.preferences_pb") }
	)

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
}
