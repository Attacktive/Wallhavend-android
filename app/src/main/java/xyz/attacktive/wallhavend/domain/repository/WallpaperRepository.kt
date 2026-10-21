package xyz.attacktive.wallhavend.domain.repository

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.UndersizedWallpaperException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

/**
 * Blends every enabled source into one rotation.
 * Sources are tried in random order so none of them dominates, and one that fails to search or to download hands over to the next instead of failing the whole update — only an all-sources-failed run surfaces an error.
 */
@Singleton
class WallpaperRepository @Inject constructor(
	private val providers: Set<@JvmSuppressWildcards WallpaperProvider>,
	private val fileManager: WallpaperFileManager
) {
	suspend fun next(settings: AppSettings, screenInfo: ScreenInfo): Result<Pair<Wallpaper, File>> {
		val failures = mutableListOf<Throwable>()

		val enabledProviders = providers.filter { it.source in settings.enabledSources }
			.shuffled()

		val minimumWidth = screenInfo.width.takeIf { settings.avoidBlurryWallpapers }
		val minimumHeight = screenInfo.height.takeIf { settings.avoidBlurryWallpapers }

		for (provider in enabledProviders) {
			val blockedIds = blockedIdsFor(provider.source, settings.blockedIds)
			val attempt = fetchFromProvider(
				provider = provider,
				settings = settings,
				screenInfo = screenInfo,
				blockedIds = blockedIds,
				minimumWidth = minimumWidth,
				minimumHeight = minimumHeight
			)

			if (attempt.isSuccess) {
				return attempt
			}

			attempt.exceptionOrNull()
				?.let { failures.add(it) }
		}

		return Result.failure(failures.mostInformative())
	}

	private suspend fun fetchFromProvider(
		provider: WallpaperProvider,
		settings: AppSettings,
		screenInfo: ScreenInfo,
		blockedIds: Set<String>,
		minimumWidth: Int?,
		minimumHeight: Int?
	): Result<Pair<Wallpaper, File>> {
		repeat(MAX_CANDIDATE_ATTEMPTS) {
			val candidate = provider.next(settings, screenInfo, blockedIds)
				.getOrElse { error -> return Result.failure(error) }

			val download = fileManager.download(candidate, minimumWidth, minimumHeight)
			if (download.isSuccess) {
				return Result.success(candidate to download.getOrThrow())
			}

			val failure = download.exceptionOrNull() ?: return Result.failure(NoResultsException())
			if (failure !is UndersizedWallpaperException) {
				return Result.failure(failure)
			}
		}

		return Result.failure(NoResultsException())
	}

	private fun blockedIdsFor(source: WallpaperSource, rawIds: Set<String>) = rawIds
		.map { WallpaperIdentity.parse(it) }
		.filter { it.source == source }
		.map { it.id }
		.toSet()

	companion object {
		private const val MAX_CANDIDATE_ATTEMPTS = 5
	}
}

/** "No results" says the least about what went wrong, so a genuine search or download failure from any source wins the report. */
private fun List<Throwable>.mostInformative() = firstOrNull { it !is NoResultsException }
	?: firstOrNull()
	?: NoResultsException()
