package com.yehleh.aislewise.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yehleh.aislewise.data.db.entities.CatalogItem
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.db.entities.ListEntry
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.db.entities.StoreSectionOrder
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY sortIndex, name COLLATE NOCASE")
    fun observeStores(): Flow<List<Store>>

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getStore(id: Long): Store?

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM stores")
    suspend fun nextSortIndex(): Int

    @Insert
    suspend fun insert(store: Store): Long

    @Update
    suspend fun update(store: Store)

    @Query("DELETE FROM stores WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections ORDER BY defaultOrder")
    fun observeSections(): Flow<List<Section>>

    @Query("SELECT * FROM sections ORDER BY defaultOrder")
    suspend fun getSections(): List<Section>

    @Query("SELECT * FROM store_section_order")
    fun observeAllStoreOrders(): Flow<List<StoreSectionOrder>>

    @Query("SELECT * FROM store_section_order WHERE storeId = :storeId")
    fun observeStoreOrder(storeId: Long): Flow<List<StoreSectionOrder>>

    @Query("DELETE FROM store_section_order WHERE storeId = :storeId")
    suspend fun clearStoreOrder(storeId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStoreOrder(rows: List<StoreSectionOrder>)
}

/** A catalog item flattened with its section, for display. */
data class CatalogRow(
    val id: Long,
    val name: String,
    val sectionId: Long?,
    val sectionName: String?,
)

@Dao
interface CatalogDao {
    @Query(
        """
        SELECT c.id, c.name, c.sectionId, s.name AS sectionName
        FROM catalog_items c LEFT JOIN sections s ON s.id = c.sectionId
        ORDER BY c.name COLLATE NOCASE
        """
    )
    fun observeCatalog(): Flow<List<CatalogRow>>

    @Query("SELECT * FROM catalog_items WHERE id = :id")
    suspend fun getItem(id: Long): CatalogItem?

    @Query("SELECT * FROM catalog_items WHERE normalizedName = :normalizedName")
    suspend fun getByNormalizedName(normalizedName: String): CatalogItem?

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE name LIKE '%' || :query || '%' OR normalizedName LIKE '%' || :query || '%'
        ORDER BY CASE WHEN normalizedName LIKE :query || '%' THEN 0 ELSE 1 END, name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<CatalogItem>

    @Insert
    suspend fun insert(item: CatalogItem): Long

    @Update
    suspend fun update(item: CatalogItem)

    @Query("DELETE FROM catalog_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM item_stores")
    fun observeAllItemStores(): Flow<List<ItemStore>>

    @Query("SELECT storeId FROM item_stores WHERE itemId = :itemId")
    suspend fun getStoreIds(itemId: Long): List<Long>

    @Query("DELETE FROM item_stores WHERE itemId = :itemId")
    suspend fun clearStores(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemStores(rows: List<ItemStore>)
}

/** A list entry flattened with its catalog item and section, for display. */
data class ListEntryRow(
    val id: Long,
    val itemId: Long,
    val name: String,
    val quantity: String?,
    val note: String?,
    val checked: Boolean,
    val addedAt: Long,
    val sectionId: Long?,
    val sectionName: String?,
    val sectionDefaultOrder: Int?,
)

@Dao
interface ListDao {
    @Query(
        """
        SELECT e.id, e.itemId, c.name, e.quantity, e.note, e.checked, e.addedAt,
               c.sectionId, s.name AS sectionName, s.defaultOrder AS sectionDefaultOrder
        FROM list_entries e
        JOIN catalog_items c ON c.id = e.itemId
        LEFT JOIN sections s ON s.id = c.sectionId
        """
    )
    fun observeEntries(): Flow<List<ListEntryRow>>

    @Query("SELECT * FROM list_entries WHERE itemId = :itemId LIMIT 1")
    suspend fun getEntryForItem(itemId: Long): ListEntry?

    @Insert
    suspend fun insert(entry: ListEntry): Long

    @Update
    suspend fun update(entry: ListEntry)

    @Query("UPDATE list_entries SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM list_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM list_entries WHERE checked = 1")
    suspend fun deleteChecked()
}
