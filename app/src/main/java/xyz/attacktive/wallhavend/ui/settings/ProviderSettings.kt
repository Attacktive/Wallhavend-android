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
	val selected = settings.configuredSources
	var showPexafySetup by rememberSaveable { mutableStateOf(false) }

	// Credentials deliberately use remember, not rememberSaveable: drafts must not enter saved-state bundles.
	var wallhavenKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
	var pexafyKey by remember(settings.pexafyApiKey) { mutableStateOf(settings.pexafyApiKey) }
	val uriHandler = LocalUriHandler.current

	Text(stringResource(R.string.settings_label_sources), style = MaterialTheme.typography.labelLarge)

	FlowRow {
		WallpaperSource.entries.forEach { source ->
			FilterChip(
				selected = source in selected,
				onClick = {
					if (source == WallpaperSource.PEXAFY && settings.pexafyApiKey.isBlank()) {
						showPexafySetup = true
					} else {
						viewModel.toggleSource(source)
					}
				},
				label = { Text(stringResource(source.nameRes)) },
				enabled = !saving,
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}

	AnimatedVisibility(visible = showPexafySetup || settings.requiresPexafySetup || settings.pexafyApiKey.isNotBlank()) {
		Column {
			val enabled = WallpaperSource.PEXAFY in selected
			val removing = pexafyKey.isBlank() && settings.pexafyApiKey.isNotBlank()
			val needsReplacement = removing && enabled && selected.size == 1
			val hint = when {
				needsReplacement -> R.string.provider_key_choose_replacement
				settings.pexafyApiKey.isBlank() -> R.string.provider_pexafy_setup_required
				pexafyKey != settings.pexafyApiKey -> R.string.provider_key_unsaved
				else -> R.string.provider_key_not_validated
			}

			Spacer(Modifier.height(8.dp))

			OutlinedTextField(
				value = pexafyKey,
				onValueChange = { pexafyKey = it },
				label = { Text(stringResource(R.string.settings_label_pexafy_api_key)) },
				supportingText = { Text(stringResource(hint)) },
				isError = needsReplacement || (settings.pexafyApiKey.isBlank() && pexafyKey.isBlank()),
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
				singleLine = true,
				enabled = !saving,
				modifier = Modifier.fillMaxWidth()
			)

			val action = when {
				removing && enabled -> R.string.provider_pexafy_remove_disable
				removing -> R.string.provider_key_remove
				!enabled -> R.string.provider_pexafy_save_enable
				else -> R.string.provider_key_save
			}

			val canSave = !saving && !needsReplacement && (removing || (pexafyKey.isNotBlank() && (!enabled || pexafyKey != settings.pexafyApiKey)))

			Button(onClick = { viewModel.savePexafyKey(pexafyKey, enable = !enabled && pexafyKey.isNotBlank()) }, enabled = canSave) {
				Text(stringResource(action))
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

	AnimatedVisibility(visible = WallpaperSource.WALLHAVEN in selected || settings.apiKey.isNotBlank()) {
		Column {
			Spacer(Modifier.height(16.dp))

			OutlinedTextField(
				value = wallhavenKey,
				onValueChange = { wallhavenKey = it },
				label = { Text(stringResource(R.string.provider_wallhaven_key)) },
				placeholder = { Text(stringResource(R.string.settings_placeholder_api_key)) },
				visualTransformation = PasswordVisualTransformation(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
				singleLine = true,
				enabled = !saving,
				modifier = Modifier.fillMaxWidth()
			)

			Button(onClick = { viewModel.saveWallhavenKey(wallhavenKey) }, enabled = !saving && wallhavenKey != settings.apiKey) {
				Text(stringResource(R.string.provider_key_save))
			}
		}
	}
}
