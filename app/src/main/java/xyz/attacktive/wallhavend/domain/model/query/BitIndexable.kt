package xyz.attacktive.wallhavend.domain.model.query

sealed interface BitIndexable {
	val bitIndex: Int
}

inline fun <reified T> Set<T>.toBitString() where T : Enum<T>, T : BitIndexable = enumValues<T>()
	.sortedBy { it.bitIndex }
	.joinToString("") { if (it in this) "1" else "0" }
