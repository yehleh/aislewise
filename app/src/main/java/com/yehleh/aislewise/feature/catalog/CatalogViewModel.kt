package com.yehleh.aislewise.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehleh.aislewise.data.normalize.ItemNameNormalizer
import com.yehleh.aislewise.data.repo.CatalogEntry
import com.yehleh.aislewise.data.repo.CatalogRepository
import com.yehleh.aislewise.data.repo.DuplicateItemNameException
import com.yehleh.aislewise.data.repo.ShoppingRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    catalog: CatalogRepository,
    private val shopping: ShoppingRepository,
) : ViewModel() {
    val query = MutableStateFlow("")

    val items: StateFlow<List<CatalogEntry>?> = combine(catalog.observeCatalog(), query) { all, q ->
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) all
        else all.filter { it.name.lowercase().contains(needle) || ItemNameNormalizer.normalize(it.name).contains(needle) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages

    fun addToList(item: CatalogEntry) = viewModelScope.launch {
        shopping.addExisting(item.id)
        _messages.emit("Added ${item.name} to the list")
    }
}

data class ItemEditorState(
    val loaded: Boolean = false,
    val name: String = "",
    val sectionId: Long? = null,
    val storeIds: Set<Long> = emptySet(),
    val note: String = "",
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class ItemEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val catalog: CatalogRepository,
    storeRepo: StoreRepository,
) : ViewModel() {
    private val itemId: Long = checkNotNull(savedState["itemId"])

    val stores = storeRepo.observeStores().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sections = storeRepo.observeSections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(ItemEditorState())
    val state: StateFlow<ItemEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val item = catalog.getKnowledge(itemId)
            _state.value = if (item == null) ItemEditorState(done = true)
            else ItemEditorState(
                loaded = true,
                name = item.name,
                sectionId = item.sectionId,
                storeIds = item.storeIds,
                note = item.note.orEmpty(),
            )
        }
    }

    fun onNameChange(name: String) = _state.update { it.copy(name = name, error = null) }
    fun onSectionChange(id: Long?) = _state.update { it.copy(sectionId = id) }
    fun onNoteChange(note: String) = _state.update { it.copy(note = note) }
    fun toggleStore(id: Long) = _state.update {
        it.copy(storeIds = if (id in it.storeIds) it.storeIds - id else it.storeIds + id)
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(error = "Name can't be empty") }
            return
        }
        viewModelScope.launch {
            try {
                catalog.update(itemId, s.name, s.sectionId, s.storeIds, s.note)
                _state.update { it.copy(done = true) }
            } catch (e: DuplicateItemNameException) {
                _state.update { it.copy(error = "\"${e.existingName}\" already exists") }
            }
        }
    }

    fun delete() = viewModelScope.launch {
        catalog.delete(itemId)
        _state.update { it.copy(done = true) }
    }
}
