package com.yehleh.aislewise.feature.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehleh.aislewise.ui.components.StoreBadges

/** Every remembered item with its section and stores; tap one to change where it can be bought. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onEditItem: (Long) -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Items & stores") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                placeholder = { Text("Search items") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val list = items ?: return@Column
            if (list.isEmpty()) {
                Text(
                    if (query.isBlank()) "Items you add to your list are remembered here, along with the stores you picked."
                    else "No items match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        overlineContent = { Text(item.sectionName ?: "No section") },
                        supportingContent = {
                            if (item.stores.isEmpty()) {
                                Text("Any store", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                StoreBadges(item.stores, Modifier.padding(top = 4.dp))
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.addToList(item) }) {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add ${item.name} to list")
                            }
                        },
                        modifier = Modifier.clickable { onEditItem(item.id) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
