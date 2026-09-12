package xyz.attacktive.wallhavend.ui.picker

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WallpaperPickerScreen(wallpapers: List<File>, onSelect: (File) -> Unit, onCancel: () -> Unit, onOpenApp: () -> Unit) {
	var previewIndex by remember { mutableStateOf<Int?>(null) }

	val isPreviewing = previewIndex != null

	BackHandler(enabled = isPreviewing) {
		previewIndex = null
	}

	if (isPreviewing) {
		val initialPage = (previewIndex ?: 0).coerceIn(0, (wallpapers.size - 1).coerceAtLeast(0))
		val pagerState = rememberPagerState(initialPage = initialPage) { wallpapers.size }
		val currentFile = wallpapers.getOrNull(pagerState.currentPage)

		val resolution by produceState<String?>(initialValue = null, currentFile) {
			value = withContext(Dispatchers.IO) {
				if (currentFile == null) {
					return@withContext null
				}

				val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
				BitmapFactory.decodeFile(currentFile.absolutePath, options)

				if (options.outWidth > 0 && options.outHeight > 0) {
					"${options.outWidth}×${options.outHeight}"
				} else {
					null
				}
			}
		}

		val caption = if (currentFile != null) {
			val id = WallpaperIdentity.parse(currentFile.nameWithoutExtension).id
			if (resolution != null) {
				"$id ($resolution)"
			} else {
				id
			}
		} else {
			""
		}

		Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
			HorizontalPager(
				state = pagerState,
				key = { wallpapers[it].absolutePath },
				modifier = Modifier.fillMaxSize()
			) { page ->
				val file = wallpapers.getOrNull(page) ?: return@HorizontalPager
				AsyncImage(
					model = file,
					contentDescription = null,
					contentScale = ContentScale.Fit,
					modifier = Modifier.fillMaxSize()
				)
			}

			Row(
				modifier = Modifier
					.align(Alignment.TopCenter)
					.fillMaxWidth()
					.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
					.statusBarsPadding()
					.padding(end = 16.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(onClick = { previewIndex = null }) {
					Icon(
						Icons.AutoMirrored.Filled.ArrowBack,
						contentDescription = stringResource(R.string.cd_back),
						tint = Color.White
					)
				}

				Text(text = caption, color = Color.White, style = MaterialTheme.typography.titleSmall)
			}

			Box(
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth()
					.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
					.navigationBarsPadding()
					.padding(16.dp),
				contentAlignment = Alignment.Center
			) {
				Button(onClick = { currentFile?.let(onSelect) }) {
					Icon(Icons.Default.Check, contentDescription = null)
					Spacer(Modifier.width(8.dp))
					Text(stringResource(R.string.picker_action_select))
				}
			}
		}
	} else {
		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text(stringResource(R.string.picker_title)) },
					navigationIcon = {
						IconButton(onClick = onCancel) {
							Icon(
								Icons.AutoMirrored.Filled.ArrowBack,
								contentDescription = stringResource(R.string.cd_back)
							)
						}
					}
				)
			}
		) { padding ->
			if (wallpapers.isEmpty()) {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(padding)
						.padding(32.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.Center
				) {
					Icon(
						Icons.Default.Image,
						contentDescription = null,
						modifier = Modifier.size(64.dp),
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)

					Spacer(modifier = Modifier.height(16.dp))

					Text(
						text = stringResource(R.string.picker_empty_title),
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurface
					)

					Spacer(modifier = Modifier.height(8.dp))

					Text(
						text = stringResource(R.string.picker_empty_desc),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center
					)

					Spacer(modifier = Modifier.height(24.dp))

					Button(onClick = onOpenApp) {
						Text(stringResource(R.string.picker_open_app))
					}
				}
			} else {
				LazyVerticalGrid(
					columns = GridCells.Fixed(3),
					contentPadding = PaddingValues(4.dp),
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalArrangement = Arrangement.spacedBy(4.dp),
					modifier = Modifier
						.fillMaxSize()
						.padding(padding)
				) {
					itemsIndexed(wallpapers) { index, file ->
						val wallpaperId = WallpaperIdentity.parse(file.nameWithoutExtension).id
						val wallpaperDescription = stringResource(R.string.picker_wallpaper_description, wallpaperId)

						Box(
							modifier = Modifier
								.aspectRatio(9f / 16f)
								.clip(RoundedCornerShape(4.dp))
								.combinedClickable(
									onClickLabel = stringResource(R.string.picker_select_wallpaper),
									onLongClickLabel = stringResource(R.string.picker_preview_wallpaper),
									onClick = { onSelect(file) },
									onLongClick = { previewIndex = index }
								)
								.semantics(mergeDescendants = true) {
									contentDescription = wallpaperDescription
								}
						) {
							AsyncImage(
								model = file,
								contentDescription = null,
								contentScale = ContentScale.Crop,
								modifier = Modifier.fillMaxSize()
							)
						}
					}
				}
			}
		}
	}
}
