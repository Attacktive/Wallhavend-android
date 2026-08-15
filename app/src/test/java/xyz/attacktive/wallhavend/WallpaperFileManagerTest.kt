package xyz.attacktive.wallhavend

import java.io.File
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
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

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

	private fun makeWallpaper(id: String, path: String) = Wallpaper(
		identity = WallpaperIdentity(WallpaperSource.WALLHAVEN, id),
		pageUrl = "https://wallhaven.cc/w/$id",
		directUrl = server.url(path).toString(),
		resolution = "1920x1080"
	)

	@Test
	fun `download names the file after the source-qualified id`() = runTest {
		val body = Buffer().write(ByteArray(100) { it.toByte() })
		server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "image/jpeg"))

		val result = manager.download(makeWallpaper("abc123", "/abc.jpg"))

		assertTrue(result.isSuccess)
		assertEquals("wallhaven_abc123.jpg", result.getOrNull()?.name)
		assertTrue(result.getOrNull()?.exists() == true)
	}

	@Test
	fun `the extension comes from what the server sent, not from the url`() = runTest {
		val body = Buffer().write(ByteArray(100) { it.toByte() })
		server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "image/png"))

		val result = manager.download(makeWallpaper("abc123", "/abc"))

		assertEquals("wallhaven_abc123.png", result.getOrNull()?.name)
	}

	@Test
	fun `download rejects a body that is not a jpeg or a png`() = runTest {
		val body = Buffer().write(ByteArray(100) { it.toByte() })
		server.enqueue(MockResponse().setBody(body).addHeader("Content-Type", "image/gif"))

		val result = manager.download(makeWallpaper("abc123", "/abc.gif"))

		assertTrue(result.exceptionOrNull() is UnsupportedFormatException)
	}

	@Test
	fun `listAll returns files sorted by last modified descending`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val files = (1..3).map { i ->
			File(wallpapersDir, "w$i.jpg").also {
				it.writeText("data")
				it.setLastModified(System.currentTimeMillis() + i * 1000L)
			}
		}

		val result = manager.listAll()

		assertEquals(3, result.size)
		assertEquals(files[2].name, result[0].name)
		assertEquals(files[1].name, result[1].name)
		assertEquals(files[0].name, result[2].name)
	}

	@Test
	fun `listAll returns empty list when no files exist`() {
		assertEquals(emptyList<File>(), manager.listAll())
	}

	@Test
	fun `trimToSize keeps newest N files`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val files = (1..5).map { i ->
			File(wallpapersDir, "w$i.jpg")
				.also {
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

	@Test
	fun `trimToSize keeps a pinned file even when it is older than the cutoff`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val files = (1..5).map { i ->
			File(wallpapersDir, "w$i.jpg")
				.also {
					it.writeText("data")
					it.setLastModified(System.currentTimeMillis() + i * 1000L)
				}
		}

		// w1 is the oldest, so without a pin it would be the first evicted.
		val kept = manager.trimToSize(2, setOf("w1"))

		assertEquals(listOf("w5.jpg", "w4.jpg", "w1.jpg"), kept.map { it.name })
		assertTrue(files[0].exists())
		assertTrue(!files[1].exists())
		assertTrue(!files[2].exists())
		assertTrue(files[3].exists())
		assertTrue(files[4].exists())
	}

	@Test
	fun `trimToSize keeps pinned files when maxSize is 0`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val rotating = File(wallpapersDir, "rot.jpg").also { it.writeText("data") }
		val pinned = File(wallpapersDir, "pin.jpg").also { it.writeText("data") }

		val kept = manager.trimToSize(0, setOf("pin"))

		assertEquals(listOf("pin.jpg"), kept.map { it.name })
		assertTrue(pinned.exists())
		assertTrue(!rotating.exists())
	}

	@Test
	fun `trimToSize keeps a file pinned under either spelling of its id`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val legacy = File(wallpapersDir, "legacy.jpg").also { it.writeText("data") }
		val qualified = File(wallpapersDir, "wallhaven_fresh.jpg").also { it.writeText("data") }
		val rotating = File(wallpapersDir, "wallhaven_rot.jpg").also { it.writeText("data") }

		// A pin made before wallpapers were qualified is bare; one made after is qualified. Both must hold.
		val kept = manager.trimToSize(0, setOf("legacy", "wallhaven_fresh"))

		assertEquals(setOf("legacy.jpg", "wallhaven_fresh.jpg"), kept.map { it.name }.toSet())
		assertTrue(legacy.exists())
		assertTrue(qualified.exists())
		assertTrue(!rotating.exists())
	}

	@Test
	fun `pinned files do not count toward maxSize`() {
		val wallpapersDir = File(tmpFolder.root, "wallpapers").also { it.mkdirs() }
		val files = (1..4).map { i ->
			File(wallpapersDir, "w$i.jpg")
				.also {
					it.writeText("data")
					it.setLastModified(System.currentTimeMillis() + i * 1000L)
				}
		}

		// Pinning the oldest must not consume a rotating-buffer slot: the two newest non-pinned survive too.
		val kept = manager.trimToSize(2, setOf("w1"))

		assertEquals(3, kept.size)
		assertTrue(files[0].exists())
		assertTrue(!files[1].exists())
		assertTrue(files[2].exists())
		assertTrue(files[3].exists())
	}
}
