package xyz.attacktive.wallhavend.domain.repository

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.ScreenInfo
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

/**
 * Blends every registered source into one rotation.
 * Sources are tried in random order so none of them dominates, and one that fails to search or to download hands over to the next instead of failing the whole update — only an all-sources-failed run surfaces an error.
 */
@Singleton
class WallpaperRepository @Inject constructor(
	private val providers: Set<@JvmSuppressWildcards WallpaperProvider>,
	private val fileManager: WallpaperFileManager
) {
	suspend fun next(settings: AppSettings, screenInfo: ScreenInfo): Result<Pair<Wallpaper, File>> {
		val failures = mutableListOf<Throwable>()

		for (provider in providers.shuffled()) {
			val blockedIds = blockedIdsFor(provider.source, settings.blockedIds)

			val attempt = provider.next(settings, screenInfo, blockedIds)
				.mapCatching { wallpaper -> wallpaper to fileManager.download(wallpaper).getOrThrow() }

			if (attempt.isSuccess) {
				return attempt
			}

			attempt.exceptionOrNull()
				?.let { failures.add(it) }
		}

		return Result.failure(failures.mostInformative())
	}

	private fun blockedIdsFor(source: WallpaperSource, rawIds: Set<String>) = rawIds
		.map { WallpaperIdentity.parse(it) }
		.filter { it.source == source }
		.map { it.id }
		.toSet()
}

/** "No results" says the least about what went wrong, so a genuine search or download failure from any source wins the report. */
private fun List<Throwable>.mostInformative() = firstOrNull { it !is NoResultsException }
	?: firstOrNull()
	?: NoResultsException()
