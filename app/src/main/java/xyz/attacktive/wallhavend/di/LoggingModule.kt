package xyz.attacktive.wallhavend.di

import xyz.attacktive.wallhavend.util.AppLogger
import xyz.attacktive.wallhavend.util.LogcatLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
	@Provides
	@Singleton
	fun provideAppLogger(): AppLogger = LogcatLogger()
}
