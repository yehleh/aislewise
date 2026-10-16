package com.yehleh.aislewise.feature.stores

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Lets the user match a store's aisle layout, so the list for that store reads in walking order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionOrderScreen(onBack: () -> Unit, viewModel: SectionOrderViewModel = hiltViewModel()) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val storeName by viewModel.storeName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (storeName.isEmpty()) "Section order" else "$storeName aisles") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { viewModel.reset() }) { Text("Reset") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Order sections the way you walk through this store.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                itemsIndexed(sections, key = { _, s -> s.id }) { index, section ->
                    ListItem(
                        headlineContent = { Text(section.name) },
                        leadingContent = { Text("${index + 1}", style = MaterialTheme.typography.labelLarge) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Move ${section.name} up")
                                }
                                IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < sections.lastIndex) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Move ${section.name} down")
                                }
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
