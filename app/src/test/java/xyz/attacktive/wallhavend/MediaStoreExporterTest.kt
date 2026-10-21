package xyz.attacktive.wallhavend

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import xyz.attacktive.wallhavend.ui.home.MediaStoreExporter
import xyz.attacktive.wallhavend.ui.home.embedExifDescription
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import xyz.attacktive.wallhavend.domain.model.WallpaperMetadata

class MediaStoreExporterTest {
	@get:Rule
	val tmpFolder = TemporaryFolder()

	@Before
	fun setUp() {
		mockkConstructor(ContentValues::class)

		every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } just Runs
		every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } just Runs
	}

	@After
	fun tearDown() {
		unmockkAll()
	}

	@Test
	fun `successful save writes pending item then finalizes it`() {
		val contentResolver = mockk<ContentResolver>(relaxed = true)
		val uri = mockk<Uri>()
		val outputStream = ByteArrayOutputStream()
		val bytes = ByteArray(100) { it.toByte() }
		val file = tmpFolder.newFile("wallhaven_abc123.jpg").also { it.writeBytes(bytes) }

		every { contentResolver.insert(any(), any()) } returns uri
		every { contentResolver.openOutputStream(uri) } returns outputStream
		every { contentResolver.update(uri, any(), null, null) } returns 1

		MediaStoreExporter(contentResolver).save(file, "image/jpeg")

		assertArrayEquals(bytes, outputStream.toByteArray())

		verify(exactly = 1) { anyConstructed<ContentValues>().put(MediaStore.MediaColumns.IS_PENDING, 1) }
		verify(exactly = 1) { anyConstructed<ContentValues>().put(MediaStore.MediaColumns.IS_PENDING, 0) }

		verifyOrder {
			contentResolver.insert(any(), any())
			contentResolver.openOutputStream(uri)
			contentResolver.update(uri, any(), null, null)
		}

		verify(exactly = 0) { contentResolver.delete(uri, null, null) }
	}

	@Test
	fun `missing output stream deletes pending item`() {
		val contentResolver = mockk<ContentResolver>(relaxed = true)
		val uri = mockk<Uri>()
		val file = tmpFolder.newFile("wallhaven_abc123.jpg").also { it.writeText("wallpaper") }

		every { contentResolver.insert(any(), any()) } returns uri
		every { contentResolver.openOutputStream(uri) } returns null

		assertThrows(IllegalStateException::class.java) {
			MediaStoreExporter(contentResolver).save(file, "image/jpeg")
		}

		verify(exactly = 1) { contentResolver.delete(uri, null, null) }
		verify(exactly = 0) { contentResolver.update(uri, any(), null, null) }
	}

	@Test
	fun `copy failure deletes pending item`() {
		val contentResolver = mockk<ContentResolver>(relaxed = true)
		val uri = mockk<Uri>()
		val outputStream = object : OutputStream() {
			override fun write(byte: Int) {
				throw IOException("copy failed")
			}
		}

		val file = tmpFolder.newFile("wallhaven_abc123.jpg").also { it.writeText("wallpaper") }

		every { contentResolver.insert(any(), any()) } returns uri
		every { contentResolver.openOutputStream(uri) } returns outputStream

		assertThrows(IOException::class.java) {
			MediaStoreExporter(contentResolver).save(file, "image/jpeg")
		}

		verify(exactly = 1) { contentResolver.delete(uri, null, null) }
		verify(exactly = 0) { contentResolver.update(uri, any(), null, null) }
	}

	@Test
	fun `finalize failure deletes pending item`() {
		val contentResolver = mockk<ContentResolver>(relaxed = true)
		val uri = mockk<Uri>()
		val file = tmpFolder.newFile("wallhaven_abc123.jpg").also { it.writeText("wallpaper") }

		every { contentResolver.insert(any(), any()) } returns uri
		every { contentResolver.openOutputStream(uri) } returns ByteArrayOutputStream()
		every { contentResolver.update(uri, any(), null, null) } returns 0

		assertThrows(IllegalStateException::class.java) {
			MediaStoreExporter(contentResolver).save(file, "image/jpeg")
		}

		verify(exactly = 1) { contentResolver.delete(uri, null, null) }
	}

	@Test
	fun `save embeds description into exported JPEG EXIF metadata when present`() {
		val contentResolver = mockk<ContentResolver>(relaxed = true)
		val uri = mockk<Uri>()
		val outputStream = ByteArrayOutputStream()
		val jpegBytes = java.util.Base64.getDecoder().decode(
			"/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigD//2Q=="
		)
		val file = tmpFolder.newFile("pexafy_123.jpg").also { it.writeBytes(jpegBytes) }

		every { contentResolver.insert(any(), any()) } returns uri
		every { contentResolver.openOutputStream(uri) } returns outputStream
		every { contentResolver.update(uri, any(), null, null) } returns 1

		val metadata = WallpaperMetadata(
			sourceUrl = "https://example.com/photo",
			licenseType = "free",
			attributionText = "Photo by Jane Doe on Pexels"
		)

		MediaStoreExporter(contentResolver).save(file, "image/jpeg", metadata)

		val exportedFile = tmpFolder.newFile("exported.jpg").also { it.writeBytes(outputStream.toByteArray()) }
		val exifInterface = ExifInterface(exportedFile)
		val actualDescription = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)

		val expectedDescription = "Photo by Jane Doe on Pexels - https://example.com/photo - License: free"
		assertEquals(expectedDescription, actualDescription)
		assertNull(ExifInterface(file).getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))
	}

	@Test
	fun `save embeds description into exported PNG EXIF metadata when present`() {
		val contentResolver = mockk<ContentResolver>(relaxed = true)
		val uri = mockk<Uri>()
		val outputStream = ByteArrayOutputStream()
		val pngBytes = java.util.Base64.getDecoder().decode(
			"iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP8zwACTGCSAQANHQEDgslx/wAAAABJRU5ErkJggg=="
		)
		val file = tmpFolder.newFile("pexafy_123.png").also { it.writeBytes(pngBytes) }

		every { contentResolver.insert(any(), any()) } returns uri
		every { contentResolver.openOutputStream(uri) } returns outputStream
		every { contentResolver.update(uri, any(), null, null) } returns 1

		val metadata = WallpaperMetadata(
			sourceUrl = "https://example.com/photo_png",
			licenseType = "free",
			attributionText = "Photo by Jane Doe on Pexels"
		)

		MediaStoreExporter(contentResolver).save(file, "image/png", metadata)

		val exportedFile = tmpFolder.newFile("exported.png").also { it.writeBytes(outputStream.toByteArray()) }
		val exifInterface = ExifInterface(exportedFile)
		val actualDescription = exifInterface.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)

		val expectedDescription = "Photo by Jane Doe on Pexels - https://example.com/photo_png - License: free"
		assertEquals(expectedDescription, actualDescription)
	}

	@Test
	fun `embedExifDescription writes description to image file`() {
		val jpegBytes = java.util.Base64.getDecoder().decode(
			"/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD3+iiigD//2Q=="
		)
		val file = tmpFolder.newFile("pre_q.jpg").also { it.writeBytes(jpegBytes) }
		embedExifDescription(file, "Sample Description")

		assertEquals("Sample Description", ExifInterface(file).getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION))
	}
}
