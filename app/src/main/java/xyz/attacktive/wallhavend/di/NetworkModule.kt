package xyz.attacktive.wallhavend.di

import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import xyz.attacktive.wallhavend.BuildConfig
import xyz.attacktive.wallhavend.data.api.WallhavenApiService

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

	@Provides
	@Singleton
	fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
		.baseUrl("https://wallhaven.cc/api/v1/")
		.client(okHttpClient)
		.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
		.build()

	@Provides
	@Singleton
	fun provideWallhavenApiService(retrofit: Retrofit): WallhavenApiService =
		retrofit.create(WallhavenApiService::class.java)
}
