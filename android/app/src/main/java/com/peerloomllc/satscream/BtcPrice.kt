package com.peerloomllc.satscream

import java.util.Locale

/**
 * Bitcoin price formatting and Fiat <-> Bitcoin-Standard conversion helpers.
 *
 * Centralizes the price math/formatting that was previously copy-pasted across
 * [MainActivity], [BitcoinService], and [BitcoinWidget].
 */
object BtcPrice {

    /** Satoshis per whole bitcoin. */
    const val SATS_PER_BTC = 100_000_000.0

    /** Whole-dollar USD with thousands separators, e.g. 96500.50 -> "$96,500". */
    fun formatUsd(price: Double): String =
        String.format(Locale.US, "$%,d", price.toLong())

    /** Sats per dollar for a given USD/BTC price, truncated to a whole number. */
    fun satsPerDollar(price: Double): Long =
        (SATS_PER_BTC / price).toLong()

    /** Sats-per-dollar with thousands separators and a "/$" suffix, e.g. "1,034/$". */
    fun formatSatsPerDollar(price: Double): String =
        String.format(Locale.US, "%,d/$", satsPerDollar(price))
}
