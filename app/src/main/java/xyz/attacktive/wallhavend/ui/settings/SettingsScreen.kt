package xyz.attacktive.wallhavend.ui.settings

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.POOL_SIZE_OPTIONS
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.UPDATE_INTERVAL_OPTIONS
import xyz.attacktive.wallhavend.domain.model.WallpaperTarget
import xyz.attacktive.wallhavend.domain.model.query.Category
import xyz.attacktive.wallhavend.domain.model.query.Purity
import xyz.attacktive.wallhavend.domain.model.query.Sorting
import xyz.attacktive.wallhavend.domain.model.query.ToplistRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit, onNavigateToBlocklist: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
	val settings by viewModel.settings.collectAsStateWithLifecycle()
	val saveError by viewModel.saveError.collectAsStateWithLifecycle()
	var selectedTab by rememberSaveable { mutableIntStateOf(0) }
	val scrollState = rememberScrollState()

	val tabs = listOf(
		stringResource(R.string.settings_tab_content),
		stringResource(R.string.settings_tab_schedule),
		stringResource(R.string.settings_tab_advanced)
	)

	val snackbarHostState = remember { SnackbarHostState() }

	val saveErrorFormat = stringResource(R.string.settings_error_save_failed)

	LaunchedEffect(saveError) {
		if (saveError != null) {
			snackbarHostState.showSnackbar(saveErrorFormat.format(saveError))
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

			Box(modifier = Modifier.weight(1f)) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
						.padding(16.dp)
				) {
					when (selectedTab) {
						0 -> ContentTab(settings = settings, onSave = viewModel::save, onNavigateToBlocklist = onNavigateToBlocklist)
						1 -> ScheduleTab(settings = settings, onSave = viewModel::save)
						2 -> AdvancedTab(settings = settings, onSave = viewModel::save)
					}
				}

				val scrimAlpha by animateFloatAsState(
					targetValue = if (scrollState.canScrollForward) {
						1f
					} else {
						0f
					},
					label = "footerScrim"
				)

				Box(
					modifier = Modifier
						.align(Alignment.BottomCenter)
						.fillMaxWidth()
						.height(12.dp)
						.alpha(scrimAlpha)
						.background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainerHighest)))
				)
			}

			val uriHandler = LocalUriHandler.current

			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 8.dp)
			) {
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
private fun ContentTab(settings: AppSettings, onSave: (AppSettings) -> Unit, onNavigateToBlocklist: () -> Unit) {
	var searchQuery by rememberSaveable { mutableStateOf(settings.searchQuery) }
	var filterColor by rememberSaveable { mutableStateOf(settings.filterColor) }

	LaunchedEffect(settings.searchQuery) {
		if (searchQuery != settings.searchQuery) {
			searchQuery = settings.searchQuery
		}
	}

	LaunchedEffect(settings.filterColor) {
		if (filterColor != settings.filterColor) {
			filterColor = settings.filterColor
		}
	}

	PersistOnChange(
		value = searchQuery,
		upstream = settings.searchQuery,
		onPersist = { onSave(settings.copy(searchQuery = it)) }
	)

	PersistOnChange(
		value = filterColor,
		upstream = settings.filterColor,
		onPersist = { onSave(settings.copy(filterColor = it)) }
	)

	SectionLabel(stringResource(R.string.settings_label_search_query))

	OutlinedTextField(
		value = searchQuery,
		onValueChange = { searchQuery = it },
		placeholder = { Text(stringResource(R.string.settings_placeholder_search_query)) },
		singleLine = true,
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
		modifier = Modifier.fillMaxWidth()
	)

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_categories))

	MultiSelectChips(
		entries = Category.entries,
		selected = settings.categories,
		nameRes = { it.nameRes },
		onSelectionChange = { onSave(settings.copy(categories = it)) }
	)

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_content_rating))

	MultiSelectChips(
		entries = Purity.entries,
		selected = settings.purity,
		nameRes = { it.nameRes },
		onSelectionChange = { onSave(settings.copy(purity = it)) }
	)

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_filter_color))

	ColorSwatchPicker(
		selected = filterColor,
		onSelect = { newColor ->
			filterColor = newColor
			onSave(settings.copy(filterColor = newColor))
		}
	)

	OutlinedTextField(
		value = filterColor,
		onValueChange = { filterColor = it.replace("#", "").lowercase() },
		placeholder = { Text(stringResource(R.string.settings_placeholder_filter_color)) },
		trailingIcon = {
			if (filterColor.isNotEmpty()) {
				IconButton(onClick = {
					filterColor = ""
					onSave(settings.copy(filterColor = ""))
				}) {
					Icon(Icons.Filled.Clear, contentDescription = null)
				}
			}
		},
		singleLine = true,
		modifier = Modifier.fillMaxWidth()
	)

	Text(
		text = stringResource(R.string.settings_hint_filter_color),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(top = 4.dp)
	)

	var sortingExpanded by remember { mutableStateOf(false) }

	Spacer(Modifier.height(16.dp))
	SectionLabel(stringResource(R.string.settings_label_sorting))

	ExposedDropdownMenuBox(expanded = sortingExpanded, onExpandedChange = { sortingExpanded = it }) {
		OutlinedTextField(
			value = stringResource(settings.sorting.nameRes),
			onValueChange = {},
			readOnly = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortingExpanded) },
			modifier = Modifier
				.fillMaxWidth()
				.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
		)

		ExposedDropdownMenu(expanded = sortingExpanded, onDismissRequest = { sortingExpanded = false }) {
			Sorting.entries.forEach { option ->
				DropdownMenuItem(
					text = { Text(stringResource(option.nameRes)) },
					onClick = {
						if (option != settings.sorting) {
							onSave(settings.copy(sorting = option))
						}

						sortingExpanded = false
					}
				)
			}
		}
	}

	AnimatedVisibility(visible = settings.sorting == Sorting.TOPLIST) {
		var toplistRangeExpanded by remember { mutableStateOf(false) }

		Column {
			Spacer(Modifier.height(16.dp))
			SectionLabel(stringResource(R.string.settings_label_toplist_range))

			ExposedDropdownMenuBox(expanded = toplistRangeExpanded, onExpandedChange = { toplistRangeExpanded = it }) {
				OutlinedTextField(
					value = stringResource(settings.toplistRange.nameRes),
					onValueChange = {},
					readOnly = true,
					trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toplistRangeExpanded) },
					modifier = Modifier
						.fillMaxWidth()
						.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
				)

				ExposedDropdownMenu(expanded = toplistRangeExpanded, onDismissRequest = { toplistRangeExpanded = false }) {
					ToplistRange.entries.forEach { range ->
						DropdownMenuItem(
							text = { Text(stringResource(range.nameRes)) },
							onClick = {
								if (range != settings.toplistRange) {
									onSave(settings.copy(toplistRange = range))
								}

								toplistRangeExpanded = false
							}
						)
					}
				}
			}
		}
	}

	Spacer(Modifier.height(16.dp))

	ToggleSetting(
		label = stringResource(R.string.settings_label_avoid_blurry),
		subtitle = stringResource(R.string.settings_subtitle_avoid_blurry),
		checked = settings.avoidBlurryWallpapers,
		onToggle = { onSave(settings.copy(avoidBlurryWallpapers = it)) }
	)

	Spacer(Modifier.height(16.dp))

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.clickable { onNavigateToBlocklist() }
			.padding(vertical = 8.dp)
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(stringResource(R.string.blocklist_settings_row))
			Text(
				stringResource(R.string.blocklist_settings_subtitle, settings.blockedIds.size),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		Icon(
			Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = null,
			tint = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
	val context = LocalContext.current
	var intervalExpanded by remember { mutableStateOf(false) }

	SectionLabel(stringResource(R.string.settings_label_update_interval))

	ExposedDropdownMenuBox(expanded = intervalExpanded, onExpandedChange = { intervalExpanded = it }) {
		OutlinedTextField(
			value = formatInterval(settings.updateIntervalMinutes),
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
					text = { Text(formatInterval(minutes)) },
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
	SectionLabel(stringResource(R.string.settings_label_rotation_mode))

	RotationMode.entries.forEach { mode ->
		val enabled = mode != RotationMode.PINNED_ONLY || settings.pinnedIds.isNotEmpty()

		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.fillMaxWidth()
				.alpha(if (enabled) { 1f } else { 0.38f })
				.clickable(enabled = enabled) { onSave(settings.copy(rotationMode = mode)) }
		) {
			RadioButton(selected = settings.rotationMode == mode, onClick = null, enabled = enabled)

			Column(modifier = Modifier.padding(start = 4.dp)) {
				Text(
					text = when (mode) {
						RotationMode.FRESH_ANY -> stringResource(R.string.settings_rotation_fresh_any)
						RotationMode.FRESH_WIFI -> stringResource(R.string.settings_rotation_fresh_wifi)
						RotationMode.PINNED_ONLY -> stringResource(R.string.settings_rotation_pinned_only)
					}
				)

				val description = when {
					!enabled -> stringResource(R.string.settings_rotation_pinned_disabled_hint)
					mode == RotationMode.FRESH_ANY -> stringResource(R.string.settings_rotation_fresh_any_desc)
					mode == RotationMode.FRESH_WIFI -> stringResource(R.string.settings_rotation_fresh_wifi_desc)
					else -> stringResource(R.string.settings_rotation_pinned_only_desc)
				}

				Text(
					text = description,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}

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

	LaunchedEffect(settings.apiKey) {
		if (apiKey != settings.apiKey) {
			apiKey = settings.apiKey
		}
	}

	PersistOnChange(
		value = apiKey,
		upstream = settings.apiKey,
		onPersist = { onSave(settings.copy(apiKey = it)) }
	)

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
			RadioButton(selected = settings.poolSize == size, onClick = null)

			Text(
				text = "$size",
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
		modifier = Modifier.fillMaxWidth()
	)
}

@Composable
private fun <T> MultiSelectChips(entries: List<T>, selected: Set<T>, nameRes: (T) -> Int, onSelectionChange: (Set<T>) -> Unit) {
	Row {
		entries.forEach { entry ->
			FilterChip(
				selected = entry in selected,
				onClick = { onSelectionChange(selected.toggleKeepingOne(entry)) },
				label = { Text(stringResource(nameRes(entry))) },
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}
}

/** Toggles [item] in the set, but never lets it empty out — the final remaining selection can't be removed. */
internal fun <T> Set<T>.toggleKeepingOne(item: T) = when {
	item !in this -> this + item
	size > 1 -> this - item
	else -> this
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
private fun PersistOnChange(value: String, upstream: String, onPersist: (String) -> Unit) {
	val saveDebounceMs = 500.milliseconds

	LaunchedEffect(value, upstream) {
		if (value != upstream) {
			delay(saveDebounceMs)
			onPersist(value)
		}
	}

	val latestValue by rememberUpdatedState(value)
	val latestUpstream by rememberUpdatedState(upstream)
	val latestOnPersist by rememberUpdatedState(onPersist)

	DisposableEffect(Unit) {
		onDispose {
			if (latestValue != latestUpstream) {
				latestOnPersist(latestValue)
			}
		}
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

private data class Swatch(val hex: String, val argb: Int, val luminance: Float)

private val SWATCHES: List<Swatch> = listOf(
	"660000", "990000", "cc0000", "cc3333", "ea4c88", "993399", "663399",
	"333399", "0066cc", "0099cc", "66cccc", "77cc33", "669900", "336600",
	"666600", "999900", "cccc33", "ffff00", "ffcc33", "ff9900", "ff6600",
	"cc6633", "996633", "663300", "000000", "999999", "cccccc", "ffffff", "424153"
).map { hex ->
	val argb = (hex.toLong(16) or 0xFF000000).toInt()
	val r = (argb shr 16) and 0xFF
	val g = (argb shr 8) and 0xFF
	val b = argb and 0xFF
	val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f

	Swatch(hex, argb, luminance)
}

@Composable
private fun ColorSwatchPicker(selected: String, onSelect: (String) -> Unit) {
	val selectedSet = remember(selected) {
		selected.split(",")
			.map { it.trim().lowercase() }
			.filter { it.isNotEmpty() }
			.toSet()
	}

	FlowRow(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp)
	) {
		SWATCHES.forEach { swatch ->
			val isSelected = swatch.hex in selectedSet

			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.padding(2.dp)
					.size(32.dp)
					.clip(CircleShape)
					.background(Color(swatch.argb))
					.border(
						width = if (isSelected) { 2.dp } else { 0.5.dp },
						color = if (isSelected) { MaterialTheme.colorScheme.primary } else { Color.Gray.copy(alpha = 0.4f) },
						shape = CircleShape
					)
					.clickable {
						val newSet = if (isSelected) {
							selectedSet - swatch.hex
						} else {
							selectedSet + swatch.hex
						}

						onSelect(newSet.joinToString(","))
					}
			) {
				if (isSelected) {
					Icon(
						imageVector = Icons.Filled.Check,
						contentDescription = null,
						tint = if (swatch.luminance > 0.5f) { Color.Black } else { Color.White },
						modifier = Modifier.size(18.dp)
					)
				}
			}
		}
	}
}

@Composable
private fun formatInterval(minutes: Int) = when {
	minutes < 60 -> stringResource(R.string.settings_unit_min, minutes)
	minutes == 60 -> stringResource(R.string.settings_unit_hr_single)
	minutes % 60 == 0 -> stringResource(R.string.settings_unit_hr_plural, minutes / 60)
	else -> stringResource(R.string.settings_unit_min, minutes)
}

