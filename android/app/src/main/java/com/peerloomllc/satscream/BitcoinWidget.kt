package com.peerloomllc.satscream

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews

class BitcoinWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update all widget instances
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Widget was resized - update with new dimensions
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onEnabled(context: Context) {
        // First widget instance created
    }

    override fun onDisabled(context: Context) {
        // Last widget instance removed
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Get shared preferences
            val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
            val price = prefs.getFloat(Prefs.LAST_PRICE, 0f).toDouble()
            val isBitcoinStandardMode = prefs.getBoolean(Prefs.BITCOIN_STANDARD_MODE, false)
            val isDarkMode = prefs.getBoolean(Prefs.DARK_MODE, false)
            val isTransparent = prefs.getBoolean(Prefs.WIDGET_TRANSPARENT, false)

            // Get widget dimensions for dynamic text sizing
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            // Calculate text size to be at least 50% of the smaller dimension
            val smallerDimension = minOf(minWidth, minHeight)
            val textSize = (smallerDimension * 0.5f).coerceAtLeast(24f)  // Minimum 24sp

            // Create RemoteViews. Transparent mode uses a dedicated layout whose text carries a
            // strong opaque halo so it stays legible on any wallpaper; the themed layout keeps a
            // crisp container.
            val views = RemoteViews(
                context.packageName,
                if (isTransparent) R.layout.widget_layout_transparent else R.layout.widget_layout
            )

            // Format and set price based on mode
            val priceText = if (isBitcoinStandardMode) {
                // Bitcoin Standard Mode: Show sats per dollar
                BtcPrice.formatSatsPerDollar(price)
            } else {
                // Fiat Mode: Show USD price
                BtcPrice.formatUsd(price)
            }

            views.setTextViewText(R.id.tvWidgetPrice, priceText)

            // Set dynamic text size based on widget dimensions (50% of smaller dimension)
            views.setTextViewTextSize(R.id.tvWidgetPrice, TypedValue.COMPLEX_UNIT_SP, textSize)

            if (isTransparent) {
                // No container — force white glyphs; the layout's dark halo provides contrast on
                // any wallpaper.
                views.setTextColor(R.id.tvWidgetPrice, Color.WHITE)
            } else {
                // Theme-aware text color (hardcoded to avoid resource lookup issues) over a
                // theme-aware rounded background. The static drawable avoids allocating a fresh
                // 400x400 ARGB_8888 bitmap on every widget update.
                val textPrimaryColor = if (isDarkMode) {
                    Color.parseColor("#E0E0E0")  // text_primary_dark
                } else {
                    Color.parseColor("#212121")  // text_primary_light
                }
                views.setTextColor(R.id.tvWidgetPrice, textPrimaryColor)
                views.setImageViewResource(
                    R.id.widgetBackground,
                    if (isDarkMode) R.drawable.widget_background_dark else R.drawable.widget_background_light
                )
            }

            // Create intent to launch MainActivity when widget is clicked
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.tvWidgetPrice, pendingIntent)

            // Update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        // Helper function for BitcoinService to update all widgets
        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, BitcoinWidget::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            // REMOVED the extra } that was here!

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, BitcoinWidget::class.java)
            )

            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        }
    }
}