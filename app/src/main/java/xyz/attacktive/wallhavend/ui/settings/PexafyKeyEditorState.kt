package xyz.attacktive.wallhavend.ui.settings

import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.configuredSources

/** Derives editor actions from a draft without persisting it or validating the credential. */
internal class PexafyKeyEditorState(settings: AppSettings, draftKey: String) {
	private val selectedSources = settings.configuredSources
	private val enabled = WallpaperSource.PEXAFY in selectedSources
	private val savedKeyMissing = settings.pexafyApiKey.isBlank()
	private val draftKeyMissing = draftKey.isBlank()
	private val changed = draftKey != settings.pexafyApiKey
	private val removing = draftKeyMissing && !savedKeyMissing

	val needsReplacement
		get() = removing && enabled && selectedSources.size == 1

	val isError
		get() = needsReplacement || (savedKeyMissing && draftKeyMissing)

	val canSave
		get() = !needsReplacement && (removing || (!draftKeyMissing && (!enabled || changed)))

	val enableOnSave
		get() = !enabled && !draftKeyMissing

	val hintRes
		get() = when {
			needsReplacement -> R.string.provider_key_choose_replacement
			savedKeyMissing -> R.string.provider_pexafy_setup_required
			changed -> R.string.provider_key_unsaved
			else -> R.string.provider_key_not_validated
		}

	val actionRes
		get() = when {
			removing && enabled -> R.string.provider_pexafy_remove_disable
			removing -> R.string.provider_key_remove
			!enabled -> R.string.provider_pexafy_save_enable
			else -> R.string.provider_key_save
		}
}

/** The editor follows source selection, except while explicitly setting up an inactive Pexafy source. */
internal fun AppSettings.shouldShowPexafyKeyEditor(setupRequested: Boolean) =
	setupRequested || WallpaperSource.PEXAFY in enabledSources

/** A selected source with missing credentials still needs setup until another source replaces it. */
internal fun AppSettings.canDismissPexafySetup(setupRequested: Boolean) =
	setupRequested && WallpaperSource.PEXAFY !in enabledSources

/** Pexafy remains unselected while its credential setup is pending. */
internal enum class PexafyChipState {
	DISABLED,
	PENDING_SETUP,
	ENABLED
}

internal fun AppSettings.pexafyChipState(setupRequested: Boolean) = when {
	WallpaperSource.PEXAFY in configuredSources -> PexafyChipState.ENABLED
	shouldShowPexafyKeyEditor(setupRequested) -> PexafyChipState.PENDING_SETUP
	else -> PexafyChipState.DISABLED
}
