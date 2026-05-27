package xyz.attacktive.wallhavend.util

import android.util.Log

/**
 * Logging seam so production code never calls android.util.Log directly.
 * Keeps JVM unit tests free of the "Method not mocked" stub crash while
 * leaving every *other* unmocked Android call to fail loudly.
 */
interface AppLogger {
	fun debug(tag: String, message: String)
	fun error(tag: String, message: String, throwable: Throwable? = null)
}

class LogcatLogger : AppLogger {
	override fun debug(tag: String, message: String) {
		Log.d(tag, message)
	}

	override fun error(tag: String, message: String, throwable: Throwable?) {
		if (throwable != null) {
			Log.e(tag, message, throwable)
		} else {
			Log.e(tag, message)
		}
	}
}
