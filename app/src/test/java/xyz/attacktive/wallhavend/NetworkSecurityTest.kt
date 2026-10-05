package xyz.attacktive.wallhavend

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.di.createBasicLoggingInterceptor

class NetworkSecurityTest {
	@Test
	fun `HTTP logging redacts the Wallhaven API key`() {
		val logs = mutableListOf<String>()
		val logger = HttpLoggingInterceptor.Logger { message -> logs += message }
		val server = MockWebServer()

		server.enqueue(MockResponse())

		try {
			server.start()

			val client = OkHttpClient.Builder()
				.addInterceptor(createBasicLoggingInterceptor(logger))
				.build()
			val request = Request.Builder()
				.url(server.url("/search?q=mountains&apikey=super-secret"))
				.build()

			client.newCall(request)
				.execute()
				.close()
		} finally {
			server.shutdown()
		}

		val output = logs.joinToString("\n")

		assertTrue(output.contains("apikey"))
		assertFalse(output.contains("super-secret"))
	}
}
