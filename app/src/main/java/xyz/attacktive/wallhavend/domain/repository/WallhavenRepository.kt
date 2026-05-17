package xyz.attacktive.wallhavend.domain.repository

import xyz.attacktive.wallhavend.data.api.WallhavenApiService
import xyz.attacktive.wallhavend.data.api.dto.WallpaperDto
import xyz.attacktive.wallhavend.data.api.dto.toDomain
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.NoResultsException
import xyz.attacktive.wallhavend.domain.model.Wallpaper
import xyz.attacktive.wallhavend.domain.model.toBitString
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallhavenRepository @Inject constructor(
    private val api: WallhavenApiService,
    private val fileManager: WallpaperFileManager
) {
    private var cache = ArrayDeque<WallpaperDto>()
    private var cacheKey: SearchKey? = null

    private data class SearchKey(
        val query: String?,
        val categories: String,
        val purity: String,
        val ratios: String?,
        val apiKey: String?
    )

    private fun AppSettings.toSearchKey() = SearchKey(
        query = searchQuery.ifBlank { null },
        categories = categories.toBitString(),
        purity = purity.toBitString(),
        ratios = aspectRatio.ifBlank { null },
        apiKey = apiKey.ifBlank { null }
    )

    suspend fun next(settings: AppSettings): Result<Pair<Wallpaper, File>> {
        val key = settings.toSearchKey()
        if (key != cacheKey) {
            cache.clear()
            cacheKey = key
        }
        if (cache.isEmpty()) {
            val fetchResult = refetch(key)
            if (fetchResult.isFailure) return Result.failure(fetchResult.exceptionOrNull()!!)
        }
        if (cache.isEmpty()) return Result.failure(NoResultsException())
        val dto = cache.removeFirst()
        val wallpaper = dto.toDomain()
        return fileManager.download(wallpaper).map { file -> Pair(wallpaper, file) }
    }

    private suspend fun refetch(key: SearchKey): Result<Unit> = runCatching {
        val response = api.search(
            query = key.query,
            categories = key.categories,
            purity = key.purity,
            ratios = key.ratios,
            sorting = "random",
            seed = UUID.randomUUID().toString(),
            apiKey = key.apiKey
        )
        cache = ArrayDeque(response.data)
    }
}
