package com.swadsangam.restaurant.core

import java.text.NumberFormat
import java.util.Locale

/** Prices are integer paise everywhere (PRD §10.1). */
object Money {
    private val inr: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
    }

    fun format(paise: Int): String {
        val rupees = paise / 100.0
        val f = inr.clone() as NumberFormat
        f.minimumFractionDigits = if (paise % 100 == 0) 0 else 2
        return "₹" + f.format(rupees)
    }
}

object PhoneValidator {
    /** Accepts 10-digit Indian mobile numbers with optional +91 / 0 prefix. */
    fun normalize(input: String): String? {
        var d = input.filter { it.isDigit() }
        if (d.length == 12 && d.startsWith("91")) d = d.substring(2)
        if (d.length == 11 && d.startsWith("0")) d = d.substring(1)
        return if (d.length == 10 && d[0] in '6'..'9') d else null
    }
}
