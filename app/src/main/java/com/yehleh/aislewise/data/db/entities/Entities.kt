package com.yehleh.aislewise.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Optional free-form label, e.g. "Grocery" or "Pharmacy". */
    val type: String? = null,
    val sortIndex: Int = 0,
    /** ARGB badge color; null means "use the palette default" (see StorePalette). */
    val color: Int? = null,
)

@Entity(tableName = "sections")
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultOrder: Int,
)

/** A store's own aisle order for a section. Sections without a row fall back to [Section.defaultOrder]. */
@Entity(
    tableName = "store_section_order",
    primaryKeys = ["storeId", "sectionId"],
    foreignKeys = [
        ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Section::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sectionId")],
)
data class StoreSectionOrder(
    val storeId: Long,
    val sectionId: Long,
    val position: Int,
)

/** Everything the app remembers about an item, independent of whether it is on the list right now. */
@Entity(
    tableName = "catalog_items",
    foreignKeys = [
        ForeignKey(entity = Section::class, parentColumns = ["id"], childColumns = ["sectionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index(value = ["normalizedName"], unique = true), Index("sectionId")],
)
data class CatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String,
    val sectionId: Long?,
    /** Free-form note, e.g. a remembered price. */
    val note: String? = null,
)

/** The stores the user last chose for an item. */
@Entity(
    tableName = "item_stores",
    primaryKeys = ["itemId", "storeId"],
    foreignKeys = [
        ForeignKey(entity = CatalogItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Store::class, parentColumns = ["id"], childColumns = ["storeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("storeId")],
)
data class ItemStore(
    val itemId: Long,
    val storeId: Long,
    /** Last known price in cents at this store; null when unknown. */
    val priceCents: Long? = null,
    /** When [priceCents] was last recorded, so the UI can show how stale it is. */
    val priceUpdatedAt: Long? = null,
)

@Entity(
    tableName = "list_entries",
    foreignKeys = [
        ForeignKey(entity = CatalogItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("itemId")],
)
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val quantity: String? = null,
    val note: String? = null,
    val checked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
)
