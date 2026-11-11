package com.yehleh.aislewise.data.repo

import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.db.entities.StoreSectionOrder

/** Filters list entries to a store and groups them by section in that store's aisle order. */
object ListGrouper {

    /**
     * @param storeFilter null for "All"; otherwise only entries available at that store, plus
     *   entries with no store assigned (they can be bought anywhere).
     */
    fun group(
        entries: List<ListEntryRow>,
        itemStores: List<ItemStore>,
        stores: List<Store>,
        sections: List<Section>,
        storeOrders: List<StoreSectionOrder>,
        storeFilter: Long?,
    ): List<SectionGroup> {
        val storesById = stores.associateBy { it.id }
        val linksByItem = itemStores.groupBy { it.itemId }
        val sectionsById = sections.associateBy { it.id }
        val orderForStore = storeFilter?.let { id ->
            storeOrders.filter { it.storeId == id }.associate { it.sectionId to it.position }
        }.orEmpty()

        fun sectionRank(sectionId: Long?): Int {
            if (sectionId == null) return Int.MAX_VALUE
            return orderForStore[sectionId] ?: sectionsById[sectionId]?.defaultOrder?.plus(10_000) ?: Int.MAX_VALUE - 1
        }

        return entries
            .mapNotNull { entry ->
                val links = linksByItem[entry.itemId].orEmpty()
                val storeIds = links.map { it.storeId }
                if (storeFilter != null && storeIds.isNotEmpty() && storeFilter !in storeIds) return@mapNotNull null
                ListItemUi(
                    entry = entry,
                    stores = storeIds.mapNotNull { storesById[it] }.sortedBy { it.sortIndex },
                    prices = links.mapNotNull { link ->
                        link.priceCents?.let { link.storeId to StorePrice(it, link.priceUpdatedAt) }
                    }.toMap(),
                )
            }
            .groupBy { it.entry.sectionId?.takeIf { id -> id in sectionsById } }
            .entries
            .sortedBy { sectionRank(it.key) }
            .map { (sectionId, items) ->
                SectionGroup(
                    section = sectionId?.let { sectionsById[it] },
                    items = items.sortedWith(compareBy({ it.entry.checked }, { it.entry.name.lowercase() })),
                )
            }
    }

    /**
     * The store with the lowest known price for this item, or null when no store has a price.
     * Ties keep the item's store order, so the first store by sort index wins.
     */
    fun cheapestStore(item: ListItemUi): Pair<Store, StorePrice>? =
        cheapestStore(item.stores, item.prices)

    fun cheapestStore(stores: List<Store>, prices: Map<Long, StorePrice>): Pair<Store, StorePrice>? =
        stores.mapNotNull { store -> prices[store.id]?.let { store to it } }
            .minByOrNull { (_, price) -> price.cents }

    /** Orders stores by their count, highest first; ties keep the given order. */
    fun sortByCount(stores: List<Store>, counts: Map<Long, Int>): List<Store> =
        stores.sortedByDescending { counts[it.id] ?: 0 }

    /** Unchecked entry counts per store (entries with no store count toward every store). */
    fun countsByStore(entries: List<ListEntryRow>, itemStores: List<ItemStore>, stores: List<Store>): Map<Long, Int> {
        val storeIdsByItem = itemStores.groupBy({ it.itemId }, { it.storeId })
        val open = entries.filterNot { it.checked }
        return stores.associate { store ->
            store.id to open.count { e ->
                val ids = storeIdsByItem[e.itemId].orEmpty()
                ids.isEmpty() || store.id in ids
            }
        }
    }
}
