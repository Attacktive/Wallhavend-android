package xyz.attacktive.wallhavend

import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WallpaperFileManagerTest {

	@get:Rule
	val tmpFolder = TemporaryFolder()

	private val server = MockWebServer()
	private lateinit var manager: WallpaperFileManager

	@Before
	fun setUp() {
		server.start()
		manager = WallpaperFileManager(File(tmpFolder.root, "wallpapers"), OkHttpClient())
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `download saves file with correct name`() = runTest {
		val body = Buffer().write(ByteArray(100) { it.toByte() })
		server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "image/jpeg"))
		val wallpaper = Wallpaper("abc123", "https://example.com", server.url("/abc.jpg").toString(), "1920x1080", "image/jpeg")
		val result = manager.download(wallpaper)
		assertTrue(result.isSuccess)
		assertEquals("abc123.jpg", result.getOrNull()?.name)
		assertTrue(result.getOrNull()?.exists() == true)
	}

	@Test
	fun `trimToSize keeps newest N files`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val files = (1..5).map { i ->
			File(wallpapersDir, "w$i.jpg").also {
				it.writeText("data")
				it.setLastModified(System.currentTimeMillis() + i * 1000L)
			}
		}
		val kept = manager.trimToSize(3)
		assertEquals(3, kept.size)
		assertEquals(files[4].name, kept[0].name)
		assertEquals(files[3].name, kept[1].name)
		assertEquals(files[2].name, kept[2].name)
		assertTrue(!files[0].exists())
		assertTrue(!files[1].exists())
	}

	@Test
	fun `trimToSize with 0 deletes all files`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val files = (0..2).map { File(wallpapersDir, "w$it.jpg").also { f -> f.writeText("data") } }
		val kept = manager.trimToSize(0)
		assertEquals(0, kept.size)
		files.forEach { assertTrue("${it.name} should be deleted", !it.exists()) }
	}
}
