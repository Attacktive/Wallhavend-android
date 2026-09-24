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
}
