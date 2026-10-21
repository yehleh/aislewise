package com.yehleh.aislewise.feature.scan

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.ui.components.SectionDropdown
import com.yehleh.aislewise.ui.components.StoreBadges
import com.yehleh.aislewise.ui.components.StoreChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, viewModel: ScanViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    var cameraMissing by rememberSaveable { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { viewModel.onPhotoTaken(it) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) {
        if (it.isNotEmpty()) viewModel.addPhotos(it)
    }
    val onTakePhoto = {
        try {
            takePicture.launch(viewModel.newCameraUri())
        } catch (e: ActivityNotFoundException) {
            cameraMissing = true
        }
    }
    val onPickPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    LaunchedEffect(state.phase) { if (state.phase == ScanPhase.Done) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.phase is ScanPhase.Review) "Check items" else "Scan a list") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.phase is ScanPhase.Review) TextButton(onClick = viewModel::backToPhotos) { Text("Photos") }
                },
            )
        },
        bottomBar = {
            val phase = state.phase
            if (phase is ScanPhase.Review) {
                val count = phase.rows.count { it.include && it.name.isNotBlank() }
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = viewModel::addToList,
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text(if (count == 1) "Add 1 item" else "Add $count items") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.configured -> Message(
                    title = "Photo reading isn't set up",
                    body = "Add GEMINI_API_KEY to local.properties (get a free key at aistudio.google.com/apikey), then rebuild the app.",
                )
                else -> when (val phase = state.phase) {
                    ScanPhase.Collecting, ScanPhase.Done -> CollectPhotos(
                        photos = state.photos,
                        onTakePhoto = onTakePhoto,
                        onPickPhotos = onPickPhotos,
                        onRemove = viewModel::removePhoto,
                        onRead = viewModel::read,
                        cameraMissing = cameraMissing,
                    )
                    ScanPhase.Reading -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Reading your list…", style = MaterialTheme.typography.titleMedium)
                    }
                    is ScanPhase.Failed -> Message(title = "Couldn't read the list", body = phase.message) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = viewModel::backToPhotos) { Text("Change photos") }
                            Button(onClick = viewModel::read) { Text("Try again") }
                        }
                    }
                    is ScanPhase.Review -> ReviewList(
                        rows = phase.rows,
                        stores = stores,
                        sections = sections,
                        onUpdate = viewModel::updateRow,
                        onToggleStoreForNew = viewModel::toggleStoreForNewItems,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectPhotos(
    photos: List<Uri>,
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onRemove: (Uri) -> Unit,
    onRead: () -> Unit,
    cameraMissing: Boolean,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Take or choose photos of a written shopping list. Use several photos for a long list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTakePhoto, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Take photo")
            }
            OutlinedButton(onClick = onPickPhotos, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose")
            }
        }
        if (cameraMissing) {
            Text("No camera app found. Choose photos instead.", color = MaterialTheme.colorScheme.error)
        }
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.toString() }) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = "List photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(120.dp, 160.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        IconButton(
                            onClick = { onRemove(uri) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
                        ) { Icon(Icons.Default.Close, "Remove photo", Modifier.size(18.dp)) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onRead, enabled = photos.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(
                when (photos.size) {
                    0 -> "Read list"
                    1 -> "Read list from 1 photo"
                    else -> "Read list from ${photos.size} photos"
                },
            )
        }
    }
}

@Composable
private fun ReviewList(
    rows: List<ReviewRow>,
    stores: List<Store>,
    sections: List<Section>,
    onUpdate: (Int, (ReviewRow) -> ReviewRow) -> Unit,
    onToggleStoreForNew: (Long) -> Unit,
) {
    val newRows = rows.filter { !it.known && it.include }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val known = rows.count { it.known }
            Text(
                buildString {
                    append("Found ${rows.size} item${if (rows.size == 1) "" else "s"}.")
                    if (known > 0) append(" $known already ${if (known == 1) "has" else "have"} stores from last time.")
                    append(" Tap an item to change it.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (newRows.isNotEmpty() && stores.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Set stores for all ${newRows.size} new items", style = MaterialTheme.typography.labelLarge)
                        StoreChips(
                            stores = stores,
                            selected = stores.filter { s -> newRows.all { s.id in it.storeIds } }.map { it.id }.toSet(),
                            onToggle = onToggleStoreForNew,
                        )
                    }
                }
            }
        }
        items(rows, key = { it.key }) { row ->
            ReviewCard(row, stores, sections) { transform -> onUpdate(row.key, transform) }
        }
    }
}

@Composable
private fun ReviewCard(row: ReviewRow, stores: List<Store>, sections: List<Section>, onUpdate: ((ReviewRow) -> ReviewRow) -> Unit) {
    var expanded by rememberSaveable(row.key) { mutableStateOf(false) }
    val needsStores = !row.known && row.storeIds.isEmpty() && stores.isNotEmpty()
    Card(
        border = if (needsStores && row.include) BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (row.include) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.include, onCheckedChange = { checked -> onUpdate { it.copy(include = checked) } })
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    row.name.ifBlank { "(no name)" } + if (row.quantity.isNotBlank()) "  ·  ${row.quantity}" else "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (row.include) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val sectionName = sections.firstOrNull { it.id == row.sectionId }?.name ?: "No section"
                val rowStores = stores.filter { it.id in row.storeIds }
                Text(
                    when {
                        needsStores -> "$sectionName · New item, pick stores"
                        rowStores.isEmpty() -> "$sectionName · Any store"
                        else -> sectionName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (needsStores) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rowStores.isNotEmpty()) StoreBadges(rowStores, Modifier.padding(top = 4.dp))
            }
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Collapse" else "Edit")
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = row.name,
                        onValueChange = { v -> onUpdate { it.copy(name = v) } },
                        label = { Text("Item") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = row.quantity,
                        onValueChange = { v -> onUpdate { it.copy(quantity = v) } },
                        label = { Text("Qty") },
                        singleLine = true,
                        modifier = Modifier.width(88.dp),
                    )
                }
                SectionDropdown(sections, row.sectionId, { id -> onUpdate { it.copy(sectionId = id) } }, Modifier.fillMaxWidth())
                StoreChips(stores, row.storeIds, { id ->
                    onUpdate { it.copy(storeIds = if (id in it.storeIds) it.storeIds - id else it.storeIds + id) }
                })
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, actions: @Composable () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        actions()
    }
}

private const val MAX_PHOTOS = 10
