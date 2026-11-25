package com.yehleh.aislewise.feature.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.db.entities.ListEntry
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.db.entities.StoreSectionOrder
import com.yehleh.aislewise.data.repo.CatalogRepository
import com.yehleh.aislewise.data.repo.ListGrouper
import com.yehleh.aislewise.data.repo.ListItemUi
import com.yehleh.aislewise.data.repo.SectionGroup
import com.yehleh.aislewise.data.repo.ShoppingRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val loading: Boolean = true,
    val stores: List<Store> = emptyList(),
    val counts: Map<Long, Int> = emptyMap(),
    val totalOpen: Int = 0,
    val selectedStoreId: Long? = null,
    val groups: List<SectionGroup> = emptyList(),
    val hasChecked: Boolean = false,
)

@HiltViewModel
class ListViewModel @Inject constructor(
    private val shopping: ShoppingRepository,
    private val catalog: CatalogRepository,
    storeRepo: StoreRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val selectedStore = savedState.getStateFlow<Long?>(KEY_STORE, null)

    private val data = combine(
        shopping.observeEntries(),
        shopping.observeItemStores(),
        storeRepo.observeStores(),
        storeRepo.observeSections(),
        storeRepo.observeAllStoreOrders(),
    ) { entries, itemStores, stores, sections, orders ->
        Snapshot(entries, itemStores, stores, sections, orders)
    }

    val state: StateFlow<ListUiState> = combine(data, selectedStore) { d, selected ->
        // Fall back to "All" if the selected store was deleted.
        val storeId = selected?.takeIf { id -> d.stores.any { it.id == id } }
        val counts = ListGrouper.countsByStore(d.entries, d.itemStores, d.stores)
        ListUiState(
            loading = false,
            stores = ListGrouper.sortByCount(d.stores, counts),
            counts = counts,
            totalOpen = d.entries.count { !it.checked },
            selectedStoreId = storeId,
            groups = ListGrouper.group(d.entries, d.itemStores, d.stores, d.sections, d.orders, storeId),
            hasChecked = d.entries.any { it.checked },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun selectStore(storeId: Long?) {
        savedState[KEY_STORE] = storeId
    }

    fun toggle(entry: ListEntryRow) = viewModelScope.launch { shopping.setChecked(entry.id, !entry.checked) }

    fun remove(entry: ListEntryRow) = viewModelScope.launch { shopping.remove(entry.id) }

    fun saveDetails(item: ListItemUi, note: String?, prices: Map<Long, Long?>) = viewModelScope.launch {
        catalog.setDetails(item.entry.itemId, note, prices)
    }

    fun undoRemove(entry: ListEntryRow) = viewModelScope.launch { shopping.restore(entry) }

    suspend fun clearChecked(): List<ListEntry> = shopping.clearChecked()

    fun undoClear(entries: List<ListEntry>) = viewModelScope.launch { shopping.restoreCleared(entries) }

    private data class Snapshot(
        val entries: List<ListEntryRow>,
        val itemStores: List<ItemStore>,
        val stores: List<Store>,
        val sections: List<Section>,
        val orders: List<StoreSectionOrder>,
    )

    private companion object {
        const val KEY_STORE = "selectedStore"
    }
}
