package com.example.satscream

data class BitcoinResponse(
    val bpi: Bpi
)

data class Bpi(
    val USD: Currency
)

data class Currency(
    val rate: String,
    val rate_float: Double
)