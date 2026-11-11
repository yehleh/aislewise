package com.yehleh.aislewise.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yehleh.aislewise.data.db.entities.Store
import com.yehleh.aislewise.data.price.PriceParser
import com.yehleh.aislewise.data.price.RelativeTime
import com.yehleh.aislewise.data.repo.ListGrouper
import com.yehleh.aislewise.data.repo.StorePrice

/** The cheapest known price for an item, naming the store when there is more than one. */
@Composable
fun BestPriceLine(stores: List<Store>, prices: Map<Long, StorePrice>, modifier: Modifier = Modifier) {
    val best = ListGrouper.cheapestStore(stores, prices) ?: return
    val (store, price) = best
    PriceText(priceText(price, store.takeIf { stores.size > 1 }?.name), modifier)
}

/** The known price at one particular store. */
@Composable
fun StorePriceLine(price: StorePrice, modifier: Modifier = Modifier) {
    PriceText(priceText(price, null), modifier)
}

@Composable
private fun PriceText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun priceText(price: StorePrice, storeName: String?): String = buildString {
    append(PriceParser.format(price.cents))
    if (storeName != null) append(" at $storeName")
    price.updatedAt?.let { append(" · ${RelativeTime.format(it)}") }
}
