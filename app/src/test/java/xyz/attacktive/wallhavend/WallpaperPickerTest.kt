package xyz.attacktive.wallhavend

import java.io.File
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import xyz.attacktive.wallhavend.ui.picker.WallpaperPickerActivity
import xyz.attacktive.wallhavend.ui.picker.WallpaperPickerViewModel

class WallpaperPickerTest {
	@After
	fun tearDown() {
		unmockkAll()
	}

	@Test
	fun `view model loads wallpapers from file manager on creation`() {
		val fileManager = mockk<WallpaperFileManager>()
		val expectedFiles = listOf(File("/path/to/wallhaven_1.jpg"), File("/path/to/openverse_2.png"))
		every { fileManager.listAll() } returns expectedFiles
		val viewModel = WallpaperPickerViewModel(fileManager)

		assertEquals(expectedFiles, viewModel.wallpapers)
	}

	@Test
	fun `createResultIntent configures uri, mime type, and grant permissions for jpeg`() {
		mockkStatic(FileProvider::class)
		mockkStatic(ClipData::class)
		mockkConstructor(Intent::class)

		val mockContext = mockk<Context>()
		val mockUri = mockk<Uri>()
		val mockClipData = mockk<ClipData>()

		every { mockContext.packageName } returns "xyz.attacktive.wallhavend"

		val file = File("/files/wallpapers/wallhaven_abc.jpg")

		every { FileProvider.getUriForFile(mockContext, "xyz.attacktive.wallhavend.fileprovider", file) } returns mockUri
		every { ClipData.newRawUri(null, mockUri) } returns mockClipData
		every { anyConstructed<Intent>().setDataAndType(any(), any()) } returns mockk()
		every { anyConstructed<Intent>().clipData = any() } returns Unit
		every { anyConstructed<Intent>().addFlags(any()) } returns mockk()

		WallpaperPickerActivity.createResultIntent(mockContext, file)

		verify {
			FileProvider.getUriForFile(mockContext, "xyz.attacktive.wallhavend.fileprovider", file)
			anyConstructed<Intent>().setDataAndType(mockUri, "image/jpeg")
			anyConstructed<Intent>().clipData = mockClipData
			anyConstructed<Intent>().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
	}

	@Test
	fun `createResultIntent configures uri, mime type, and grant permissions for png`() {
		mockkStatic(FileProvider::class)
		mockkStatic(ClipData::class)
		mockkConstructor(Intent::class)

		val mockContext = mockk<Context>()
		val mockUri = mockk<Uri>()
		val mockClipData = mockk<ClipData>()

		every { mockContext.packageName } returns "xyz.attacktive.wallhavend"

		val file = File("/files/wallpapers/openverse_xyz.png")

		every { FileProvider.getUriForFile(mockContext, "xyz.attacktive.wallhavend.fileprovider", file) } returns mockUri
		every { ClipData.newRawUri(null, mockUri) } returns mockClipData
		every { anyConstructed<Intent>().setDataAndType(any(), any()) } returns mockk()
		every { anyConstructed<Intent>().clipData = any() } returns Unit
		every { anyConstructed<Intent>().addFlags(any()) } returns mockk()

		WallpaperPickerActivity.createResultIntent(mockContext, file)

		verify {
			FileProvider.getUriForFile(mockContext, "xyz.attacktive.wallhavend.fileprovider", file)
			anyConstructed<Intent>().setDataAndType(mockUri, "image/png")
			anyConstructed<Intent>().clipData = mockClipData
			anyConstructed<Intent>().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
	}
}
