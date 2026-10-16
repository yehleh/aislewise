package com.yehleh.aislewise.data

import com.yehleh.aislewise.data.db.dao.ListEntryRow
import com.yehleh.aislewise.data.db.entities.ItemStore
import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.db.entities.StoreSectionOrder
import com.yehleh.aislewise.data.repo.ListGrouper
import org.junit.Assert.assertEquals
import org.junit.Test

class ListGrouperTest {
    private val produce = Section(1, "Produce", 0)
    private val dairy = Section(2, "Dairy", 1)
    private val snacks = Section(3, "Snacks", 2)
    private val sections = listOf(produce, dairy, snacks)
    private val costco = Store(10, "Costco", sortIndex = 0)
    private val shoprite = Store(11, "ShopRite", sortIndex = 1)
    private val stores = listOf(costco, shoprite)

    private fun entry(id: Long, name: String, section: Section?, checked: Boolean = false) =
        ListEntryRow(id, itemId = id, name = name, quantity = null, note = null, checked = checked, addedAt = 0,
            sectionId = section?.id, sectionName = section?.name, sectionDefaultOrder = section?.defaultOrder)

    private val entries = listOf(
        entry(1, "Milk", dairy),
        entry(2, "Apples", produce),
        entry(3, "Chips", snacks),
        entry(4, "Batteries", null),
        entry(5, "Bananas", produce, checked = true),
    )
    private val itemStores = listOf(
        ItemStore(1, costco.id), ItemStore(1, shoprite.id),
        ItemStore(2, shoprite.id),
        ItemStore(3, costco.id),
        // 4 and 5 have no store: available anywhere.
    )

    private fun names(storeFilter: Long?, orders: List<StoreSectionOrder> = emptyList()) =
        ListGrouper.group(entries, itemStores, stores, sections, orders, storeFilter)
            .map { g -> (g.section?.name ?: "Unsorted") to g.items.map { it.entry.name } }

    @Test fun `all view groups every entry by default section order, unsorted last, checked items last`() {
        assertEquals(
            listOf(
                "Produce" to listOf("Apples", "Bananas"),
                "Dairy" to listOf("Milk"),
                "Snacks" to listOf("Chips"),
                "Unsorted" to listOf("Batteries"),
            ),
            names(null),
        )
    }

    @Test fun `store view keeps items sold there plus items with no store`() {
        assertEquals(
            listOf(
                "Produce" to listOf("Bananas"),
                "Dairy" to listOf("Milk"),
                "Snacks" to listOf("Chips"),
                "Unsorted" to listOf("Batteries"),
            ),
            names(costco.id),
        )
    }

    @Test fun `store view follows that store's aisle order`() {
        val orders = listOf(
            StoreSectionOrder(shoprite.id, snacks.id, 0),
            StoreSectionOrder(shoprite.id, dairy.id, 1),
            StoreSectionOrder(shoprite.id, produce.id, 2),
        )
        assertEquals(listOf("Dairy", "Produce", "Unsorted"), names(shoprite.id, orders).map { it.first })
        // Another store's order doesn't leak into this one.
        assertEquals(listOf("Produce", "Dairy", "Snacks", "Unsorted"), names(costco.id, orders).map { it.first })
    }

    @Test fun `counts include unchecked entries available at each store`() {
        assertEquals(mapOf(costco.id to 3, shoprite.id to 3), ListGrouper.countsByStore(entries, itemStores, stores))
    }
}
