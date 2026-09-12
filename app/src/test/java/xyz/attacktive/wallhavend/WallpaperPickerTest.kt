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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WallpaperPickerTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	private val mainDispatcher = UnconfinedTestDispatcher()

	@Before
	fun setUp() {
		Dispatchers.setMain(mainDispatcher)
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
		unmockkAll()
	}

	@Test
	fun `view model refreshes wallpapers off the main thread`() = runTest {
		val fileManager = mockk<WallpaperFileManager>()
		val expectedFiles = listOf(File("/path/to/wallhaven_1.jpg"), File("/path/to/openverse_2.png"))
		every { fileManager.listAll() } returns expectedFiles
		val viewModel = WallpaperPickerViewModel(fileManager)

		viewModel.refresh().join()

		assertEquals(expectedFiles, viewModel.wallpapers.value)
		verify(atLeast = 1) { fileManager.listAll() }
	}

	@Test
	fun `createResultIntent rejects a missing wallpaper`() {
		mockkStatic(FileProvider::class)
		val context = mockk<Context>()
		val file = File(tmpFolder.root, "missing.jpg")

		assertThrows(IllegalArgumentException::class.java) {
			WallpaperPickerActivity.createResultIntent(context, file)
		}

		verify(exactly = 0) { FileProvider.getUriForFile(any(), any(), file) }
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

		val file = tmpFolder.newFile("wallhaven_abc.jpg")

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

		val file = tmpFolder.newFile("openverse_xyz.png")

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
