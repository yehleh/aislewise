package com.yehleh.aislewise.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.db.Migrations
import com.yehleh.aislewise.data.repo.CatalogRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Builds a database exactly as version [version] of the app created it, from the exported Room schema. */
    private fun createFromSchema(name: String, version: Int): SQLiteDatabase {
        val schema = Json.parseToJsonElement(File("schemas/${AppDatabase::class.qualifiedName}/$version.json").readText())
            .jsonObject["database"]!!.jsonObject
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs(); delete() }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        schema["entities"]!!.jsonArray.forEach { e ->
            val entity = e.jsonObject
            val table = entity["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { i ->
                db.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.version = version
        return db
    }

    @Test fun `v1 to v2 keeps stores and their items, and adds an empty color`() = runTest {
        createFromSchema("migration-test", 1).apply {
            execSQL("INSERT INTO sections (id, name, defaultOrder) VALUES (1, 'Dairy', 0)")
            execSQL("INSERT INTO stores (id, name, type, sortIndex) VALUES (1, 'Costco', 'Warehouse', 0)")
            execSQL("INSERT INTO catalog_items (id, name, normalizedName, sectionId) VALUES (1, 'Milk', 'milk', 1)")
            execSQL("INSERT INTO item_stores (itemId, storeId) VALUES (1, 1)")
            close()
        }

        // Opening runs the migrations; Room then fails if the result doesn't match the current entities.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test")
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val store = StoreRepository(db).observeStores().first().single()
            assertEquals("Costco", store.name)
            assertEquals("Warehouse", store.type)
            assertNull(store.color)
            assertEquals(setOf(1L), CatalogRepository(db).findByName("milk")!!.storeIds)
        } finally {
            db.close()
        }
    }

    @Test fun `v4 to v5 drops the unused list entry note but keeps entries`() = runTest {
        createFromSchema("migration-test-v4", 4).apply {
            execSQL("INSERT INTO sections (id, name, defaultOrder) VALUES (1, 'Dairy', 0)")
            execSQL("INSERT INTO catalog_items (id, name, normalizedName, sectionId, note) VALUES (1, 'Milk', 'milk', 1, NULL)")
            execSQL("INSERT INTO list_entries (id, itemId, quantity, note, checked, addedAt) VALUES (1, 1, '2', 'old note', 0, 123)")
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-v4")
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val entry = db.listDao().observeEntries().first().single()
            assertEquals("2", entry.quantity)
            assertEquals(false, entry.checked)

            val columns = mutableListOf<String>()
            db.openHelper.readableDatabase.query("PRAGMA table_info(list_entries)").use { cursor ->
                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertFalse(columns.contains("note"))
        } finally {
            db.close()
        }
    }

    @Test fun `v5 to v6 reorders the default sections but keeps store overrides`() = runTest {
        createFromSchema("migration-test-v5", 5).apply {
            listOf(
                "Produce", "Bakery", "Dairy", "Meat & Fish", "Deli", "Dry Goods",
                "Canned & Jarred", "Spices & Baking", "Snacks", "Beverages", "Frozen",
                "Household", "Personal Care", "Other",
            ).forEachIndexed { index, name ->
                execSQL("INSERT INTO sections (name, defaultOrder) VALUES (?, ?)", arrayOf<Any?>(name, index))
            }
            execSQL("INSERT INTO stores (id, name, type, sortIndex, color) VALUES (1, 'Costco', NULL, 0, NULL)")
            execSQL("INSERT INTO store_section_order (storeId, sectionId, position) VALUES (1, 14, 0)")
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-v5")
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val names = StoreRepository(db).getSections().map { it.name }
            assertEquals(
                listOf(
                    "Produce", "Deli", "Dairy", "Meat & Fish", "Dry Goods", "Canned & Jarred",
                    "Spices & Baking", "Frozen", "Snacks", "Bakery", "Beverages", "Household",
                    "Personal Care", "Other",
                ),
                names,
            )
            assertEquals(0, db.sectionDao().observeStoreOrder(1).first().single().position)
        } finally {
            db.close()
        }
    }

    @Test fun `v3 to v4 keeps item stores and adds empty prices`() = runTest {
        createFromSchema("migration-test-v3", 3).apply {
            execSQL("INSERT INTO sections (id, name, defaultOrder) VALUES (1, 'Dairy', 0)")
            execSQL("INSERT INTO stores (id, name, type, sortIndex, color) VALUES (1, 'Costco', NULL, 0, NULL)")
            execSQL("INSERT INTO catalog_items (id, name, normalizedName, sectionId, note) VALUES (1, 'Milk', 'milk', 1, NULL)")
            execSQL("INSERT INTO item_stores (itemId, storeId) VALUES (1, 1)")
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-v3")
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val item = CatalogRepository(db).findByName("milk")!!
            assertEquals(setOf(1L), item.storeIds)
            assertEquals(0, item.prices.size)
        } finally {
            db.close()
        }
    }

    @Test fun `v2 to v3 keeps catalog items and adds an empty note`() = runTest {
        createFromSchema("migration-test-v2", 2).apply {
            execSQL("INSERT INTO sections (id, name, defaultOrder) VALUES (1, 'Dairy', 0)")
            execSQL("INSERT INTO catalog_items (id, name, normalizedName, sectionId) VALUES (1, 'Milk', 'milk', 1)")
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migration-test-v2")
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        try {
            val item = CatalogRepository(db).findByName("milk")!!
            assertEquals("Milk", item.name)
            assertNull(item.note)
        } finally {
            db.close()
        }
    }
}
