package xyz.attacktive.wallhavend.ui.home

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import xyz.attacktive.wallhavend.BuildConfig
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.AppError
import xyz.attacktive.wallhavend.domain.model.RotationMode
import xyz.attacktive.wallhavend.domain.model.ServiceState
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToSettings: () -> Unit, onNavigateToPreview: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
	val state by viewModel.serviceState.collectAsStateWithLifecycle()
	val pinnedIds by viewModel.pinnedIds.collectAsStateWithLifecycle()
	val rotationMode by viewModel.rotationMode.collectAsStateWithLifecycle()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.home_title, BuildConfig.VERSION_NAME)) },
				actions = {
					IconButton(onClick = onNavigateToSettings) {
						Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings))
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

			val pinnedRotationIdle = state.isRunning && rotationMode == RotationMode.PINNED_ONLY && pinnedIds.isEmpty()

			if (pinnedRotationIdle) {
				Spacer(modifier = Modifier.height(8.dp))
				PinnedIdleBanner()
			}

			Spacer(modifier = Modifier.height(16.dp))

			if (state.poolPaths.isNotEmpty()) {
				Text(stringResource(R.string.home_recent_wallpapers), style = MaterialTheme.typography.labelMedium)
				Spacer(modifier = Modifier.height(8.dp))

				WallpaperGrid(
					paths = state.poolPaths,
					currentPath = state.currentWallpaperPath,
					pinnedIds = pinnedIds,
					onTap = { path -> onNavigateToPreview(File(path).nameWithoutExtension) }
				)
			}
		}
	}
}

@Composable
private fun StatusCard(state: ServiceState) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = stringResource(if (state.isRunning) R.string.home_status_auto_update_on else R.string.home_status_auto_update_off),
					style = MaterialTheme.typography.labelMedium,
					color = if (state.isRunning) {
						MaterialTheme.colorScheme.primary
					} else {
						MaterialTheme.colorScheme.onSurfaceVariant
					}
				)

				state.lastUpdatedMs?.let { timestamp ->
					val formattedTime = SimpleDateFormat("HH:mm", LocalLocale.current.platformLocale)
						.format(Date(timestamp))

					Text(
						text = stringResource(R.string.home_status_last_updated, formattedTime),
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
			Text(stringResource(if (state.isRunning) R.string.home_action_stop else R.string.home_action_start))
		}

		OutlinedButton(onClick = onUpdateNow) {
			Icon(Icons.Default.CloudDownload, contentDescription = null)
			Spacer(Modifier.width(4.dp))
			Text(stringResource(R.string.home_action_download_now))
		}
	}
}

@Composable
private fun WallpaperGrid(paths: List<String>, currentPath: String?, pinnedIds: Set<String>, onTap: (String) -> Unit) {
	LazyVerticalGrid(
		columns = GridCells.Fixed(3),
		contentPadding = PaddingValues(vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		verticalArrangement = Arrangement.spacedBy(4.dp)
	) {
		items(paths) { path ->
			val isCurrent = path == currentPath
			val isPinned = WallpaperIdentity.parse(File(path).nameWithoutExtension)
				.matches(pinnedIds)

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

				if (isPinned) {
					Box(
						contentAlignment = Alignment.Center,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(4.dp)
							.size(18.dp)
							.clip(CircleShape)
							.background(MaterialTheme.colorScheme.primary)
					) {
						Icon(
							Icons.Default.PushPin,
							contentDescription = stringResource(R.string.cd_pinned),
							tint = MaterialTheme.colorScheme.onPrimary,
							modifier = Modifier.size(12.dp)
						)
					}
				}
			}
		}
	}
}

@Composable
private fun ErrorBanner(error: AppError) {
	val text = when (error) {
		is AppError.NoResults -> stringResource(R.string.error_no_results)
		is AppError.NoResultsWithRatioHint -> stringResource(R.string.error_no_results_hint)
		is AppError.ApiError -> stringResource(R.string.error_api_error, error.code)
		is AppError.UnsupportedFormat -> stringResource(R.string.error_unsupported_format)
		is AppError.WallpaperApplyFailed -> stringResource(R.string.error_apply_failed, error.cause)
		is AppError.NetworkError -> stringResource(R.string.error_network_error, error.cause)
	}

	Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
		Text(
			text = text,
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
			text = stringResource(R.string.home_offline_banner),
			modifier = Modifier.padding(12.dp),
			style = MaterialTheme.typography.bodySmall
		)
	}
}

@Composable
private fun PinnedIdleBanner() {
	Card(modifier = Modifier.fillMaxWidth()) {
		Text(
			text = stringResource(R.string.home_pinned_idle_banner),
			modifier = Modifier.padding(12.dp),
			style = MaterialTheme.typography.bodySmall
		)
	}
}
