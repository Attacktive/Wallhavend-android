package xyz.attacktive.wallhavend.domain.model

enum class WallhavenCategory(val bitIndex: Int, val displayName: String) {
	GENERAL(0, "General"), ANIME(1, "Anime"), PEOPLE(2, "People")
}

fun Set<WallhavenCategory>.toBitString(): String {
	val bits = CharArray(3) { '0' }
	forEach { bits[it.bitIndex] = '1' }

	return String(bits)
}
