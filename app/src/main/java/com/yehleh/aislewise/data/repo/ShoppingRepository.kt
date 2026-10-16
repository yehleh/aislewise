package com.yehleh.aislewise.data.repo

import androidx.room.withTransaction
import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.db.entities.ListEntry
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepository @Inject constructor(
    private val db: AppDatabase,
    private val catalog: CatalogRepository,
) {
    private val dao = db.listDao()

    fun observeEntries(): Flow<List<ListEntryRow>> = dao.observeEntries()

    fun observeItemStores(): Flow<List<ItemStore>> = db.catalogDao().observeAllItemStores()

    /**
     * Puts an item on the list and remembers its section and stores for next time.
     * If the item is already on the list it is reused (and unchecked) instead of duplicated.
     */
    suspend fun addToList(name: String, quantity: String?, sectionId: Long?, storeIds: Set<Long>): Long =
        db.withTransaction {
            val itemId = catalog.remember(name, sectionId, storeIds)
            addExisting(itemId, quantity)
            itemId
        }

    /** Puts an already-remembered item on the list. */
    suspend fun addExisting(itemId: Long, quantity: String? = null) {
        val qty = quantity?.trim()?.ifEmpty { null }
        val existing = dao.getEntryForItem(itemId)
        if (existing == null) {
            dao.insert(ListEntry(itemId = itemId, quantity = qty))
        } else {
            dao.update(existing.copy(checked = false, quantity = qty ?: existing.quantity))
        }
    }

    suspend fun setChecked(entryId: Long, checked: Boolean) = dao.setChecked(entryId, checked)

    suspend fun remove(entryId: Long) = dao.delete(entryId)

    /** Re-inserts a removed entry (for undo). */
    suspend fun restore(row: ListEntryRow) {
        dao.insert(ListEntry(row.id, row.itemId, row.quantity, row.note, row.checked, row.addedAt))
    }

    suspend fun clearChecked() = dao.deleteChecked()
}
