package xyz.attacktive.wallhavend.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.ASPECT_RATIO_SUGGESTIONS
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.POOL_SIZE_OPTIONS
import xyz.attacktive.wallhavend.domain.model.Purity
import xyz.attacktive.wallhavend.domain.model.UPDATE_INTERVAL_OPTIONS
import xyz.attacktive.wallhavend.domain.model.WallhavenCategory
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	onNavigateBack: () -> Unit,
	viewModel: SettingsViewModel = hiltViewModel()
) {
	val context = LocalContext.current
	val settings by viewModel.settings.collectAsStateWithLifecycle()
	val saveError by viewModel.saveError.collectAsStateWithLifecycle()
	var selectedTab by rememberSaveable { mutableIntStateOf(0) }
	val tabs = listOf(
		stringResource(R.string.settings_tab_content),
		stringResource(R.string.settings_tab_schedule),
		stringResource(R.string.settings_tab_advanced)
	)
	val snackbarHostState = remember { SnackbarHostState() }

	LaunchedEffect(saveError) {
		if (saveError != null) {
			snackbarHostState.showSnackbar(context.getString(R.string.settings_error_save_failed, saveError))
			viewModel.clearSaveError()
		}
	}

	Scaffold(
		snackbarHost = {
			SnackbarHost(snackbarHostState) { data ->
				Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
			}
		},
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.settings_title)) },
				navigationIcon = {
					IconButton(onClick = onNavigateBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
		) {
			PrimaryTabRow(selectedTabIndex = selectedTab) {
				tabs.forEachIndexed { index, title ->
					Tab(
						selected = selectedTab == index,
						onClick = { selectedTab = index },
						text = { Text(title) }
					)
				}
			}
			Column(
				modifier = Modifier
					.fillMaxSize()
					.verticalScroll(rememberScrollState())
					.padding(16.dp)
			) {
				when (selectedTab) {
					0 -> ContentTab(settings = settings, onSave = viewModel::save)
					1 -> ScheduleTab(settings = settings, onSave = viewModel::save)
					2 -> AdvancedTab(settings = settings, onSave = viewModel::save)
				}

				Spacer(Modifier.height(24.dp))

				val uriHandler = LocalUriHandler.current

				Text(
					text = stringResource(R.string.settings_powered_by),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textDecoration = TextDecoration.Underline,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.fillMaxWidth()
						.clickable { uriHandler.openUri("https://wallhaven.cc") }
						.padding(vertical = 8.dp)
				)

				Text(
					text = stringResource(R.string.settings_report_bug),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textDecoration = TextDecoration.Underline,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.fillMaxWidth()
						.clickable { uriHandler.openUri("https://github.com/Attacktive/Wallhavend-android/issues") }
						.padding(vertical = 8.dp)
				)
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
	var searchQuery by rememberSaveable { mutableStateOf(settings.searchQuery) }
	var aspectRatio by rememberSaveable { mutableStateOf(settings.aspectRatio) }
	var searchQueryHasFocused by remember { mutableStateOf(false) }
	var aspectRatioHasFocused by remember { mutableStateOf(false) }

	LaunchedEffect(settings.searchQuery) {
		if (searchQuery != settings.searchQuery) {
			searchQuery = settings.searchQuery
		}
	}

	LaunchedEffect(settings.aspectRatio) {
		if (aspectRatio != settings.aspectRatio) {
			aspectRatio = settings.aspectRatio
		}
	}

	SectionLabel(stringResource(R.string.settings_label_search_query))
	OutlinedTextField(
		value = searchQuery,
		onValueChange = { searchQuery = it },
		placeholder = { Text(stringResource(R.string.settings_placeholder_search_query)) },
		singleLine = true,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
		modifier = Modifier
			.fillMaxWidth()
			.onFocusChanged { focusState ->
				when {
					focusState.isFocused -> searchQueryHasFocused = true
					searchQueryHasFocused -> onSave(settings.copy(searchQuery = searchQuery))
				}
			}
	)

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_categories))

	Row {
		WallhavenCategory.entries.forEach { category ->
			val isSelected = category in settings.categories
			FilterChip(
				selected = isSelected,
				onClick = {
					val newSet = if (isSelected && settings.categories.size > 1) {
						settings.categories - category
					} else if (!isSelected) {
						settings.categories + category
					} else {
						settings.categories
					}

					onSave(settings.copy(categories = newSet))
				},
				label = { Text(stringResource(category.nameRes)) },
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_content_rating))

	Row {
		Purity.entries.forEach { purity ->
			val isSelected = purity in settings.purity

			FilterChip(
				selected = isSelected,
				onClick = {
					val newSet = if (isSelected && settings.purity.size > 1) {
						settings.purity - purity
					} else if (!isSelected) {
						settings.purity + purity
					} else {
						settings.purity
					}

					onSave(settings.copy(purity = newSet))
				},
				label = { Text(stringResource(purity.nameRes)) },
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_aspect_ratio))

	val selectedRatios = aspectRatio.split(",")
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.toSet()

	Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
		ASPECT_RATIO_SUGGESTIONS.forEach { ratio ->
			FilterChip(
				selected = ratio in selectedRatios,
				onClick = {
					val newSet = if (ratio in selectedRatios) {
						selectedRatios - ratio
					} else {
						selectedRatios + ratio
					}

					aspectRatio = newSet.joinToString(",")
					onSave(settings.copy(aspectRatio = aspectRatio))
				},
				label = { Text(ratio) },
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}

	OutlinedTextField(
		value = aspectRatio,
		onValueChange = { aspectRatio = it },
		placeholder = { Text(stringResource(R.string.settings_placeholder_aspect_ratio)) },
		singleLine = true,
		modifier = Modifier
			.fillMaxWidth()
			.onFocusChanged { focusState ->
				when {
					focusState.isFocused -> aspectRatioHasFocused = true
					aspectRatioHasFocused -> onSave(settings.copy(aspectRatio = aspectRatio))
				}
			}
	)

	Text(
		text = stringResource(R.string.settings_hint_aspect_ratio),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 4.dp)
	)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
	val context = LocalContext.current
	var intervalExpanded by remember { mutableStateOf(false) }

	SectionLabel(stringResource(R.string.settings_label_update_interval))
	ExposedDropdownMenuBox(
		expanded = intervalExpanded,
		onExpandedChange = { intervalExpanded = it }
	) {
		OutlinedTextField(
			value = formatInterval(context, settings.updateIntervalMinutes),
			onValueChange = {},
			readOnly = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
			modifier = Modifier
				.fillMaxWidth()
				.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
		)

		ExposedDropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
			UPDATE_INTERVAL_OPTIONS.forEach { minutes ->
				DropdownMenuItem(
					text = { Text(formatInterval(context, minutes)) },
					onClick = {
						if (minutes != settings.updateIntervalMinutes) {
							onSave(settings.copy(updateIntervalMinutes = minutes))
							Toast.makeText(context, R.string.settings_toast_update_delay, Toast.LENGTH_SHORT).show()
						}

						intervalExpanded = false
					}
				)
			}
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_wallpaper_target))
	Text(
		text = stringResource(R.string.settings_hint_wallpaper_target),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	WallpaperTarget.entries.forEach { target ->
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.clickable { onSave(settings.copy(wallpaperTarget = target)) }
		) {
			RadioButton(selected = settings.wallpaperTarget == target, onClick = null)

			Text(
				text = when (target) {
					WallpaperTarget.HOME -> stringResource(R.string.settings_target_home)
					WallpaperTarget.LOCK -> stringResource(R.string.settings_target_lock)
					WallpaperTarget.BOTH -> stringResource(R.string.settings_target_both)
				},
				modifier = Modifier.padding(start = 4.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	ToggleSetting(
		label = stringResource(R.string.settings_label_wifi_only),
		subtitle = stringResource(R.string.settings_subtitle_wifi_only),
		checked = settings.wifiOnly,
		onToggle = { onSave(settings.copy(wifiOnly = it)) }
	)

	Spacer(Modifier.height(8.dp))
	ToggleSetting(
		label = stringResource(R.string.settings_label_auto_start),
		subtitle = stringResource(R.string.settings_subtitle_auto_start),
		checked = settings.autoStartOnBoot,
		onToggle = { onSave(settings.copy(autoStartOnBoot = it)) }
	)
}

@Composable
private fun AdvancedTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
	var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
	var apiKeyHasFocused by remember { mutableStateOf(false) }

	LaunchedEffect(settings.apiKey) {
		if (apiKey != settings.apiKey) apiKey = settings.apiKey
	}

	SectionLabel(stringResource(R.string.settings_label_pool_size))
	Text(
		text = stringResource(R.string.settings_subtitle_pool_size),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(bottom = 8.dp)
	)

	POOL_SIZE_OPTIONS.forEach { size ->
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.clickable { onSave(settings.copy(poolSize = size)) }
		) {
			RadioButton(
				selected = settings.poolSize == size,
				onClick = null
			)

			Text(
				text = if (size == 0) {
					stringResource(R.string.settings_pool_size_zero)
				} else {
					"$size"
				},
				modifier = Modifier.padding(start = 4.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_api_key))
	OutlinedTextField(
		value = apiKey,
		onValueChange = { apiKey = it },
		placeholder = { Text(stringResource(R.string.settings_placeholder_api_key)) },
		visualTransformation = PasswordVisualTransformation(),
		keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
		modifier = Modifier
			.fillMaxWidth()
			.onFocusChanged { focusState ->
				when {
					focusState.isFocused -> apiKeyHasFocused = true
					apiKeyHasFocused -> onSave(settings.copy(apiKey = apiKey))
				}
			}
	)
}

@Composable
private fun ToggleSetting(label: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
	Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		Column(modifier = Modifier.weight(1f)) {
			Text(label)
			Text(
				subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		Switch(checked = checked, onCheckedChange = onToggle)
	}
}

@Composable
private fun SectionLabel(text: String) {
	Text(
		text = text,
		style = MaterialTheme.typography.labelSmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(bottom = 4.dp)
	)
}

private fun formatInterval(context: android.content.Context, minutes: Int) = when {
	minutes < 60 -> context.getString(R.string.settings_unit_min, minutes)
	minutes == 60 -> context.getString(R.string.settings_unit_hr_single)
	minutes % 60 == 0 -> context.getString(R.string.settings_unit_hr_plural, minutes / 60)
	else -> context.getString(R.string.settings_unit_min, minutes)
}
