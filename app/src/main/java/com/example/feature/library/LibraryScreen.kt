package com.example.feature.library

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.data.UiState
import com.example.core.designsystem.QltyChip
import com.example.core.designsystem.QltyColors
import com.example.core.designsystem.QltyEmptyState
import com.example.core.designsystem.QltyIconButton
import com.example.core.designsystem.QltyInput
import com.example.core.designsystem.QltyRadius
import com.example.core.designsystem.QltySizes
import com.example.core.designsystem.QltySkeletonBox
import com.example.core.designsystem.QltySpace
import com.example.core.designsystem.QltyTopBar
import com.example.core.designsystem.QltyType
import com.example.core.domain.ExportFormat
import com.example.core.domain.LibraryAsset
import com.example.core.image.MediaExportManager
import java.io.File

private enum class LibraryFilter(val label: String) {
    ALL("All"),
    ENHANCED("Enhanced"),
    UPSCALED("Upscaled"),
    SAVED("Saved")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    libraryState: UiState<List<LibraryAsset>>,
    onImportPhoto: () -> Unit,
    onOpenAsset: (LibraryAsset) -> Unit,
    onToggleFavorite: (LibraryAsset) -> Unit,
    onExportSelected: (List<LibraryAsset>) -> Unit,
    onDeleteSelected: (List<LibraryAsset>) -> Unit,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val inSelectionMode = selectedIds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(QltyColors.Bg)
    ) {
        if (inSelectionMode) {
            val allAssets = (libraryState as? UiState.Success)?.data.orEmpty()
            val selectedAssets = allAssets.filter { it.id in selectedIds }

            QltyTopBar(
                title = "${selectedIds.size} selected",
                navigationIcon = {
                    QltyIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Clear selection",
                        onClick = { selectedIds = emptySet() },
                        testTag = "library_clear_selection"
                    )
                },
                actions = {
                    QltyIconButton(
                        icon = Icons.Rounded.Download,
                        contentDescription = "Export selected to gallery",
                        onClick = {
                            onExportSelected(selectedAssets)
                            selectedIds = emptySet()
                        },
                        testTag = "library_batch_export"
                    )
                    QltyIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Delete selected",
                        tint = QltyColors.Danger,
                        onClick = {
                            onDeleteSelected(selectedAssets)
                            selectedIds = emptySet()
                        },
                        testTag = "library_batch_delete"
                    )
                }
            )
        } else {
            QltyTopBar(
                title = "My Library",
                subtitle = "Your enhanced photos",
                actions = {
                    QltyIconButton(
                        icon = if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = if (showSearch) "Close search" else "Search library",
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) searchQuery = ""
                        },
                        testTag = "library_search_button"
                    )
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = QltySpace.S16),
            verticalArrangement = Arrangement.spacedBy(QltySpace.S16)
        ) {
            if (showSearch) {
                QltyInput(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "",
                    placeholder = "Search by name, preset, or resolution",
                    leadingIcon = Icons.Rounded.Search,
                    testTag = "library_search_input"
                )
            }

            // Category Chips: All, Enhanced, Upscaled, Saved
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(QltySpace.S8)
            ) {
                LibraryFilter.entries.forEach { filter ->
                    QltyChip(
                        label = filter.label,
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        testTag = "library_chip_${filter.label}"
                    )
                }
            }

            when (libraryState) {
                is UiState.Loading -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(QltySpace.S12),
                        verticalArrangement = Arrangement.spacedBy(QltySpace.S12),
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + QltySpace.S24),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(4) {
                            QltySkeletonBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.9f)
                            )
                        }
                    }
                }
                is UiState.Error -> {
                    QltyEmptyState(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = "Unable to Load Library",
                        description = libraryState.message
                    )
                }
                is UiState.Success -> {
                    val filtered = remember(libraryState.data, activeFilter, searchQuery) {
                        libraryState.data.filter { item ->
                            val matchesFilter = when (activeFilter) {
                                LibraryFilter.ALL -> true
                                LibraryFilter.ENHANCED -> item.resolutionLabel == "Original" || item.mode == "cloud"
                                LibraryFilter.UPSCALED -> item.resolutionLabel == "4K" || item.resolutionLabel == "2x"
                                LibraryFilter.SAVED -> item.isFavorite || item.isSavedToGallery
                            }
                            val q = searchQuery.trim().lowercase()
                            val matchesQuery = q.isEmpty() ||
                                item.displayName.lowercase().contains(q) ||
                                item.resolutionLabel.lowercase().contains(q) ||
                                item.presetUsed.lowercase().contains(q)
                            matchesFilter && matchesQuery
                        }
                    }

                    if (filtered.isEmpty()) {
                        QltyEmptyState(
                            icon = Icons.Rounded.PhotoLibrary,
                            title = if (libraryState.data.isEmpty()) "No Enhanced Photos Yet" else "No Matching Photos",
                            description = if (libraryState.data.isEmpty()) {
                                "Photos you enhance locally or via Cloud AI appear here for comparison, sharing, and export."
                            } else {
                                "Try clearing your search or switching category filters."
                            },
                            actionLabel = if (libraryState.data.isEmpty()) "Import Photo" else null,
                            onAction = if (libraryState.data.isEmpty()) onImportPhoto else null
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(QltySpace.S12),
                            verticalArrangement = Arrangement.spacedBy(QltySpace.S12),
                            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + QltySpace.S24),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id.ifBlank { it.jobId } }) { asset ->
                                val isSelected = asset.id in selectedIds
                                val imgFile = File(asset.thumbnailPath.ifBlank { asset.localOutputPath })

                                Surface(
                                    shape = QltyRadius.CardShape,
                                    color = QltyColors.Surface,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else QltySizes.Hairline,
                                        color = if (isSelected) QltyColors.PrimaryHighlight else QltyColors.Border
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.92f)
                                        .clip(QltyRadius.CardShape)
                                        .combinedClickable(
                                            onClick = {
                                                if (inSelectionMode) {
                                                    selectedIds = if (isSelected) {
                                                        selectedIds - asset.id
                                                    } else {
                                                        selectedIds + asset.id
                                                    }
                                                } else {
                                                    onOpenAsset(asset)
                                                }
                                            },
                                            onLongClick = {
                                                if (asset.id.isNotBlank()) {
                                                    selectedIds = selectedIds + asset.id
                                                }
                                            }
                                        )
                                        .testTag("library_card_${asset.id}")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (imgFile.exists()) {
                                            AsyncImage(
                                                model = imgFile,
                                                contentDescription = asset.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PhotoLibrary,
                                                    contentDescription = null,
                                                    tint = QltyColors.TextMuted
                                                )
                                            }
                                        }

                                        // Top-right favorite / multi-select badge
                                        if (inSelectionMode) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = if (isSelected) "Selected" else "Not selected",
                                                tint = if (isSelected) QltyColors.PrimaryHighlight else QltyColors.TextMuted,
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(QltySpace.S8)
                                                    .size(QltySizes.IconDefault)
                                            )
                                        } else {
                                            IconButton(
                                                onClick = { onToggleFavorite(asset) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(QltySizes.TouchTarget)
                                            ) {
                                                Icon(
                                                    imageVector = if (asset.isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                                    contentDescription = if (asset.isFavorite) "Remove bookmark" else "Bookmark photo",
                                                    tint = if (asset.isFavorite) QltyColors.PrimaryHighlight else QltyColors.OnOverlay,
                                                    modifier = Modifier
                                                        .background(QltyColors.OverlayDark, QltyRadius.PillShape)
                                                        .padding(QltySpace.S4)
                                                        .size(QltySizes.IconChip)
                                                )
                                            }
                                        }

                                        // Bottom metadata pill
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(QltyColors.OverlayDark)
                                                .padding(horizontal = QltySpace.S8, vertical = QltySpace.S8),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = asset.displayName,
                                                style = QltyType.CaptionMedium,
                                                color = QltyColors.OnOverlay,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = asset.resolutionLabel,
                                                style = QltyType.Caption,
                                                color = QltyColors.PrimaryHighlight,
                                                modifier = Modifier.padding(start = QltySpace.S4)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
