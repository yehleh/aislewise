package com.yehleh.aislewise.feature.stores

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.repo.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoresViewModel @Inject constructor(private val repo: StoreRepository) : ViewModel() {
    val stores: StateFlow<List<Store>?> = repo.observeStores().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(existing: Store?, name: String, type: String) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        if (existing == null) repo.addStore(name, type) else repo.updateStore(existing.copy(name = name, type = type))
    }

    fun delete(store: Store) = viewModelScope.launch { repo.deleteStore(store.id) }
}

@HiltViewModel
class SectionOrderViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: StoreRepository,
) : ViewModel() {
    private val storeId: Long = checkNotNull(savedState["storeId"])

    val storeName = MutableStateFlow("")
    val sections: StateFlow<List<Section>> =
        repo.observeSectionOrder(storeId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { storeName.value = repo.getStore(storeId)?.name.orEmpty() }
    }

    fun move(index: Int, delta: Int) {
        val current = sections.value.toMutableList()
        val target = index + delta
        if (index !in current.indices || target !in current.indices) return
        current.add(target, current.removeAt(index))
        viewModelScope.launch { repo.setSectionOrder(storeId, current.map { it.id }) }
    }

    fun reset() = viewModelScope.launch { repo.resetSectionOrder(storeId) }
}
