package com.yehleh.aislewise.feature.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.repo.ListItemUi
import com.yehleh.aislewise.feature.add.AddItemSheet
import com.yehleh.aislewise.ui.components.StoreBadges
import com.yehleh.aislewise.ui.components.StoreDot
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(onScan: () -> Unit, viewModel: ListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun removeWithUndo(entry: ListEntryRow) {
        viewModel.remove(entry)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Removed ${entry.name}", actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(entry)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AisleWise") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear checked items") },
                            enabled = state.hasChecked,
                            onClick = {
                                menuOpen = false
                                viewModel.clearChecked()
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = onScan) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan a written list")
                }
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Add item") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.stores.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedStoreId == null,
                            onClick = { viewModel.selectStore(null) },
                            label = { Text("All (${state.totalOpen})") },
                        )
                    }
                    items(state.stores, key = { it.id }) { store ->
                        FilterChip(
                            selected = state.selectedStoreId == store.id,
                            onClick = { viewModel.selectStore(store.id) },
                            label = { Text("${store.name} (${state.counts[store.id] ?: 0})") },
                            leadingIcon = { StoreDot(store) },
                        )
                    }
                }
            }

            if (!state.loading && state.groups.isEmpty()) {
                EmptyList(
                    hasStores = state.stores.isNotEmpty(),
                    filtered = state.selectedStoreId != null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
                    state.groups.forEach { group ->
                        stickyHeader(key = "section-${group.section?.id}") {
                            Text(
                                group.section?.name ?: "Unsorted",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(group.items, key = { it.entry.id }) { item ->
                            ListRow(
                                item = item,
                                showStores = state.selectedStoreId == null,
                                onToggle = { viewModel.toggle(item.entry) },
                                onRemove = { removeWithUndo(item.entry) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddItemSheet(onDismiss = { showAdd = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListRow(
    item: ListItemUi,
    showStores: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it != SwipeToDismissBoxValue.Settled) onRemove()
            it != SwipeToDismissBoxValue.Settled
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.errorContainer,
                label = "swipe",
            )
            Box(Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        val entry = item.entry
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = entry.checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                val dim = if (entry.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = dim,
                    textDecoration = if (entry.checked) TextDecoration.LineThrough else null,
                )
                when {
                    item.stores.isEmpty() ->
                        Text("Any store", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    showStores -> StoreBadges(item.stores, Modifier.padding(top = 2.dp))
                }
            }
            if (!entry.quantity.isNullOrBlank()) {
                Text(
                    entry.quantity,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyList(hasStores: Boolean, filtered: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (filtered) "Nothing to buy at this store" else "Your list is empty",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasStores) "Tap Add item, or the camera to scan a written list."
            else "Start by adding the stores you shop at on the Stores tab, then add items here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
