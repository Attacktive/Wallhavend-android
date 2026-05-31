package xyz.attacktive.wallhavend.domain.model

import androidx.annotation.StringRes
import xyz.attacktive.wallhavend.R

enum class WallhavenCategory(override val bitIndex: Int, @get:StringRes val nameRes: Int): BitIndexable {
	GENERAL(0, R.string.category_general), ANIME(1, R.string.category_anime), PEOPLE(2, R.string.category_people)
}
