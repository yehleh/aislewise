package com.yehleh.aislewise.feature.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehleh.aislewise.ui.components.SectionDropdown
import com.yehleh.aislewise.ui.components.StoreBadges
import com.yehleh.aislewise.ui.components.StoreChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(onDismiss: () -> Unit, viewModel: AddItemViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { nameFocus.requestFocus() }
    // Keep the cursor in the name field after each add for quick entry.
    LaunchedEffect(form.lastAdded) { if (form.lastAdded != null) nameFocus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add item", style = MaterialTheme.typography.titleLarge)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Item") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                    modifier = Modifier.weight(1f).focusRequester(nameFocus),
                )
                OutlinedTextField(
                    value = form.quantity,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text("Qty") },
                    singleLine = true,
                    modifier = Modifier.width(88.dp),
                )
            }

            if (form.suggestions.isNotEmpty()) {
                Column {
                    form.suggestions.take(5).forEach { s ->
                        ListItem(
                            headlineContent = { Text(s.name) },
                            supportingContent = {
                                val itemStores = stores.filter { it.id in s.storeIds }
                                if (itemStores.isEmpty()) Text("Any store") else StoreBadges(itemStores)
                            },
                            leadingContent = { Icon(Icons.Default.History, null) },
                            modifier = Modifier.clickable { viewModel.pickSuggestion(s) },
                        )
                    }
                }
            }

            when {
                form.matched != null && !form.userEdited ->
                    Hint("Using the stores you picked last time for ${form.matched!!.name}.")
                form.lastAdded != null -> Hint("Added ${form.lastAdded}.")
            }

            SectionDropdown(sections, form.sectionId, viewModel::onSectionChange, Modifier.fillMaxWidth())

            Text("Available at", style = MaterialTheme.typography.labelLarge)
            StoreChips(stores, form.storeIds, viewModel::toggleStore)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Done") }
                Button(onClick = viewModel::submit, enabled = form.name.isNotBlank()) { Text("Add") }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
}
