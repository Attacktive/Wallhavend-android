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

	@Test
	fun `HTTP logging redacts the Pexafy x-api-key header`() {
		val logs = mutableListOf<String>()
		val logger = HttpLoggingInterceptor.Logger { message -> logs += message }
		val server = MockWebServer()

		server.enqueue(MockResponse())

		try {
			server.start()

			val client = OkHttpClient.Builder()
				.addInterceptor(
					createBasicLoggingInterceptor(logger).apply {
						level = HttpLoggingInterceptor.Level.HEADERS
					}
				)
				.build()
			val request = Request.Builder()
				.url(server.url("/api/v1/search/photos?q=mountains"))
				.header("x-api-key", "pexafy_api_super_secret")
				.build()

			client.newCall(request)
				.execute()
				.close()
		} finally {
			server.shutdown()
		}

		val output = logs.joinToString("\n")

		assertTrue(output.contains("x-api-key"))
		assertFalse(output.contains("pexafy_api_super_secret"))
	}
}
