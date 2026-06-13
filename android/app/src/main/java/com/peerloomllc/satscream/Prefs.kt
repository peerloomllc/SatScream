package com.peerloomllc.satscream

/**
 * Single source of truth for the SharedPreferences file name and all keys.
 *
 * The app uses SharedPreferences as a lightweight IPC bus between [BitcoinService],
 * [MainActivity], [BitcoinWidget], and [AudioSettingsActivity]. Centralizing the keys here
 * keeps that contract in one place so a rename can't silently desync the writer and reader.
 *
 * NOTE: the string values are the on-disk contract — changing one is a data migration, not a
 * rename. Keep them exactly as they are unless you intend to migrate stored prefs.
 */
object Prefs {
    const val FILE = "BitcoinPrefs"

    const val LAST_PRICE = "LAST_PRICE"
    const val LAST_UPDATE_TIME = "LAST_UPDATE_TIME"

    const val PUMP_TARGET = "TARGET_PRICE_PUMP"
    const val DUMP_TARGET = "TARGET_PRICE_DUMP"
    const val PUMP_TRIGGERED = "PUMP_ALERT_TRIGGERED"
    const val DUMP_TRIGGERED = "DUMP_ALERT_TRIGGERED"
    const val PUMP_IS_BITCOIN_MODE = "PUMP_ALERT_IS_BITCOIN_MODE"
    const val DUMP_IS_BITCOIN_MODE = "DUMP_ALERT_IS_BITCOIN_MODE"

    const val BITCOIN_STANDARD_MODE = "BITCOIN_STANDARD_MODE"
    const val DARK_MODE = "DARK_MODE"
    const val WELCOME_SHOWN = "WELCOME_SHOWN"

    const val CUSTOM_PUMP_AUDIO_PATH = "CUSTOM_PUMP_AUDIO_PATH"
    const val CUSTOM_DUMP_AUDIO_PATH = "CUSTOM_DUMP_AUDIO_PATH"
    const val CUSTOM_PUMP_AUDIO_NAME = "CUSTOM_PUMP_AUDIO_NAME"
    const val CUSTOM_DUMP_AUDIO_NAME = "CUSTOM_DUMP_AUDIO_NAME"
}
