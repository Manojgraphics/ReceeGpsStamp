package com.receegpsstamp.data.util

import java.util.Locale

/**
 * Flex / signage area is always priced in square feet. Dimensions entered in inches convert via
 * 1 sq.ft = 144 sq.in. Pure math (no Android) so it can be unit-tested — see MediaMathTest.
 */
object MediaMath {

    /** Total area in sq.ft. `unit` is "ft" (no conversion) or "in" (÷144). */
    fun areaSqFt(width: Float, height: Float, qty: Int, unit: String): Float =
        if (unit == "in") (width * height * qty) / 144f else (width * height * qty)

    /** Compact area label — drops trailing zeros: "24", "1.5", "12.25". */
    fun formatArea(sqFt: Float): String =
        String.format(Locale.US, "%.2f", sqFt).trimEnd('0').trimEnd('.')
}
