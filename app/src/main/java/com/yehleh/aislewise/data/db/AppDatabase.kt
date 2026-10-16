package com.yehleh.aislewise.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yehleh.aislewise.data.db.dao.CatalogDao
import com.yehleh.aislewise.data.db.dao.ListDao
import com.yehleh.aislewise.data.db.dao.SectionDao
import com.yehleh.aislewise.data.db.dao.StoreDao
import com.yehleh.aislewise.data.db.entities.CatalogItem
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.db.entities.ListEntry
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.db.entities.StoreSectionOrder

@Database(
    entities = [Store::class, Section::class, StoreSectionOrder::class, CatalogItem::class, ItemStore::class, ListEntry::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun sectionDao(): SectionDao
    abstract fun catalogDao(): CatalogDao
    abstract fun listDao(): ListDao

    companion object {
        const val NAME = "aislewise.db"
    }
}

/** Inserts the default sections when the database is first created. */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        Seed.DEFAULT_SECTIONS.forEachIndexed { index, name ->
            db.execSQL("INSERT INTO sections (name, defaultOrder) VALUES (?, ?)", arrayOf(name, index))
        }
    }
}

object Seed {
    const val OTHER = "Other"

    val DEFAULT_SECTIONS = listOf(
        "Produce",
        "Bakery",
        "Dairy",
        "Meat & Fish",
        "Deli",
        "Dry Goods",
        "Canned & Jarred",
        "Spices & Baking",
        "Snacks",
        "Beverages",
        "Frozen",
        "Household",
        "Personal Care",
        OTHER,
    )
}
