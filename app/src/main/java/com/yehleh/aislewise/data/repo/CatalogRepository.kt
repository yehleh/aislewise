package com.yehleh.aislewise.data.repo

import androidx.room.withTransaction
import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.db.entities.CatalogItem
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.normalize.ItemNameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(private val db: AppDatabase) {
    private val dao = db.catalogDao()

    fun observeCatalog(): Flow<List<CatalogEntry>> =
        combine(dao.observeCatalog(), dao.observeAllItemStores(), db.storeDao().observeStores()) { rows, links, stores ->
            val storesById = stores.associateBy { it.id }
            val storeIdsByItem = links.groupBy({ it.itemId }, { it.storeId })
            rows.map { row ->
                CatalogEntry(
                    id = row.id,
                    name = row.name,
                    sectionId = row.sectionId,
                    sectionName = row.sectionName,
                    stores = storeIdsByItem[row.id].orEmpty().mapNotNull { storesById[it] }.sortedBy { it.sortIndex },
                )
            }
        }

    /** Looks up a remembered item by name, ignoring case, spacing and plurals. */
    suspend fun findByName(name: String): ItemKnowledge? {
        val key = ItemNameNormalizer.normalize(name)
        if (key.isEmpty()) return null
        return dao.getByNormalizedName(key)?.let { knowledgeFor(it) }
    }

    suspend fun getKnowledge(itemId: Long): ItemKnowledge? = dao.getItem(itemId)?.let { knowledgeFor(it) }

    suspend fun search(query: String, limit: Int = 8): List<ItemKnowledge> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return dao.search(q.lowercase(), limit).map { knowledgeFor(it) }
    }

    /**
     * Creates the item or updates the remembered one with the same name, replacing its
     * section and stores with this selection. Returns the item id.
     */
    suspend fun remember(name: String, sectionId: Long?, storeIds: Set<Long>): Long = db.withTransaction {
        val displayName = name.trim().replace(Regex("\\s+"), " ")
        val key = ItemNameNormalizer.normalize(displayName)
        require(key.isNotEmpty()) { "Item name is empty" }
        val existing = dao.getByNormalizedName(key)
        val id = if (existing != null) {
            dao.update(existing.copy(sectionId = sectionId))
            existing.id
        } else {
            dao.insert(CatalogItem(name = displayName, normalizedName = key, sectionId = sectionId))
        }
        replaceStores(id, storeIds)
        id
    }

    /** Edits an existing item, including renaming it. */
    suspend fun update(itemId: Long, name: String, sectionId: Long?, storeIds: Set<Long>) = db.withTransaction {
        val item = requireNotNull(dao.getItem(itemId)) { "Item $itemId not found" }
        val displayName = name.trim().replace(Regex("\\s+"), " ")
        val key = ItemNameNormalizer.normalize(displayName)
        require(key.isNotEmpty()) { "Item name is empty" }
        val clash = dao.getByNormalizedName(key)
        if (clash != null && clash.id != itemId) throw DuplicateItemNameException(clash.name)
        dao.update(item.copy(name = displayName, normalizedName = key, sectionId = sectionId))
        replaceStores(itemId, storeIds)
    }

    suspend fun delete(itemId: Long) = dao.delete(itemId)

    private suspend fun replaceStores(itemId: Long, storeIds: Set<Long>) {
        dao.clearStores(itemId)
        dao.insertItemStores(storeIds.map { ItemStore(itemId, it) })
    }

    private suspend fun knowledgeFor(item: CatalogItem) =
        ItemKnowledge(item.id, item.name, item.sectionId, dao.getStoreIds(item.id).toSet())
}
