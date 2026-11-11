package com.yehleh.aislewise.feature.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.price.PriceParser
import com.yehleh.aislewise.data.repo.ListItemUi
import com.yehleh.aislewise.feature.add.AddItemSheet
import com.yehleh.aislewise.feature.settings.LocalShowPrices
import com.yehleh.aislewise.ui.components.BestPriceLine
import com.yehleh.aislewise.ui.components.StoreBadges
import com.yehleh.aislewise.ui.components.StoreDot
import com.yehleh.aislewise.ui.components.StorePriceLine
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(
    onScan: () -> Unit,
    onEditItem: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: ListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var quickEditing by remember { mutableStateOf<ListItemUi?>(null) }

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
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                menuOpen = false
                                onSettings()
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
                                selectedStoreId = state.selectedStoreId,
                                onToggle = { viewModel.toggle(item.entry) },
                                onEditDetails = { quickEditing = item },
                                onEditItem = { onEditItem(item.entry.itemId) },
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

    quickEditing?.let { item ->
        ItemDetailsDialog(
            item = item,
            showPrices = LocalShowPrices.current,
            onDismiss = { quickEditing = null },
            onSave = { note, prices ->
                viewModel.saveDetails(item, note, prices)
                quickEditing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    item: ListItemUi,
    selectedStoreId: Long?,
    onToggle: () -> Unit,
    onEditDetails: () -> Unit,
    onEditItem: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showPrices = LocalShowPrices.current
    // The swipe state is remembered across recompositions, so read the latest callback, not the first one.
    val currentOnRemove by rememberUpdatedState(onRemove)
    var rowWidth by remember { mutableFloatStateOf(0f) }
    lateinit var dismissState: SwipeToDismissBoxState
    dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.Settled) return@rememberSwipeToDismissBoxState true
            // A fast flick can request a dismiss after only a short drag; require a deliberate swipe.
            val dragged = abs(dismissState.requireOffset())
            val deliberate = rowWidth > 0f && dragged >= rowWidth * SWIPE_DELETE_FRACTION
            if (deliberate) currentOnRemove()
            deliberate
        },
        positionalThreshold = { totalDistance -> totalDistance * SWIPE_DELETE_FRACTION },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.onSizeChanged { rowWidth = it.width.toFloat() },
        enableDismissFromStartToEnd = false,
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
                .combinedClickable(onClick = onToggle, onLongClick = onEditItem)
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
                entry.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (entry.checked) TextDecoration.LineThrough else null,
                    )
                }
                when {
                    item.stores.isEmpty() ->
                        Text("Any store", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    selectedStoreId == null ->
                        StoreBadges(item.stores, Modifier.padding(top = 2.dp))
                    else -> {
                        val otherStores = item.stores.filter { it.id != selectedStoreId }
                        if (otherStores.isNotEmpty()) {
                            Row(
                                Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    "Also at",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                StoreBadges(otherStores)
                            }
                        }
                    }
                }
                if (showPrices) {
                    if (selectedStoreId != null) {
                        item.prices[selectedStoreId]?.let { StorePriceLine(it, Modifier.padding(top = 2.dp)) }
                    } else {
                        BestPriceLine(item.stores, item.prices, Modifier.padding(top = 2.dp))
                    }
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
            IconButton(onClick = onEditDetails) {
                val hasDetails = !entry.note.isNullOrBlank() || (showPrices && item.prices.isNotEmpty())
                Icon(
                    Icons.Default.Edit,
                    contentDescription = if (hasDetails) "Edit details for ${entry.name}" else "Add note or price",
                    tint = if (hasDetails) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How far (as a share of the row width) an item must be dragged before letting go deletes it. */
private const val SWIPE_DELETE_FRACTION = 0.4f

@Composable
private fun ItemDetailsDialog(
    item: ListItemUi,
    showPrices: Boolean,
    onDismiss: () -> Unit,
    onSave: (String?, Map<Long, Long?>) -> Unit,
) {
    val entry = item.entry
    var note by rememberSaveable(entry.id) { mutableStateOf(entry.note.orEmpty()) }
    var prices by remember(entry.id) {
        mutableStateOf(item.prices.mapValues { (_, price) -> PriceParser.toEditable(price.cents) })
    }
    var invalidPrice by remember { mutableStateOf(false) }
    val showPriceFields = showPrices && item.stores.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${entry.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    placeholder = { Text("e.g. Get the large box") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showPriceFields) {
                    Text("Prices", style = MaterialTheme.typography.labelLarge)
                    item.stores.forEach { store ->
                        OutlinedTextField(
                            value = prices[store.id].orEmpty(),
                            onValueChange = {
                                prices = prices + (store.id to it)
                                invalidPrice = false
                            },
                            label = { Text("Price at ${store.name}") },
                            placeholder = { Text("3.49") },
                            singleLine = true,
                            isError = invalidPrice,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val typed = if (showPriceFields) {
                    item.stores.associate { it.id to prices[it.id].orEmpty() }
                } else {
                    emptyMap()
                }
                if (typed.values.any { it.isNotBlank() && PriceParser.parseToCents(it) == null }) {
                    invalidPrice = true
                } else {
                    onSave(note, typed.mapValues { (_, text) -> PriceParser.parseToCents(text) })
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
