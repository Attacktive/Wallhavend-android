package xyz.attacktive.wallhavend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import android.app.backup.BackupAgentHelper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import xyz.attacktive.wallhavend.domain.repository.clearRestoredApiKeys

class WallhavendBackupAgent: BackupAgentHelper() {
	override fun onRestoreFinished() {
		super.onRestoreFinished()

		val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
		val settingsDataStore = PreferenceDataStoreFactory.create(
			scope = scope,
			produceFile = { preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) }
		)

		val credentialDataStore = PreferenceDataStoreFactory.create(
			scope = scope,
			produceFile = { preferencesDataStoreFile(CREDENTIAL_DATASTORE_NAME) }
		)

		try {
			runBlocking {
				clearRestoredApiKeys(settingsDataStore, credentialDataStore)
			}
		} finally {
			scope.cancel()
		}
	}

	companion object {
		private const val SETTINGS_DATASTORE_NAME = "wallhavend_settings"
		private const val CREDENTIAL_DATASTORE_NAME = "wallhavend_credentials"
	}
}
