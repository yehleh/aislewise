package com.yehleh.aislewise.feature.scan

import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.repo.CatalogRepository
import com.yehleh.aislewise.data.repo.ShoppingRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import com.yehleh.aislewise.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Parser that returns fixed items, standing in for Gemini. */
class FakeListImageParser(private val items: List<ParsedItem>) : ListImageParser {
    override val isConfigured = true
    override suspend fun parse(images: List<android.net.Uri>, sectionNames: List<String>) = Result.success(items)
}

@RunWith(RobolectricTestRunner::class)
class ScanMatcherTest {
    private val db: AppDatabase = inMemoryDb()
    private val catalog = CatalogRepository(db)
    private val shopping = ShoppingRepository(db, catalog)
    private val stores = StoreRepository(db)

    @After fun tearDown() = db.close()

    @Test fun `known items get their remembered stores and new items get the suggested section`() = runTest {
        val costco = stores.addStore("Costco", null)
        val sections = stores.getSections()
        val dairy = sections.first { it.name == "Dairy" }.id
        val produce = sections.first { it.name == "Produce" }.id
        val other = stores.otherSectionId()
        shopping.addToList("Milk", null, dairy, setOf(costco))

        val parsed = FakeListImageParser(
            listOf(
                ParsedItem("milk", "2 gal", "Beverages"),
                ParsedItem("Bananas", null, "Produce"),
                ParsedItem("Mystery thing", null, "Not a section"),
            ),
        ).parse(emptyList(), sections.map { it.name }).getOrThrow()

        val rows = ScanMatcher(catalog).match(parsed, sections, other)

        val milk = rows[0]
        assertTrue(milk.known)
        assertEquals("Milk", milk.name)
        assertEquals("2 gal", milk.quantity)
        assertEquals(dairy, milk.sectionId) // remembered section wins over the model's guess
        assertEquals(setOf(costco), milk.storeIds)

        val bananas = rows[1]
        assertFalse(bananas.known)
        assertEquals(produce, bananas.sectionId)
        assertEquals(emptySet<Long>(), bananas.storeIds)

        assertEquals(other, rows[2].sectionId)
    }
}
