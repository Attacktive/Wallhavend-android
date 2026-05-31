package xyz.attacktive.wallhavend.domain.model

import androidx.annotation.StringRes
import xyz.attacktive.wallhavend.R

enum class Purity(override val bitIndex: Int, @StringRes val nameRes: Int): BitIndexable {
	SFW(0, R.string.purity_sfw), SKETCHY(1, R.string.purity_sketchy), NSFW(2, R.string.purity_nsfw)
}
