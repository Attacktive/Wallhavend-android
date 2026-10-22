package xyz.attacktive.wallhavend.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.WallpaperSource
import xyz.attacktive.wallhavend.domain.model.configuredSources
import xyz.attacktive.wallhavend.domain.model.requiresPexafySetup

@Composable
internal fun ProviderSettings(settings: AppSettings, viewModel: SettingsViewModel) {
	val saving by viewModel.savingProvider.collectAsStateWithLifecycle()
	var showPexafySetup by rememberSaveable { mutableStateOf(false) }

	// Credentials deliberately use remember, not rememberSaveable: drafts must not enter saved-state bundles.
	var wallhavenKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
	var pexafyKey by remember(settings.pexafyApiKey) { mutableStateOf(settings.pexafyApiKey) }

	ProviderSourceChips(settings, saving, onShowPexafySetup = { showPexafySetup = true }, onToggleSource = viewModel::toggleSource)

	AnimatedVisibility(visible = showPexafySetup || settings.requiresPexafySetup || settings.pexafyApiKey.isNotBlank()) {
		PexafyKeySettings(settings, pexafyKey, onKeyChange = { pexafyKey = it }, saving = saving, onSave = viewModel::savePexafyKey)
	}

	AnimatedVisibility(visible = WallpaperSource.WALLHAVEN in settings.configuredSources || settings.apiKey.isNotBlank()) {
		WallhavenKeySettings(settings.apiKey, wallhavenKey, onKeyChange = { wallhavenKey = it }, saving = saving, onSave = viewModel::saveWallhavenKey)
	}
}

@Composable
private fun ProviderSourceChips(settings: AppSettings, saving: Boolean, onShowPexafySetup: () -> Unit, onToggleSource: (WallpaperSource) -> Unit) {
	val selected = settings.configuredSources

	Text(stringResource(R.string.settings_label_sources), style = MaterialTheme.typography.labelLarge)

	FlowRow {
		WallpaperSource.entries.forEach { source ->
			FilterChip(
				selected = source in selected,
				onClick = {
					if (source == WallpaperSource.PEXAFY && settings.pexafyApiKey.isBlank()) {
						onShowPexafySetup()
					} else {
						onToggleSource(source)
					}
				},
				label = { Text(stringResource(source.nameRes)) },
				enabled = !saving,
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}
}

@Composable
private fun PexafyKeySettings(settings: AppSettings, key: String, onKeyChange: (String) -> Unit, saving: Boolean, onSave: (String, Boolean) -> Unit) {
	val editor = PexafyKeyEditorState(settings, key)
	val uriHandler = LocalUriHandler.current

	Column {
		Spacer(Modifier.height(8.dp))

		OutlinedTextField(
			value = key,
			onValueChange = onKeyChange,
			label = { Text(stringResource(R.string.settings_label_pexafy_api_key)) },
			supportingText = { Text(stringResource(editor.hintRes)) },
			isError = editor.isError,
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
			singleLine = true,
			enabled = !saving,
			modifier = Modifier.fillMaxWidth()
		)

		Button(onClick = { onSave(key, editor.enableOnSave) }, enabled = !saving && editor.canSave) {
			Text(stringResource(editor.actionRes))
		}

		Text(
			text = stringResource(R.string.settings_link_create_pexafy_api_key),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.primary,
			textDecoration = TextDecoration.Underline,
			modifier = Modifier.padding(top = 4.dp).clickable { uriHandler.openUri("https://pexafy.com/dashboard/api-keys/create") }
		)
	}
}

@Composable
private fun WallhavenKeySettings(savedKey: String, key: String, onKeyChange: (String) -> Unit, saving: Boolean, onSave: (String) -> Unit) {
	Column {
		Spacer(Modifier.height(16.dp))

		OutlinedTextField(
			value = key,
			onValueChange = onKeyChange,
			label = { Text(stringResource(R.string.provider_wallhaven_key)) },
			placeholder = { Text(stringResource(R.string.settings_placeholder_api_key)) },
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
			singleLine = true,
			enabled = !saving,
			modifier = Modifier.fillMaxWidth()
		)

		Button(onClick = { onSave(key) }, enabled = !saving && key != savedKey) {
			Text(stringResource(R.string.provider_key_save))
		}
	}
}
