package xyz.attacktive.wallhavend.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import xyz.attacktive.wallhavend.domain.service.WallpaperFileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "wallhavend_settings")

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

	@Provides
	@Singleton
	fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
		context.dataStore

	@Provides
	@Singleton
	fun provideWallpaperFileManager(
		@ApplicationContext context: Context,
		okHttpClient: OkHttpClient
	): WallpaperFileManager {
		val dir = File(context.filesDir, "wallpapers")
		return WallpaperFileManager(dir, okHttpClient)
	}
}
