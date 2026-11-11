package com.yehleh.aislewise.data.repo

import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store

/** The last known price of an item at a particular store. */
data class StorePrice(val cents: Long, val updatedAt: Long?)

/** What the app remembers about an item: its section, the stores it can be bought at, and prices. */
data class ItemKnowledge(
    val itemId: Long,
    val name: String,
    val sectionId: Long?,
    val storeIds: Set<Long>,
    val note: String? = null,
    val prices: Map<Long, StorePrice> = emptyMap(),
)

data class CatalogEntry(
    val id: Long,
    val name: String,
    val sectionId: Long?,
    val sectionName: String?,
    val stores: List<Store>,
    val prices: Map<Long, StorePrice> = emptyMap(),
)

data class ListItemUi(
    val entry: ListEntryRow,
    val stores: List<Store>,
    val prices: Map<Long, StorePrice> = emptyMap(),
)

data class SectionGroup(
    /** Null for items without a section. */
    val section: Section?,
    val items: List<ListItemUi>,
)

class DuplicateItemNameException(val existingName: String) :
    IllegalArgumentException("An item named \"$existingName\" already exists")
