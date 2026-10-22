package xyz.attacktive.wallhavend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.ui.settings.PexafyKeyEditorState
import xyz.attacktive.wallhavend.ui.settings.shouldShowPexafyKeyEditor

class PexafyKeyEditorStateTest {
	private val configured = AppSettings(enabledSources = setOf(WallpaperSource.WALLHAVEN, WallpaperSource.PEXAFY), pexafyApiKey = "saved-key")

	@Test
	fun `blank setup cannot be saved or enabled`() {
		val editor = PexafyKeyEditorState(AppSettings(), "")

		assertFalse(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertTrue(editor.isError)
	}

	@Test
	fun `whitespace drafts do not count as a setup key`() {
		val editor = PexafyKeyEditorState(AppSettings(), " \t\n")

		assertFalse(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertTrue(editor.isError)
	}

	@Test
	fun `a nonblank setup draft offers explicit activation`() {
		val editor = PexafyKeyEditorState(AppSettings(), "new-key")

		assertTrue(editor.canSave)
		assertTrue(editor.enableOnSave)
		assertFalse(editor.isError)
	}

	@Test
	fun `an unchanged active key has nothing to save`() {
		val editor = PexafyKeyEditorState(configured, "saved-key")

		assertFalse(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertFalse(editor.isError)
	}

	@Test
	fun `replacing an active key does not request another activation`() {
		val editor = PexafyKeyEditorState(configured, "replacement-key")

		assertTrue(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertFalse(editor.isError)
	}

	@Test
	fun `an unchanged saved key can explicitly enable a disabled provider`() {
		val settings = configured.copy(enabledSources = setOf(WallpaperSource.WALLHAVEN))
		val editor = PexafyKeyEditorState(settings, "saved-key")

		assertTrue(editor.canSave)
		assertTrue(editor.enableOnSave)
	}

	@Test
	fun `removing an active key is allowed when another source remains`() {
		val editor = PexafyKeyEditorState(configured, "")

		assertTrue(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertFalse(editor.needsReplacement)
		assertFalse(editor.isError)
	}

	@Test
	fun `removing the sole provider key requires a replacement source`() {
		val settings = configured.copy(enabledSources = setOf(WallpaperSource.PEXAFY))
		val editor = PexafyKeyEditorState(settings, " \t")

		assertFalse(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertTrue(editor.needsReplacement)
		assertTrue(editor.isError)
	}

	@Test
	fun `removing a disabled provider key does not enable it`() {
		val settings = configured.copy(enabledSources = setOf(WallpaperSource.WALLHAVEN))
		val editor = PexafyKeyEditorState(settings, "")

		assertTrue(editor.canSave)
		assertFalse(editor.enableOnSave)
		assertFalse(editor.needsReplacement)
	}

	@Test
	fun `a restored keyless selection still requires explicit activation`() {
		val settings = AppSettings(enabledSources = setOf(WallpaperSource.PEXAFY))
		val editor = PexafyKeyEditorState(settings, "new-key")

		assertTrue(editor.canSave)
		assertTrue(editor.enableOnSave)
		assertFalse(editor.needsReplacement)
		assertFalse(editor.isError)
	}

	@Test
	fun `disabled Pexafy hides its editor but retains its saved key`() {
		val disabled = configured.copy(enabledSources = setOf(WallpaperSource.WALLHAVEN))

		assertTrue(configured.shouldShowPexafyKeyEditor(false))
		assertFalse(disabled.shouldShowPexafyKeyEditor(false))
		assertEquals("saved-key", disabled.pexafyApiKey)
		assertTrue(disabled.copy(enabledSources = configured.enabledSources).shouldShowPexafyKeyEditor(false))
	}

	@Test
	fun `unconfigured Pexafy shows its editor after explicit setup request`() {
		val settings = AppSettings()

		assertFalse(settings.shouldShowPexafyKeyEditor(false))
		assertTrue(settings.shouldShowPexafyKeyEditor(true))
	}

	@Test
	fun `restored keyless Pexafy selection still shows its setup editor`() {
		val settings = AppSettings(enabledSources = setOf(WallpaperSource.PEXAFY))

		assertTrue(settings.shouldShowPexafyKeyEditor(false))
	}
}
