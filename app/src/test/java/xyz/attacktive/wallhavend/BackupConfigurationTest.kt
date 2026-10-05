package xyz.attacktive.wallhavend

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupConfigurationTest {
	@Test
	fun `credential DataStore is excluded from backup and device transfer`() {
		val manifest = source("AndroidManifest.xml")
		val backupRules = source("res/xml/backup_rules.xml")
		val dataExtractionRules = source("res/xml/data_extraction_rules.xml")
		val credentialPath = "datastore/wallhavend_credentials.preferences_pb"
		val exclusion = """<exclude domain="file" path="$credentialPath" />"""

		assertTrue(manifest.contains("""android:fullBackupContent="@xml/backup_rules"""))
		assertTrue(manifest.contains("""android:dataExtractionRules="@xml/data_extraction_rules"""))
		assertTrue(backupRules.contains(exclusion))
		assertTrue(dataExtractionRules.contains("<cloud-backup>"))
		assertTrue(dataExtractionRules.contains("<device-transfer>"))
		assertEquals(2, Regex(Regex.escape(exclusion)).findAll(dataExtractionRules).count())
	}

	private fun source(path: String): String {
		val moduleFile = File("src/main/$path")
		val file = if (moduleFile.exists()) {
			moduleFile
		} else {
			File("app/src/main/$path")
		}

		return file.readText()
	}
}
