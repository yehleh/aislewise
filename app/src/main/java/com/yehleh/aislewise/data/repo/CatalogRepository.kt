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
            val linksByItem = links.groupBy { it.itemId }
            rows.map { row ->
                val itemLinks = linksByItem[row.id].orEmpty()
                CatalogEntry(
                    id = row.id,
                    name = row.name,
                    sectionId = row.sectionId,
                    sectionName = row.sectionName,
                    stores = itemLinks.mapNotNull { storesById[it.storeId] }.sortedBy { it.sortIndex },
                    prices = pricesOf(itemLinks),
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

    /**
     * Edits an existing item, including renaming it. [prices] maps a store to its typed price in
     * cents; a key present with a null value clears that store's price, while an absent key keeps
     * whatever was remembered (so hiding the price UI can't wipe it).
     */
    suspend fun update(
        itemId: Long,
        name: String,
        sectionId: Long?,
        storeIds: Set<Long>,
        note: String? = null,
        prices: Map<Long, Long?> = emptyMap(),
    ) = db.withTransaction {
        val item = requireNotNull(dao.getItem(itemId)) { "Item $itemId not found" }
        val displayName = name.trim().replace(Regex("\\s+"), " ")
        val key = ItemNameNormalizer.normalize(displayName)
        require(key.isNotEmpty()) { "Item name is empty" }
        val clash = dao.getByNormalizedName(key)
        if (clash != null && clash.id != itemId) throw DuplicateItemNameException(clash.name)
        dao.update(item.copy(name = displayName, normalizedName = key, sectionId = sectionId, note = cleanNote(note)))
        replaceStores(itemId, storeIds, prices)
    }

    /** Sets the free-form note remembered with an item; blank clears it. */
    suspend fun setNote(itemId: Long, note: String?) = dao.setNote(itemId, cleanNote(note))

    suspend fun delete(itemId: Long) = dao.delete(itemId)

    private fun cleanNote(note: String?): String? = note?.trim()?.ifEmpty { null }

    /**
     * Replaces an item's store links, keeping the remembered price of stores that stay. For a
     * store in [prices], a changed value bumps the timestamp and a null clears it; stores not in
     * [prices] keep their price untouched.
     */
    private suspend fun replaceStores(itemId: Long, storeIds: Set<Long>, prices: Map<Long, Long?> = emptyMap()) {
        val now = System.currentTimeMillis()
        val existing = dao.getItemStores(itemId).associateBy { it.storeId }
        val rows = storeIds.map { storeId ->
            val prev = existing[storeId]
            val cents = if (prices.containsKey(storeId)) prices[storeId] else prev?.priceCents
            val updatedAt = when {
                !prices.containsKey(storeId) -> prev?.priceUpdatedAt
                cents == null -> null
                prev == null || prev.priceCents != cents -> now
                else -> prev.priceUpdatedAt
            }
            ItemStore(itemId, storeId, cents, updatedAt)
        }
        dao.clearStores(itemId)
        dao.insertItemStores(rows)
    }

    private suspend fun knowledgeFor(item: CatalogItem): ItemKnowledge {
        val links = dao.getItemStores(item.id)
        return ItemKnowledge(
            itemId = item.id,
            name = item.name,
            sectionId = item.sectionId,
            storeIds = links.map { it.storeId }.toSet(),
            note = item.note,
            prices = pricesOf(links),
        )
    }

    private fun pricesOf(links: List<ItemStore>): Map<Long, StorePrice> =
        links.mapNotNull { link ->
            link.priceCents?.let { link.storeId to StorePrice(it, link.priceUpdatedAt) }
        }.toMap()
}
