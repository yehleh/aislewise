package com.yehleh.aislewise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yehleh.aislewise.data.StorePalette
import com.yehleh.aislewise.data.db.entities.Store

fun Store.badgeColor(): Color = Color(StorePalette.colorOf(this))

fun Store.onBadgeColor(): Color = Color(StorePalette.onColor(StorePalette.colorOf(this)))

/** A store name on its own color, with text that stays readable on light and dark colors. */
@Composable
fun StoreBadge(store: Store, modifier: Modifier = Modifier) {
    Text(
        store.name,
        style = MaterialTheme.typography.labelSmall,
        color = store.onBadgeColor(),
        maxLines = 1,
        modifier = modifier
            .background(store.badgeColor(), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreBadges(stores: List<Store>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        stores.forEach { StoreBadge(it) }
    }
}

@Composable
fun StoreDot(store: Store, size: Dp = 10.dp) {
    Box(Modifier.size(size).background(store.badgeColor(), CircleShape))
}

/** Palette swatches; the selected one shows a check in its readable text color. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatches(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StorePalette.COLORS.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .clickable(role = Role.RadioButton, onClickLabel = "Choose color") { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(StorePalette.onColor(argb)))
            }
        }
    }
}
