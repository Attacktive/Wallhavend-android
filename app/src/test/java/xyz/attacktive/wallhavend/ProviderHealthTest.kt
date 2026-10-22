package xyz.attacktive.wallhavend

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.InvalidApiKeyException
import xyz.attacktive.wallhavend.domain.model.MissingApiKeyException
import xyz.attacktive.wallhavend.domain.model.QuotaExceededException
import xyz.attacktive.wallhavend.domain.model.RateLimitedException
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.repository.ServiceStateRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderHealthTest {
	private val settings = AppSettings(enabledSources = WallpaperSource.entries.toSet(), pexafyApiKey = "old-key")
	private val repository = ServiceStateRepository()

	@Test
	fun `successful fallback does not erase the other provider's diagnosis`() {
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, InvalidApiKeyException(WallpaperSource.PEXAFY))
		repository.recordProviderResult(WallpaperSource.WALLHAVEN, settings, null)
		val failure = repository.state.value.providerFailures.getValue(WallpaperSource.PEXAFY)
		assertEquals(AppError.InvalidApiKey(WallpaperSource.PEXAFY), failure.error)
		assertTrue(failure.isCurrent(settings))
	}

	@Test
	fun `new credentials invalidate old diagnoses including late request results`() {
		val updated = settings.copy(pexafyApiKey = "new-key")
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, InvalidApiKeyException(WallpaperSource.PEXAFY))
		val failure = repository.state.value.providerFailures.getValue(WallpaperSource.PEXAFY)
		assertFalse(failure.isCurrent(updated))
		assertFalse(failure.toString().contains("old-key"))
		assertFalse(failure.isCurrent(settings.copy(enabledSources = setOf(WallpaperSource.WALLHAVEN))))
	}

	@Test
	fun `a successful Pexafy attempt clears its own failure only`() {
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, InvalidApiKeyException(WallpaperSource.PEXAFY))
		repository.recordProviderResult(WallpaperSource.WALLHAVEN, settings, InvalidApiKeyException(WallpaperSource.WALLHAVEN))
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, null)
		assertEquals(setOf(WallpaperSource.WALLHAVEN), repository.state.value.providerFailures.keys)
	}

	@Test
	fun `missing configuration is separate from runtime quota and rate diagnostics`() {
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, MissingApiKeyException(WallpaperSource.PEXAFY))
		assertTrue(repository.state.value.providerFailures.isEmpty())
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, QuotaExceededException(WallpaperSource.PEXAFY))
		assertEquals(AppError.QuotaExceeded(WallpaperSource.PEXAFY), repository.state.value.providerFailures.getValue(WallpaperSource.PEXAFY).error)
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, RateLimitedException(WallpaperSource.PEXAFY, 45))
		assertEquals(AppError.RateLimited(WallpaperSource.PEXAFY, 45), repository.state.value.providerFailures.getValue(WallpaperSource.PEXAFY).error)
	}

	@Test
	fun `delayed generic error clearing cannot erase provider health`() = runTest {
		repository.repositoryScope = backgroundScope
		repository.postError(AppError.NoResults)
		repository.recordProviderResult(WallpaperSource.PEXAFY, settings, QuotaExceededException(WallpaperSource.PEXAFY))
		testScheduler.advanceTimeBy(11_000)
		testScheduler.runCurrent()
		assertNull(repository.state.value.error)
		assertTrue(repository.state.value.providerFailures.containsKey(WallpaperSource.PEXAFY))
	}

	@Test
	fun `direct error replacement cancels an older delayed clear`() = runTest {
		repository.repositoryScope = backgroundScope
		repository.postError(AppError.NoResults)
		testScheduler.advanceTimeBy(5_000)
		repository.update { it.copy(error = AppError.UnsupportedFormat) }
		testScheduler.advanceTimeBy(6_000)
		testScheduler.runCurrent()
		assertEquals(AppError.UnsupportedFormat, repository.state.value.error)
	}
}
