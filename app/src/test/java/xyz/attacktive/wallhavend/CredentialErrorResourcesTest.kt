package xyz.attacktive.wallhavend

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CredentialErrorResourcesTest(private val locale: String) {
	@Test
	fun `credential recovery messages point to the localized Content tab`() {
		val source = File(resourceDirectory(), "$locale/strings.xml").readText()
		val content = stringValue(source, "settings_tab_content")
		val advanced = stringValue(source, "settings_tab_advanced")

		listOf("error_missing_api_key", "error_invalid_api_key").forEach { name ->
			val message = stringValue(source, name)
			assertTrue("$locale/$name must point to Content", message.contains("\u2192 $content"))
			assertFalse("$locale/$name must not point to Advanced", message.contains("\u2192 $advanced"))
			assertTrue("$locale/$name must retain the provider placeholder", message.contains("%1\$s"))
		}
	}

	private fun stringValue(source: String, name: String) = requireNotNull(Regex("<string name=\"$name\">([^<]*)</string>").find(source)).groupValues[1]

	companion object {
		private fun resourceDirectory() = File("src/main/res").takeIf { it.isDirectory } ?: File("app/src/main/res")

		@JvmStatic
		@Parameterized.Parameters(name = "{0}")
		fun locales() = requireNotNull(resourceDirectory().listFiles())
			.filter { it.isDirectory && it.name.startsWith("values") && File(it, "strings.xml").isFile }
			.map { it.name }
			.sorted()
	}
}
