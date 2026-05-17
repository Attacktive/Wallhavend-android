package xyz.attacktive.wallhavend.ui.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import xyz.attacktive.wallhavend.domain.model.AppSettings
import xyz.attacktive.wallhavend.domain.model.ASPECT_RATIO_SUGGESTIONS
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
    var selectedTab by remember { mutableIntStateOf(0) }
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
    SectionLabel("SEARCH QUERY (OPTIONAL)")
    OutlinedTextField(
        value = settings.searchQuery,
        onValueChange = { onSave(settings.copy(searchQuery = it)) },
        placeholder = { Text("e.g. landscape mountains") },
        modifier = Modifier.fillMaxWidth()
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
                label = { Text(purity.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    SectionLabel("ASPECT RATIO")
    AspectRatioField(
        value = settings.aspectRatio,
        onValueChange = { onSave(settings.copy(aspectRatio = it)) }
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
private fun AspectRatioField(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = ASPECT_RATIO_SUGGESTIONS.filter {
        it.startsWith(value, ignoreCase = true) && it != value
    }
    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            placeholder = { Text("e.g. 9x16") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleTab(settings: AppSettings, onSave: (AppSettings) -> Unit) {
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
        ExposedDropdownMenu(
            expanded = intervalExpanded,
            onDismissRequest = { intervalExpanded = false }
        ) {
            UPDATE_INTERVAL_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(formatInterval(minutes)) },
                    onClick = {
                        onSave(settings.copy(updateIntervalMinutes = minutes))
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = settings.wallpaperTarget == target,
                onClick = { onSave(settings.copy(wallpaperTarget = target)) }
            )
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
        label = "Unmetered Wi-Fi only",
        subtitle = "Skip updates on mobile data",
        checked = settings.unmeteredOnly,
        onToggle = { onSave(settings.copy(unmeteredOnly = it)) }
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
    SectionLabel("WALLPAPER POOL SIZE")
    Text(
        "Wallpapers kept on device and shown in gallery",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    POOL_SIZE_OPTIONS.forEach { size ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = settings.poolSize == size,
                onClick = { onSave(settings.copy(poolSize = size)) }
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
        value = settings.apiKey,
        onValueChange = { onSave(settings.copy(apiKey = it)) },
        placeholder = { Text("Your Wallhaven API key") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ToggleSetting(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

private fun formatInterval(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hr"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "$minutes min"
}
