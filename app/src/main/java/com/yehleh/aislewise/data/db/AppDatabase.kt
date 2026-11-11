package com.yehleh.aislewise.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
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
    version = 5,
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

object Migrations {
    /** v2: per-store badge color. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE stores ADD COLUMN color INTEGER DEFAULT NULL")
        }
    }

    /** v3: a free-form note remembered with each catalog item. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE catalog_items ADD COLUMN note TEXT DEFAULT NULL")
        }
    }

    /** v4: a remembered last price per item per store. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE item_stores ADD COLUMN priceCents INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE item_stores ADD COLUMN priceUpdatedAt INTEGER DEFAULT NULL")
        }
    }

    /** v5: drops the unused per-entry note (the list shows the catalog item's note). */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `list_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`itemId` INTEGER NOT NULL, `quantity` TEXT, `checked` INTEGER NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, FOREIGN KEY(`itemId`) REFERENCES `catalog_items`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL(
                "INSERT INTO `list_entries_new` (`id`, `itemId`, `quantity`, `checked`, `addedAt`) " +
                    "SELECT `id`, `itemId`, `quantity`, `checked`, `addedAt` FROM `list_entries`",
            )
            db.execSQL("DROP TABLE `list_entries`")
            db.execSQL("ALTER TABLE `list_entries_new` RENAME TO `list_entries`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_itemId` ON `list_entries` (`itemId`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
