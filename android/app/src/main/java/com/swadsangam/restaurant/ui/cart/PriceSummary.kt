package com.swadsangam.restaurant.ui.cart

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.Quote

/** Every charge is disclosed separately (FR-06). */
@Composable
fun PriceSummary(q: Quote, coupon: String?, verified: Boolean) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Line(stringResource(R.string.subtotal), Money.format(q.subtotalPaise))
        if (q.discountPaise > 0) Line(stringResource(R.string.discount_code, coupon ?: ""), "−" + Money.format(q.discountPaise))
        if (q.taxPaise > 0) Line("${q.taxLabel} (${q.taxRateBps / 100.0}%)", Money.format(q.taxPaise))
        if (q.packagingFeePaise > 0) Line(stringResource(R.string.packaging_fee), Money.format(q.packagingFeePaise))
        if (q.deliveryFeePaise > 0) Line(stringResource(R.string.delivery_fee), Money.format(q.deliveryFeePaise))
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Line(stringResource(R.string.total_payable), Money.format(q.totalPaise), bold = true)
        if (q.pricesIncludeTax) {
            Text(stringResource(R.string.prices_include_tax), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!verified) {
            Text(stringResource(R.string.total_confirmed_at_checkout), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else null)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}
