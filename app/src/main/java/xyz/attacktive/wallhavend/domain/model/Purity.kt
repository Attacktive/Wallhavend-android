package xyz.attacktive.wallhavend.domain.model

enum class Purity(val bitIndex: Int) {
	SFW(0), SKETCHY(1), NSFW(2)
}

fun Set<Purity>.toBitString(): String {
	val bits = CharArray(3) { '0' }
	forEach { bits[it.bitIndex] = '1' }
	return String(bits)
}
