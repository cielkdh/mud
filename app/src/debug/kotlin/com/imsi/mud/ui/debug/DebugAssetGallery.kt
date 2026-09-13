package com.imsi.mud.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.imsi.mud.content.ResolvedAsset

/** Debug-only surface for verifying the P1 -> P22 image-state handoff. */
sealed interface DebugAssetGalleryState {
    data object Loading : DebugAssetGalleryState

    data class Content(
        val publicName: String,
        val description: String,
        val resolution: DebugAssetResolution,
    ) : DebugAssetGalleryState

    data class NoImage(
        val publicName: String,
        val description: String,
        val actionLabel: String,
    ) : DebugAssetGalleryState
}

enum class DebugAssetResolution {
    Exact,
    Fallback,
}

/** Keeps resolver diagnostics out of accessibility output while preserving the P22 handoff states. */
fun ResolvedAsset.toDebugGalleryState(
    publicName: String,
    description: String,
    noImageDescription: String,
    actionLabel: String,
): DebugAssetGalleryState = when (this) {
    is ResolvedAsset.Exact -> DebugAssetGalleryState.Content(
        publicName = publicName,
        description = description,
        resolution = DebugAssetResolution.Exact,
    )

    is ResolvedAsset.Fallback -> DebugAssetGalleryState.Content(
        publicName = publicName,
        description = description,
        resolution = DebugAssetResolution.Fallback,
    )

    is ResolvedAsset.SkippedByQualityMode,
    is ResolvedAsset.AssetUnavailable -> DebugAssetGalleryState.NoImage(
        publicName = publicName,
        description = noImageDescription,
        actionLabel = actionLabel,
    )
}

@Composable
fun DebugAssetGallery(
    state: DebugAssetGalleryState,
    modifier: Modifier = Modifier,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { isTraversalGroup = true },
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                DebugAssetGalleryState.Loading -> LoadingFixture()
                is DebugAssetGalleryState.Content -> ContentFixture(state)
                is DebugAssetGalleryState.NoImage -> NoImageFixture(state, onAction)
            }
        }
    }
}

@Composable
private fun LoadingFixture() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(48.dp)
                .clearAndSetSemantics { },
        )
    }
    Text(
        text = "이미지 불러오는 중",
        modifier = Modifier
            .testTag("asset-gallery-loading")
            .semantics {
                contentDescription = "이미지 불러오는 중"
                traversalIndex = 0f
            },
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ContentFixture(state: DebugAssetGalleryState.Content) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag("asset-gallery-image")
            .semantics {
                contentDescription = state.publicName
                traversalIndex = 0f
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (state.resolution) {
                DebugAssetResolution.Exact -> "정확한 자산"
                DebugAssetResolution.Fallback -> "승인된 대체 자산"
            },
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
    Text(
        text = state.publicName,
        modifier = Modifier.clearAndSetSemantics { },
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = state.description,
        modifier = Modifier.semantics { traversalIndex = 1f },
    )
}

@Composable
private fun NoImageFixture(
    state: DebugAssetGalleryState.NoImage,
    onAction: () -> Unit,
) {
    Text(
        text = state.publicName,
        modifier = Modifier.semantics { traversalIndex = 0f },
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = state.description,
        modifier = Modifier.semantics { traversalIndex = 1f },
    )
    Button(
        onClick = onAction,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { traversalIndex = 2f },
    ) {
        Text(state.actionLabel)
    }
}
