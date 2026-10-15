package com.abeswastaken.protontodo.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** What an item is about, shown as a small icon in front of its title. */
enum class Category(val label: String, val icon: ImageVector) {
    CAR("Car", Icons.Outlined.DirectionsCar),
    HEALTH("Health", Icons.Outlined.LocalHospital),
    MONEY("Money", Icons.Outlined.AttachMoney),
    HOME("Home", Icons.Outlined.Home),
    WORK("Work", Icons.Outlined.Work),
    BIRTHDAY("Birthday", Icons.Outlined.Cake),
    TRAVEL("Travel", Icons.Outlined.Flight),
    SHOPPING("Shopping", Icons.Outlined.ShoppingCart),
    SCHOOL("School", Icons.Outlined.School),
    PETS("Pets", Icons.Outlined.Pets);

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name }
    }
}

/** The important mark and category icon that sit in front of a title. Draws nothing when neither is set. */
@Composable
fun Markers(important: Boolean, category: String?) {
    val cat = Category.of(category)
    if (!important && cat == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (important) Icon(Icons.Rounded.PriorityHigh, contentDescription = "Important", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        if (cat != null) Icon(cat.icon, contentDescription = cat.label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
    }
}
