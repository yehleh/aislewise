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

        // Opening runs the migration; Room then fails if the result doesn't match the v2 entities.
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
}
