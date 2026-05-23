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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
	val settings by viewModel.settings.collectAsStateWithLifecycle()
	var selectedTab by rememberSaveable { mutableIntStateOf(0) }
	val tabs = listOf("Content", "Schedule", "Advanced")

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Settings") },
				navigationIcon = {
					IconButton(onClick = onNavigateBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
			TabRow(selectedTabIndex = selectedTab) {
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
					text = "Powered by wallhaven.cc",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textDecoration = TextDecoration.Underline,
					textAlign = TextAlign.Center,
					modifier = Modifier
						.fillMaxWidth()
						.clickable { uriHandler.openUri("https://wallhaven.cc") }
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

	SectionLabel("SEARCH QUERY (OPTIONAL)")
	OutlinedTextField(
		value = searchQuery,
		onValueChange = { searchQuery = it },
		placeholder = { Text("e.g. landscape mountains") },
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
	SectionLabel("CATEGORIES")

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
				label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel("CONTENT RATING")

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
				label = {
					Text(when (purity) {
						Purity.SFW -> "SFW"
						Purity.SKETCHY -> "Sketchy"
						Purity.NSFW -> "NSFW"
					})
				},
				modifier = Modifier.padding(end = 8.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel("ASPECT RATIO")

	val selectedRatios = aspectRatio.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

	Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
		ASPECT_RATIO_SUGGESTIONS.forEach { ratio ->
			FilterChip(
				selected = ratio in selectedRatios,
				onClick = {
					val newSet = if (ratio in selectedRatios) selectedRatios - ratio else selectedRatios + ratio
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
		placeholder = { Text("Custom, e.g. 9x16 or 9x16,16x9") },
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
		"Leave blank to skip ratio filtering",
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

	SectionLabel("UPDATE INTERVAL")
	ExposedDropdownMenuBox(
		expanded = intervalExpanded,
		onExpandedChange = { intervalExpanded = it }
	) {
		OutlinedTextField(
			value = formatInterval(settings.updateIntervalMinutes),
			onValueChange = {},
			readOnly = true,
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
			modifier = Modifier
				.fillMaxWidth()
				.menuAnchor()
		)

		ExposedDropdownMenu(expanded = intervalExpanded, onDismissRequest = { intervalExpanded = false }) {
			UPDATE_INTERVAL_OPTIONS.forEach { minutes ->
				DropdownMenuItem(
					text = { Text(formatInterval(minutes)) },
					onClick = {
						if (minutes != settings.updateIntervalMinutes) {
							onSave(settings.copy(updateIntervalMinutes = minutes))
							Toast.makeText(context, "Takes effect on next update", Toast.LENGTH_SHORT).show()
						}

						intervalExpanded = false
					}
				)
			}
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel("WALLPAPER TARGET")
	Text(
		"Lock screen may not work on some devices (e.g. Samsung One UI)",
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
					WallpaperTarget.HOME -> "Home screen only"
					WallpaperTarget.LOCK -> "Lock screen only"
					WallpaperTarget.BOTH -> "Both"
				},
				modifier = Modifier.padding(start = 4.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	ToggleSetting(
		label = "Wi-Fi only",
		subtitle = "Skip updates on mobile data",
		checked = settings.wifiOnly,
		onToggle = { onSave(settings.copy(wifiOnly = it)) }
	)

	Spacer(Modifier.height(8.dp))
	ToggleSetting(
		label = "Auto-start on boot",
		subtitle = "No root required",
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

	SectionLabel("WALLPAPER POOL SIZE")
	Text(
		"Wallpapers kept on device and shown in gallery",
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
				text = if (size == 0) "0 — current only, no gallery" else "$size",
				modifier = Modifier.padding(start = 4.dp)
			)
		}
	}

	Spacer(Modifier.height(16.dp))
	SectionLabel("API KEY (OPTIONAL, REQUIRED FOR NSFW)")
	OutlinedTextField(
		value = apiKey,
		onValueChange = { apiKey = it },
		placeholder = { Text("Your Wallhaven API key") },
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

private fun formatInterval(minutes: Int) = when {
	minutes < 60 -> "$minutes min"
	minutes == 60 -> "1 hr"
	minutes % 60 == 0 -> "${minutes / 60} hr"
	else -> "$minutes min"
}
