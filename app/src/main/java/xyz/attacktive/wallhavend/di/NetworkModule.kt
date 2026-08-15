package xyz.attacktive.wallhavend.di

import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import xyz.attacktive.wallhavend.BuildConfig
import xyz.attacktive.wallhavend.data.api.OpenverseApiService
import xyz.attacktive.wallhavend.data.api.WallhavenApiService

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallhavenRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenverseRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
	@Provides
	@Singleton
	fun provideJson() = Json { ignoreUnknownKeys = true }

	@Provides
	@Singleton
	fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(30, TimeUnit.SECONDS)
		.readTimeout(30, TimeUnit.SECONDS)
		.writeTimeout(30, TimeUnit.SECONDS)
		.apply {
			if (BuildConfig.DEBUG) {
				val basicLoggingInterceptor = HttpLoggingInterceptor()
					.apply {
						level = HttpLoggingInterceptor.Level.BASIC
					}

				addInterceptor(basicLoggingInterceptor)
			}
		}
		.build()

	/** The two APIs differ only by base URL, so they share the client and the converter and are told apart by a qualifier rather than by a subclass. */
	@Provides
	@Singleton
	@WallhavenRetrofit
	fun provideWallhavenRetrofit(okHttpClient: OkHttpClient, json: Json) = retrofitFor("https://wallhaven.cc/api/v1/", okHttpClient, json)

	@Provides
	@Singleton
	@OpenverseRetrofit
	fun provideOpenverseRetrofit(okHttpClient: OkHttpClient, json: Json) = retrofitFor("https://api.openverse.org/", okHttpClient, json)

	@Provides
	@Singleton
	fun provideWallhavenApiService(@WallhavenRetrofit retrofit: Retrofit): WallhavenApiService = retrofit.create(WallhavenApiService::class.java)

	@Provides
	@Singleton
	fun provideOpenverseApiService(@OpenverseRetrofit retrofit: Retrofit): OpenverseApiService = retrofit.create(OpenverseApiService::class.java)
}

private fun retrofitFor(baseUrl: String, okHttpClient: OkHttpClient, json: Json) = Retrofit.Builder()
	.baseUrl(baseUrl)
	.client(okHttpClient)
	.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
	.build()
