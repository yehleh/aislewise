package com.yehleh.aislewise.feature.scan

import com.yehleh.aislewise.data.db.entities.Section
import com.yehleh.aislewise.data.repo.CatalogRepository
import javax.inject.Inject

/** A parsed item prepared for review: matched against what the app already knows. */
data class ReviewRow(
    val key: Int,
    val name: String,
    val quantity: String,
    val sectionId: Long?,
    val storeIds: Set<Long>,
    val include: Boolean = true,
    /** True when the item was found in the catalog, so its stores came from memory. */
    val known: Boolean,
)

class ScanMatcher @Inject constructor(private val catalog: CatalogRepository) {

    suspend fun match(items: List<ParsedItem>, sections: List<Section>, fallbackSectionId: Long?): List<ReviewRow> {
        val sectionByName = sections.associateBy { it.name.lowercase() }
        return items.mapIndexed { index, item ->
            val known = catalog.findByName(item.name)
            if (known != null) {
                ReviewRow(
                    key = index,
                    name = known.name,
                    quantity = item.quantity.orEmpty(),
                    sectionId = known.sectionId,
                    storeIds = known.storeIds,
                    known = true,
                )
            } else {
                ReviewRow(
                    key = index,
                    name = item.name,
                    quantity = item.quantity.orEmpty(),
                    sectionId = item.suggestedSection?.lowercase()?.let { sectionByName[it]?.id } ?: fallbackSectionId,
                    storeIds = emptySet(),
                    known = false,
                )
            }
        }
    }
}
