package xyz.attacktive.wallhavend.ui.home

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import xyz.attacktive.wallhavend.BuildConfig
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.ServiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
	val state by viewModel.serviceState.collectAsStateWithLifecycle()
	val context = LocalContext.current
	var pendingSavePath by remember { mutableStateOf<String?>(null) }

	val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (granted) {
			pendingSavePath?.let { viewModel.saveToPictures(it) }
		}

		pendingSavePath = null
	}

	LaunchedEffect(Unit) {
		viewModel.saveMessage.collect { message ->
			Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
		}
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Wallhavend ${BuildConfig.VERSION_NAME}") },
				actions = {
					IconButton(onClick = onNavigateToSettings) {
						Icon(Icons.Default.Settings, contentDescription = "Settings")
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(16.dp)
		) {
			StatusCard(state = state)
			Spacer(modifier = Modifier.height(12.dp))
			QuickActions(
				state = state,
				onStartStop = {
					if (state.isRunning) {
						viewModel.stopService()
					} else {
						viewModel.startService()
					}
				},
				onUpdateNow = viewModel::updateNow
			)

			state.error?.let { error ->
				Spacer(modifier = Modifier.height(8.dp))
				ErrorBanner(error = error)
			}

			if (!state.isOnline) {
				Spacer(modifier = Modifier.height(8.dp))
				OfflineBanner()
			}

			Spacer(modifier = Modifier.height(16.dp))

			if (state.poolPaths.isNotEmpty()) {
				Text("RECENT WALLPAPERS", style = MaterialTheme.typography.labelSmall)
				Spacer(modifier = Modifier.height(8.dp))
				WallpaperGrid(
					paths = state.poolPaths,
					currentPath = state.currentWallpaperPath,
					onTap = viewModel::applyFromPool,
					onDelete = viewModel::deleteFromPool,
					onSave = { path ->
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							viewModel.saveToPictures(path)
						} else {
							pendingSavePath = path
							writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
						}
					}
				)
			}
		}
	}
}

@Composable
private fun StatusCard(state: ServiceState) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.padding(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = if (state.isRunning) "● AUTO-UPDATE ON" else "○ AUTO-UPDATE OFF",
					style = MaterialTheme.typography.labelMedium,
					color = if (state.isRunning) {
						MaterialTheme.colorScheme.primary
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					}
				)

				state.lastUpdatedMs?.let {
					Text(
						text = "Last: ${SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale).format(Date(it))}",
						style = MaterialTheme.typography.bodySmall
					)
				}
			}
		}
	}
}

@Composable
private fun QuickActions(state: ServiceState, onStartStop: () -> Unit, onUpdateNow: () -> Unit) {
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Button(onClick = onStartStop) {
			Icon(
				imageVector = if (state.isRunning) {
					Icons.Default.Stop
				} else {
					Icons.Default.PlayArrow
				},
				contentDescription = null
			)

			Spacer(Modifier.width(4.dp))
			Text(if (state.isRunning) "Stop" else "Start")
		}

		OutlinedButton(onClick = onUpdateNow) {
			Icon(Icons.Default.CloudDownload, contentDescription = null)
			Spacer(Modifier.width(4.dp))
			Text("Download now")
		}
	}
}

@Composable
private fun WallpaperGrid(paths: List<String>, currentPath: String?, onTap: (String) -> Unit, onDelete: (String) -> Unit, onSave: (String) -> Unit) {
	LazyVerticalGrid(
		columns = GridCells.Fixed(3),
		contentPadding = PaddingValues(vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		items(paths) { path ->
			val isCurrent = path == currentPath

			Box(
				modifier = Modifier
					.aspectRatio(9f / 16f)
					.clip(RoundedCornerShape(4.dp))
					.then(
						if (isCurrent) {
							Modifier.border(
								BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
								RoundedCornerShape(4.dp)
							)
						} else {
							Modifier
						}
					)
					.clickable { onTap(path) }
			) {
				AsyncImage(
					model = File(path),
					contentDescription = null,
					contentScale = ContentScale.Crop,
					modifier = Modifier.fillMaxSize()
				)

				IconButton(
					onClick = { onSave(path) },
					modifier = Modifier
						.align(Alignment.TopStart)
						.size(28.dp)
				) {
					Icon(
						Icons.Default.Save,
						contentDescription = "Save to Pictures",
						modifier = Modifier.size(16.dp),
						tint = Color.White
					)
				}

				IconButton(
					onClick = { onDelete(path) },
					modifier = Modifier
						.align(Alignment.TopEnd)
						.size(28.dp)
				) {
					Icon(
						Icons.Default.Close,
						contentDescription = "Delete",
						modifier = Modifier.size(16.dp),
						tint = Color.White
					)
				}
			}
		}
	}
}

@Composable
private fun ErrorBanner(error: AppError) {
	val message = when (error) {
		is AppError.NoResults -> "No results — try relaxing your filters"
		is AppError.NoResultsWithRatioHint -> "No results — try clearing the aspect ratio, or remove the API key if you don't need NSFW wallpapers"
		is AppError.ApiError -> "API error ${error.code}"
		is AppError.UnsupportedFormat -> "Unsupported image format — skipping"
		is AppError.WallpaperApplyFailed -> "Failed to apply wallpaper: ${error.cause}"
		is AppError.NetworkError -> "Network error: ${error.cause}"
	}

	Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
		Text(
			text = "⚠ $message",
			modifier = Modifier.padding(12.dp),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.error
		)
	}
}

@Composable
private fun OfflineBanner() {
	Card(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = "Offline — updates paused",
			modifier = Modifier.padding(12.dp),
			style = MaterialTheme.typography.bodySmall
		)
	}
}
