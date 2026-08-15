package xyz.attacktive.wallhavend.ui.blocklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import xyz.attacktive.wallhavend.R
import xyz.attacktive.wallhavend.domain.model.WallpaperIdentity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(onNavigateBack: () -> Unit, viewModel: BlocklistViewModel = hiltViewModel()) {
	val blockedWallpapers by viewModel.blockedWallpapers.collectAsStateWithLifecycle()

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(stringResource(R.string.blocklist_title)) },
				navigationIcon = {
					IconButton(onClick = onNavigateBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
					}
				}
			)
		}
	) { padding ->
		if (blockedWallpapers.isEmpty()) {
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.fillMaxSize()
					.padding(padding)
					.padding(32.dp)
			) {
				Text(
					text = stringResource(R.string.blocklist_empty),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			return@Scaffold
		}

		LazyColumn(modifier = Modifier
			.fillMaxSize()
			.padding(padding)
		) {
			items(blockedWallpapers, key = { it.qualified }) { identity ->
				BlockedRow(identity = identity, onUnblock = { viewModel.unblock(identity) })
			}
		}
	}
}

@Composable
private fun BlockedRow(identity: WallpaperIdentity, onUnblock: () -> Unit) {
	val uriHandler = LocalUriHandler.current

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		RevealableThumbnail(identity = identity)

		Column(
			modifier = Modifier
				.weight(1f)
				.padding(start = 16.dp)
		) {
			Text(
				text = identity.id,
				style = MaterialTheme.typography.bodyLarge,
				color = MaterialTheme.colorScheme.primary,
				textDecoration = TextDecoration.Underline,
				modifier = Modifier.clickable { uriHandler.openUri(identity.pageUrl) }
			)

			Text(
				text = stringResource(R.string.blocklist_open_hint),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		TextButton(onClick = onUnblock) {
			Text(stringResource(R.string.blocklist_unblock))
		}
	}
}

/**
 * Blocked wallpapers are hidden behind a placeholder so opening the list doesn't re-expose content the
 * user chose to be rid of. The thumbnail loads from the network only after an explicit tap.
 */
@Composable
private fun RevealableThumbnail(identity: WallpaperIdentity) {
	var revealed by remember(identity) { mutableStateOf(false) }

	Box(
		contentAlignment = Alignment.Center,
		modifier = Modifier
			.size(64.dp)
			.clip(RoundedCornerShape(6.dp))
			.background(MaterialTheme.colorScheme.surfaceVariant)
			.clickable { revealed = true }
	) {
		if (revealed) {
			AsyncImage(
				model = identity.thumbnailUrl,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize()
			)
		} else {
			Icon(
				imageVector = Icons.Filled.Visibility,
				contentDescription = stringResource(R.string.blocklist_reveal),
				tint = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}
