package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.db.AppDatabase
import com.yehleh.aislewise.data.repo.CatalogRepository
import com.yehleh.aislewise.data.repo.DuplicateItemNameException
import com.yehleh.aislewise.data.repo.ShoppingRepository
import com.yehleh.aislewise.data.repo.StoreRepository
import com.yehleh.aislewise.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var catalog: CatalogRepository
    private lateinit var shopping: ShoppingRepository
    private lateinit var storeRepo: StoreRepository

    @Before fun setUp() {
        db = inMemoryDb()
        catalog = CatalogRepository(db)
        shopping = ShoppingRepository(db, catalog)
        storeRepo = StoreRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun `default sections are seeded`() = runTest {
        val names = storeRepo.getSections().map { it.name }
        assertEquals("Produce", names.first())
        assertEquals("Other", names.last())
    }

    @Test fun `stores picked for an item are remembered the next time it is added`() = runTest {
        val a = storeRepo.addStore("Costco", "Warehouse")
        val b = storeRepo.addStore("ShopRite", null)
        val dairy = storeRepo.getSections().first { it.name == "Dairy" }.id

        shopping.addToList("Milk", "2", dairy, setOf(a, b))
        val entry = shopping.observeEntries().first().single()
        shopping.remove(entry.id)

        val remembered = catalog.findByName("milk ")!!
        assertEquals(setOf(a, b), remembered.storeIds)
        assertEquals(dairy, remembered.sectionId)
        assertEquals("Milk", remembered.name)
    }

    @Test fun `adding again updates the remembered stores and does not duplicate the entry`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val b = storeRepo.addStore("ShopRite", null)

        shopping.addToList("Eggs", null, null, setOf(a))
        val first = shopping.observeEntries().first().single()
        shopping.setChecked(first.id, true)
        shopping.addToList("egg", "12", null, setOf(b))

        val entries = shopping.observeEntries().first()
        assertEquals(1, entries.size)
        assertFalse(entries.single().checked)
        assertEquals("12", entries.single().quantity)
        assertEquals(setOf(b), catalog.findByName("Eggs")!!.storeIds)
    }

    @Test fun `editing an item's stores is used on the next add`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val b = storeRepo.addStore("Target", null)
        val id = shopping.addToList("Paper towels", null, null, setOf(a))

        catalog.update(id, "Paper towels", null, setOf(b))

        assertEquals(setOf(b), catalog.findByName("paper towel")!!.storeIds)
    }

    @Test fun `a note is remembered with the item and shown on the list`() = runTest {
        val id = shopping.addToList("Milk", null, null, emptySet())

        catalog.setNote(id, "  $3.49 at ShopRite  ")

        assertEquals("$3.49 at ShopRite", catalog.findByName("milk")!!.note)
        assertEquals("$3.49 at ShopRite", shopping.observeEntries().first().single().note)
    }

    @Test fun `a blank note clears it`() = runTest {
        val id = shopping.addToList("Milk", null, null, emptySet())
        catalog.setNote(id, "price")

        catalog.setNote(id, "   ")

        assertNull(catalog.findByName("milk")!!.note)
    }

    @Test fun `a note survives re-adding and editing the item`() = runTest {
        val id = shopping.addToList("Milk", null, null, emptySet())
        catalog.setNote(id, "price")

        shopping.addToList("milk", "2", null, emptySet())
        assertEquals("price", catalog.findByName("Milk")!!.note)

        catalog.update(id, "Whole milk", null, emptySet(), "price")
        assertEquals("price", catalog.findByName("Whole milk")!!.note)
    }

    @Test fun `a price is remembered per store and survives re-adding the item`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val b = storeRepo.addStore("ShopRite", null)
        val id = shopping.addToList("Milk", null, null, setOf(a, b))

        catalog.update(id, "Milk", null, setOf(a, b), null, mapOf(a to 349L, b to 299L))
        assertEquals(349L, catalog.findByName("milk")!!.prices.getValue(a).cents)
        assertEquals(299L, catalog.findByName("milk")!!.prices.getValue(b).cents)

        shopping.addToList("milk", "2", null, setOf(a, b))
        assertEquals(349L, catalog.findByName("milk")!!.prices.getValue(a).cents)
        assertEquals(299L, catalog.findByName("milk")!!.prices.getValue(b).cents)
    }

    @Test fun `an unchanged price keeps its timestamp and a new one is recorded`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val id = shopping.addToList("Milk", null, null, setOf(a))

        catalog.update(id, "Milk", null, setOf(a), null, mapOf(a to 349L))
        val first = catalog.findByName("milk")!!.prices.getValue(a)
        catalog.update(id, "Milk", null, setOf(a), null, mapOf(a to 349L))
        assertEquals(first.updatedAt, catalog.findByName("milk")!!.prices.getValue(a).updatedAt)

        catalog.update(id, "Milk", null, setOf(a), null, mapOf(a to 399L))
        val changed = catalog.findByName("milk")!!.prices.getValue(a)
        assertEquals(399L, changed.cents)
        assertFalse(changed.updatedAt == null)
    }

    @Test fun `a blank price clears it while an absent entry keeps it`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val id = shopping.addToList("Milk", null, null, setOf(a))
        catalog.update(id, "Milk", null, setOf(a), null, mapOf(a to 349L))

        catalog.update(id, "Milk", null, setOf(a), null, emptyMap())
        assertEquals(349L, catalog.findByName("milk")!!.prices.getValue(a).cents)

        catalog.update(id, "Milk", null, setOf(a), null, mapOf(a to null))
        assertNull(catalog.findByName("milk")!!.prices[a])
    }

    @Test fun `removing a store drops its remembered price`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val b = storeRepo.addStore("ShopRite", null)
        val id = shopping.addToList("Milk", null, null, setOf(a, b))
        catalog.update(id, "Milk", null, setOf(a, b), null, mapOf(a to 349L, b to 299L))

        catalog.update(id, "Milk", null, setOf(b), null, emptyMap())

        val prices = catalog.findByName("milk")!!.prices
        assertNull(prices[a])
        assertEquals(299L, prices.getValue(b).cents)
    }

    @Test(expected = DuplicateItemNameException::class)
    fun `renaming onto an existing item is rejected`() = runTest {
        shopping.addToList("Apples", null, null, emptySet())
        val id = shopping.addToList("Pears", null, null, emptySet())
        catalog.update(id, "apple", null, emptySet())
    }

    @Test fun `deleting a store removes it from items`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        shopping.addToList("Rice", null, null, setOf(a))
        storeRepo.deleteStore(a)
        assertEquals(emptySet<Long>(), catalog.findByName("rice")!!.storeIds)
    }

    @Test fun `deleting a catalog item removes it from the list`() = runTest {
        val id = shopping.addToList("Bread", null, null, emptySet())
        catalog.delete(id)
        assertEquals(0, shopping.observeEntries().first().size)
        assertNull(catalog.findByName("bread"))
    }

    @Test fun `custom section order is applied per store`() = runTest {
        val store = storeRepo.addStore("Aldi", null)
        val sections = storeRepo.getSections()
        val reversed = sections.reversed().map { it.id }
        storeRepo.setSectionOrder(store, reversed)
        assertEquals(reversed, storeRepo.observeSectionOrder(store).first().map { it.id })

        storeRepo.resetSectionOrder(store)
        assertEquals(sections.map { it.id }, storeRepo.observeSectionOrder(store).first().map { it.id })
    }

    @Test fun `new stores get distinct colors and keep a chosen one`() = runTest {
        val a = storeRepo.addStore("Costco", null)
        val b = storeRepo.addStore("ShopRite", null)
        val chosen = com.yehleh.aislewise.data.StorePalette.COLORS[5]
        val c = storeRepo.addStore("Target", null, chosen)
        val colors = storeRepo.observeStores().first().associate { it.id to it.color }
        assertEquals(com.yehleh.aislewise.data.StorePalette.COLORS[0], colors[a])
        assertEquals(com.yehleh.aislewise.data.StorePalette.COLORS[1], colors[b])
        assertEquals(chosen, colors[c])
    }
}
