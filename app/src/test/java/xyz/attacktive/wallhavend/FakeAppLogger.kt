package xyz.attacktive.wallhavend

import xyz.attacktive.wallhavend.util.AppLogger

/**
 * Recording test double for AppLogger. Captures entries so tests can both
 * run without touching android.util.Log and assert on logged output later.
 */
class FakeAppLogger : AppLogger {
	data class Entry(val level: String, val tag: String, val message: String, val throwable: Throwable? = null)

	val entries = mutableListOf<Entry>()

	override fun debug(tag: String, message: String) {
		entries += Entry("D", tag, message)
	}

	override fun error(tag: String, message: String, throwable: Throwable?) {
		entries += Entry("E", tag, message, throwable)
	}
}
