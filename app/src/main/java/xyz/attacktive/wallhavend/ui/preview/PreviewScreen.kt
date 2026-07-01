package xyz.attacktive.wallhavend.ui.preview

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.PushPin as PushPinOutlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.ui.home.HomeViewModel

@Composable
fun PreviewScreen(id: String, onNavigateBack: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
	val state by viewModel.serviceState.collectAsStateWithLifecycle()
	val pinnedIds by viewModel.pinnedIds.collectAsStateWithLifecycle()
	val context = LocalContext.current

	/*
	 * An empty pool means either it is still loading (empty on the first frame) or the last
	 * wallpaper was just deleted. Show black and wait; a destructive action navigates back itself
	 * when it empties the pool, so we never bounce out before the state arrives.
	 */
	if (state.poolPaths.isEmpty()) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black)
		)

		return
	}

	// Seed the pager at the tapped wallpaper once, when the pool is first available.
	val initialPage = remember {
		state.poolPaths.indexOfFirst { File(it).nameWithoutExtension == id }
	}

	// The tapped id is gone from a loaded pool (e.g. a stale deep-link); leave for the grid.
	if (initialPage < 0) {
		LaunchedEffect(Unit) { onNavigateBack() }

		Box(
			modifier = Modifier
				.fillMaxSize()
				.background(Color.Black)
		)

		return
	}

	val pagerState = rememberPagerState(initialPage = initialPage) { state.poolPaths.size }
	val currentPage = pagerState.currentPage.coerceIn(0, state.poolPaths.lastIndex)
	val currentPath = state.poolPaths[currentPage]
	val currentId = File(currentPath).nameWithoutExtension

	val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (granted) {
			viewModel.saveToPictures(currentPath)
		}
	}

	LaunchedEffect(Unit) {
		viewModel.saveMessage.collect { message ->
			Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
		}
	}

	var controlsVisible by remember { mutableStateOf(true) }

	val resolution by produceState<String?>(initialValue = null, currentPath) {
		value = withContext(Dispatchers.IO) {
			val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			BitmapFactory.decodeFile(currentPath, options)

			if (options.outWidth > 0 && options.outHeight > 0) {
				"${options.outWidth}×${options.outHeight}"
			} else {
				null
			}
		}
	}

	val caption = if (resolution != null) {
		"$currentId ($resolution)"
	} else {
		currentId
	}

	val scope = rememberCoroutineScope()

	/** Slides to a neighbor before evicting so the removed wallpaper animates away instead of popping. */
	fun evictAndAdvance(evict: () -> Unit) {
		if (state.poolPaths.size == 1) {
			evict()
			onNavigateBack()
			return
		}

		val target = if (currentPage < state.poolPaths.lastIndex) {
			currentPage + 1
		} else {
			currentPage - 1
		}

		scope.launch {
			pagerState.animateScrollToPage(target)
			evict()
		}
	}

	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(Color.Black)
			.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
				controlsVisible = !controlsVisible
			}
	) {
		HorizontalPager(state = pagerState, key = { state.poolPaths[it] }, modifier = Modifier.fillMaxSize()) { page ->
			val pagePath = state.poolPaths.getOrNull(page) ?: return@HorizontalPager

			AsyncImage(
				model = File(pagePath),
				contentDescription = null,
				contentScale = ContentScale.Fit,
				modifier = Modifier.fillMaxSize()
			)
		}

		AnimatedVisibility(
			visible = controlsVisible,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier
				.align(Alignment.TopCenter)
				.fillMaxWidth()
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)))
					.statusBarsPadding()
					.padding(end = 16.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(onClick = onNavigateBack) {
					Icon(
						Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = stringResource(R.string.cd_back),
						tint = Color.White
					)
				}

				Text(text = caption, color = Color.White, style = MaterialTheme.typography.titleSmall)
			}
		}

		AnimatedVisibility(
			visible = controlsVisible,
			enter = fadeIn() + slideInVertically { it },
			exit = fadeOut() + slideOutVertically { it },
			modifier = Modifier.align(Alignment.BottomCenter)
		) {
			Row(
				modifier = Modifier
					.fillMaxWidth()
					.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
					.navigationBarsPadding()
					.padding(top = 24.dp, bottom = 12.dp),
				horizontalArrangement = Arrangement.SpaceEvenly
			) {
				PreviewAction(
					icon = Icons.Default.Wallpaper,
					label = stringResource(R.string.preview_action_apply),
					onClick = {
						viewModel.applyFromPool(currentPath)
						goHome(context)
					}
				)

				val isPinned = currentId in pinnedIds

				PreviewAction(
					icon = if (isPinned) {
						Icons.Default.PushPin
					} else {
						Icons.Outlined.PushPinOutlined
					},
					label = stringResource(if (isPinned) { R.string.preview_action_unpin } else { R.string.preview_action_pin }),
					onClick = { viewModel.togglePin(currentId) }
				)

				PreviewAction(
					icon = Icons.Default.Download,
					label = stringResource(R.string.preview_action_save),
					onClick = {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							viewModel.saveToPictures(currentPath)
						} else {
							writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
						}
					}
				)

				PreviewAction(
					icon = Icons.Default.Delete,
					label = stringResource(R.string.preview_action_remove),
					onClick = { evictAndAdvance { viewModel.deleteFromPool(currentPath) } }
				)

				PreviewAction(
					icon = Icons.Default.Block,
					label = stringResource(R.string.preview_action_block),
					onClick = { evictAndAdvance { viewModel.blockFromPool(currentId, currentPath) } }
				)

				PreviewAction(
					icon = Icons.Default.OpenInBrowser,
					label = stringResource(R.string.preview_action_open_in_browser),
					onClick = {
						val intent = Intent(Intent.ACTION_VIEW, "https://wallhaven.cc/w/$currentId".toUri())
						context.startActivity(intent)
					}
				)
			}
		}
	}
}

@Composable
private fun PreviewAction(icon: ImageVector, label: String, onClick: () -> Unit) {
	Column(
		modifier = Modifier
			.clickable(onClick = onClick)
			.padding(horizontal = 12.dp, vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
		Text(text = label, color = Color.White, style = MaterialTheme.typography.labelSmall)
	}
}

/** Launches the home screen so the freshly-applied wallpaper is visible immediately. */
private fun goHome(context: Context) {
	val intent = Intent(Intent.ACTION_MAIN)
		.addCategory(Intent.CATEGORY_HOME)
		.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

	context.startActivity(intent)
}
