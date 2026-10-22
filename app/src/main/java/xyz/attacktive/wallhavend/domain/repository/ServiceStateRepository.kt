package xyz.attacktive.wallhavend.domain.repository

import androidx.annotation.VisibleForTesting
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.InvalidApiKeyException
import xyz.attacktive.wallhavend.domain.model.MissingApiKeyException
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ProviderFailure
import xyz.attacktive.wallhavend.domain.model.QuotaExceededException
import xyz.attacktive.wallhavend.domain.model.RateLimitedException
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.model.UnsupportedFormatException
import xyz.attacktive.wallhavend.domain.model.WallpaperSource

@Singleton
class ServiceStateRepository @Inject constructor() {
	private val _state = MutableStateFlow(ServiceState())
	val state = _state.asStateFlow()

	@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
	internal var repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private var errorClearJob: Job? = null

	@Synchronized
	fun update(transform: (ServiceState) -> ServiceState) {
		val previous = _state.value
		val next = transform(previous)
		if (next.error != previous.error) {
			errorClearJob?.cancel()
			errorClearJob = null
		}

		_state.value = next
	}

	fun recordProviderResult(source: WallpaperSource, settings: AppSettings, failure: Throwable?) {
		val error = when (failure) {
			null, is NoResultsException, is UnsupportedFormatException, is MissingApiKeyException -> null
			is InvalidApiKeyException -> AppError.InvalidApiKey(source)
			is QuotaExceededException -> AppError.QuotaExceeded(source)
			is RateLimitedException -> AppError.RateLimited(source, failure.retryAfterSeconds)
			is HttpException -> AppError.ApiError(failure.code(), source)
			else -> AppError.NetworkError(failure.message ?: failure.javaClass.simpleName, source)
		}

		update { state ->
			val failures = if (error == null) {
				state.providerFailures - source
			} else {
				state.providerFailures + (source to ProviderFailure(source, error, settings))
			}

			state.copy(providerFailures = failures)
		}
	}

	@Synchronized
	fun postError(error: AppError, autoClear: Boolean = true) {
		update { it.copy(error = error) }

		errorClearJob?.cancel()
		if (!autoClear) {
			errorClearJob = null

			return
		}

		errorClearJob = repositoryScope.launch {
			delay(10_000.milliseconds)
			update { if (it.error == error) { it.copy(error = null) } else { it } }
		}
	}
}
