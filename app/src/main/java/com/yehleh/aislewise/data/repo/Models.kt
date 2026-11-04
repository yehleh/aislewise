package com.yehleh.aislewise.data.repo

import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store

/** What the app remembers about an item: its section and the stores it can be bought at. */
data class ItemKnowledge(
    val itemId: Long,
    val name: String,
    val sectionId: Long?,
    val storeIds: Set<Long>,
    val note: String? = null,
)

data class CatalogEntry(
    val id: Long,
    val name: String,
    val sectionId: Long?,
    val sectionName: String?,
    val stores: List<Store>,
)

data class ListItemUi(
    val entry: ListEntryRow,
    val stores: List<Store>,
)

data class SectionGroup(
    /** Null for items without a section. */
    val section: Section?,
    val items: List<ListItemUi>,
)

class DuplicateItemNameException(val existingName: String) :
    IllegalArgumentException("An item named \"$existingName\" already exists")
