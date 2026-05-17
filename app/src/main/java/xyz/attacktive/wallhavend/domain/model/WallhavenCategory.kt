package xyz.attacktive.wallhavend.domain.model

enum class WallhavenCategory(val bitIndex: Int) {
	GENERAL(0), ANIME(1), PEOPLE(2)
}

fun Set<WallhavenCategory>.toBitString(): String {
	val bits = CharArray(3) { '0' }
	forEach { bits[it.bitIndex] = '1' }

	return String(bits)
}
