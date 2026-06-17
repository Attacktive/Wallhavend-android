package xyz.attacktive.wallhavend.ui.home

sealed interface BlockReplacement {
	data object None: BlockReplacement
	data class ApplyFromPool(val path: String): BlockReplacement
	data object Roll: BlockReplacement
}

internal fun blockReplacement(blockedPath: String, currentPath: String?, remainingPool: List<String>): BlockReplacement {
	if (blockedPath != currentPath) {
		return BlockReplacement.None
	}

	return when (val next = remainingPool.firstOrNull()) {
		null -> BlockReplacement.Roll
		else -> BlockReplacement.ApplyFromPool(next)
	}
}
