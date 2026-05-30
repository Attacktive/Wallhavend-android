package xyz.attacktive.wallhavend.domain.model

enum class Purity(val bitIndex: Int, val displayName: String) {
	SFW(0, "SFW"), SKETCHY(1, "Sketchy"), NSFW(2, "NSFW")
}

fun Set<Purity>.toBitString(): String {
	val bits = CharArray(3) { '0' }
	forEach { bits[it.bitIndex] = '1' }

	return String(bits)
}
