package com.yehleh.aislewise.feature.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehleh.aislewise.data.repo.CatalogRepository
import com.yehleh.aislewise.data.repo.ItemKnowledge
import com.yehleh.aislewise.data.repo.ShoppingRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddItemForm(
    val name: String = "",
    val quantity: String = "",
    val sectionId: Long? = null,
    val storeIds: Set<Long> = emptySet(),
    /** The remembered item whose section and stores are currently filled in, if any. */
    val matched: ItemKnowledge? = null,
    /** Set once the user changes the section or stores by hand, so typing no longer overwrites them. */
    val userEdited: Boolean = false,
    val suggestions: List<ItemKnowledge> = emptyList(),
    val lastAdded: String? = null,
)

@HiltViewModel
class AddItemViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val shopping: ShoppingRepository,
    storeRepo: StoreRepository,
) : ViewModel() {

    val stores = storeRepo.observeStores().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sections = storeRepo.observeSections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _form = MutableStateFlow(AddItemForm())
    val form: StateFlow<AddItemForm> = _form.asStateFlow()

    private var lookupJob: Job? = null

    fun onNameChange(name: String) {
        _form.update { it.copy(name = name, lastAdded = null) }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            delay(150)
            val suggestions = catalog.search(name)
            val exact = catalog.findByName(name)
            _form.update { f ->
                val base = f.copy(suggestions = suggestions.filterNot { it.itemId == exact?.itemId })
                when {
                    f.userEdited -> base.copy(matched = exact)
                    exact != null -> base.copy(matched = exact, sectionId = exact.sectionId, storeIds = exact.storeIds)
                    // Typed past a remembered item: drop the selection that was filled in for it.
                    f.matched != null -> base.copy(matched = null, sectionId = null, storeIds = emptySet())
                    else -> base
                }
            }
        }
    }

    fun pickSuggestion(item: ItemKnowledge) {
        lookupJob?.cancel()
        _form.update {
            it.copy(
                name = item.name,
                matched = item,
                sectionId = item.sectionId,
                storeIds = item.storeIds,
                userEdited = false,
                suggestions = emptyList(),
            )
        }
    }

    fun onQuantityChange(quantity: String) = _form.update { it.copy(quantity = quantity) }

    fun onSectionChange(sectionId: Long?) = _form.update { it.copy(sectionId = sectionId, userEdited = true) }

    fun toggleStore(storeId: Long) = _form.update {
        val ids = if (storeId in it.storeIds) it.storeIds - storeId else it.storeIds + storeId
        it.copy(storeIds = ids, userEdited = true)
    }

    /** Adds the item and resets the form so the next item can be typed right away. */
    fun submit() {
        val f = _form.value
        if (f.name.isBlank()) return
        lookupJob?.cancel()
        viewModelScope.launch {
            shopping.addToList(f.name, f.quantity, f.sectionId, f.storeIds)
            _form.value = AddItemForm(lastAdded = f.name.trim())
        }
    }
}
