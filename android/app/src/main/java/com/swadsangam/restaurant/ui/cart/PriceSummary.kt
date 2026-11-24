package com.swadsangam.restaurant.ui.cart

import com.swadsangam.restaurant.ui.components.Tone
import com.swadsangam.restaurant.ui.components.StatusBanner
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.swadsangam.restaurant.R
import com.swadsangam.restaurant.core.Money
import com.swadsangam.restaurant.domain.Quote
import com.swadsangam.restaurant.ui.components.AppCard
import com.swadsangam.restaurant.ui.components.KeyValueRow
import com.swadsangam.restaurant.ui.components.SectionDivider
import com.swadsangam.restaurant.ui.theme.AppTheme
import com.swadsangam.restaurant.ui.theme.Spacing
import com.swadsangam.restaurant.ui.theme.secondaryText

/** Bill details card — every charge is disclosed separately (FR-06). */
@Composable
fun PriceSummary(q: Quote, coupon: String?, verified: Boolean,
                 modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text(stringResource(R.string.bill_details), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.s))
        KeyValueRow(stringResource(R.string.subtotal), Money.format(q.subtotalPaise))
        if (q.discountPaise > 0) KeyValueRow(stringResource(R.string.discount_code, coupon ?: ""), "−" + Money.format(q.discountPaise), valueColor = AppTheme.colors.success)
        if (q.taxPaise > 0) KeyValueRow("${q.taxLabel} (${q.taxRateBps / 100.0}%)", Money.format(q.taxPaise))
        if (q.packagingFeePaise > 0) KeyValueRow(stringResource(R.string.packaging_fee), Money.format(q.packagingFeePaise))
        if (q.deliveryFeePaise > 0) KeyValueRow(stringResource(R.string.delivery_fee), Money.format(q.deliveryFeePaise))
        SectionDivider()
        KeyValueRow(stringResource(R.string.total_payable), Money.format(q.totalPaise), emphasize = true)
        if (q.discountPaise > 0) {
            Spacer(Modifier.height(Spacing.s))
            StatusBanner(stringResource(R.string.savings, Money.format(q.discountPaise)), tone = Tone.Success)
        }
        if (q.pricesIncludeTax) Text(stringResource(R.string.prices_include_tax), style = MaterialTheme.typography.bodySmall, color = secondaryText)
        if (!verified) Text(stringResource(R.string.total_confirmed_at_checkout), style = MaterialTheme.typography.bodySmall, color = secondaryText)
    }
}
