package xyz.attacktive.wallhavend.di

import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import xyz.attacktive.wallhavend.util.AppLogger
import xyz.attacktive.wallhavend.util.LogcatLogger

@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
	@Provides
	@Singleton
	fun provideAppLogger(): AppLogger = LogcatLogger()
}
