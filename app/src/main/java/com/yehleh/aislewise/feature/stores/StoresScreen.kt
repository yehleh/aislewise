package com.yehleh.aislewise.feature.stores

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehleh.aislewise.data.StorePalette
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.ui.components.ColorSwatches
import com.yehleh.aislewise.ui.components.StoreBadge
import com.yehleh.aislewise.ui.components.StoreDot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoresScreen(onEditSectionOrder: (Long) -> Unit, viewModel: StoresViewModel = hiltViewModel()) {
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    // null = closed; Store(id = 0) = adding a new store.
    var editing by remember { mutableStateOf<Store?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Stores") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Store(name = "") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add store") },
            )
        },
    ) { padding ->
        val list = stores ?: return@Scaffold
        if (list.isEmpty()) {
            Text(
                "Add the stores you shop at (or store types, like \"Pharmacy\"). Then tag items with where they can be bought.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(list, key = { it.id }) { store ->
                ListItem(
                    leadingContent = { StoreDot(store, size = 24.dp) },
                    headlineContent = { Text(store.name) },
                    supportingContent = store.type?.let { { Text(it) } },
                    trailingContent = {
                        IconButton(onClick = { onEditSectionOrder(store.id) }) {
                            Icon(Icons.Default.SwapVert, contentDescription = "Section order for ${store.name}")
                        }
                    },
                    modifier = Modifier.clickable { editing = store },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { store ->
        StoreDialog(
            store = store,
            onDismiss = { editing = null },
            onSave = { name, type, color ->
                viewModel.save(store.takeIf { it.id != 0L }, name, type, color)
                editing = null
            },
            onDelete = if (store.id != 0L) {
                {
                    viewModel.delete(store)
                    editing = null
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun StoreDialog(store: Store, onDismiss: () -> Unit, onSave: (String, String, Int?) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(store.name) }
    var type by rememberSaveable { mutableStateOf(store.type.orEmpty()) }
    // New stores start unset so the repository can pick the next unused color.
    var color by rememberSaveable { mutableStateOf(if (store.id == 0L) null else StorePalette.colorOf(store)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${store.name}?") },
            text = { Text("Items tagged with this store will lose that tag.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (store.id == 0L) "Add store" else "Edit store") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
                )
                LaunchedEffect(Unit) { nameFocus.requestFocus() }
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Type (optional)") },
                    placeholder = { Text("e.g. Grocery, Pharmacy") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Color", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ColorSwatches(selected = color ?: 0, onSelect = { color = it })
                if (name.isNotBlank()) {
                    val preview = store.copy(name = name.trim(), color = color ?: StorePalette.COLORS.first())
                    if (color != null) StoreBadge(preview)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, type, color) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
