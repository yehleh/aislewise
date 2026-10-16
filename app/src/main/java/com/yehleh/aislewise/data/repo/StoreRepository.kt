package com.yehleh.aislewise.data.repo

import androidx.room.withTransaction
import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.db.Seed
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.db.entities.StoreSectionOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepository @Inject constructor(private val db: AppDatabase) {
    private val storeDao = db.storeDao()
    private val sectionDao = db.sectionDao()

    fun observeStores(): Flow<List<Store>> = storeDao.observeStores()

    fun observeSections(): Flow<List<Section>> = sectionDao.observeSections()

    fun observeAllStoreOrders(): Flow<List<StoreSectionOrder>> = sectionDao.observeAllStoreOrders()

    suspend fun getSections(): List<Section> = sectionDao.getSections()

    suspend fun getStore(id: Long): Store? = storeDao.getStore(id)

    /** The id of the catch-all "Other" section, used when nothing better is known. */
    suspend fun otherSectionId(): Long? = sectionDao.getSections().firstOrNull { it.name == Seed.OTHER }?.id

    suspend fun addStore(name: String, type: String?): Long =
        storeDao.insert(Store(name = name.trim(), type = type?.trim()?.ifEmpty { null }, sortIndex = storeDao.nextSortIndex()))

    suspend fun updateStore(store: Store) =
        storeDao.update(store.copy(name = store.name.trim(), type = store.type?.trim()?.ifEmpty { null }))

    suspend fun deleteStore(id: Long) = storeDao.delete(id)

    /** Sections in the order they appear in this store (custom order first, then defaults). */
    fun observeSectionOrder(storeId: Long): Flow<List<Section>> =
        combine(sectionDao.observeSections(), sectionDao.observeStoreOrder(storeId)) { sections, order ->
            sortForStore(sections, order)
        }

    suspend fun setSectionOrder(storeId: Long, orderedSectionIds: List<Long>) = db.withTransaction {
        sectionDao.clearStoreOrder(storeId)
        sectionDao.insertStoreOrder(orderedSectionIds.mapIndexed { i, id -> StoreSectionOrder(storeId, id, i) })
    }

    suspend fun resetSectionOrder(storeId: Long) = sectionDao.clearStoreOrder(storeId)

    companion object {
        fun sortForStore(sections: List<Section>, order: List<StoreSectionOrder>): List<Section> {
            val pos = order.associate { it.sectionId to it.position }
            return sections.sortedWith(compareBy({ pos[it.id] ?: Int.MAX_VALUE }, { it.defaultOrder }))
        }
    }
}
