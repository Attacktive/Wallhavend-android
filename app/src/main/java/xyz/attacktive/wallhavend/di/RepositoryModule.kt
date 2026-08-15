package xyz.attacktive.wallhavend.di

import java.io.File
import javax.inject.Singleton
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import xyz.attacktive.wallhavend.domain.repository.OpenverseProvider
import xyz.attacktive.wallhavend.domain.repository.WallhavenProvider
import xyz.attacktive.wallhavend.domain.repository.WallpaperProvider
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager

private val Context.dataStore by preferencesDataStore(name = "wallhavend_settings")

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
	@Provides
	@Singleton
	fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

	@Provides
	@Singleton
	fun provideWallpaperFileManager(@ApplicationContext context: Context, okHttpClient: OkHttpClient): WallpaperFileManager {
		val dir = File(context.filesDir, "wallpapers")

		return WallpaperFileManager(dir, okHttpClient)
	}

	/** Every source binds itself into this set, so WallpaperRepository blends whatever is registered without naming any of them. */
	@Provides
	@IntoSet
	fun provideWallhavenProvider(wallhavenProvider: WallhavenProvider): WallpaperProvider = wallhavenProvider

	@Provides
	@IntoSet
	fun provideOpenverseProvider(openverseProvider: OpenverseProvider): WallpaperProvider = openverseProvider
}
