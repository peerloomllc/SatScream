package com.peerloomllc.satscream

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import java.util.Locale

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
            val prefs = context.getSharedPreferences("BitcoinPrefs", Context.MODE_PRIVATE)
            val price = prefs.getFloat("LAST_PRICE", 0f).toDouble()
            val isBitcoinStandardMode = prefs.getBoolean("BITCOIN_STANDARD_MODE", false)
            val isDarkMode = prefs.getBoolean("DARK_MODE", false)

            // Get widget dimensions for dynamic text sizing
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            // Calculate text size to be at least 50% of the smaller dimension
            val smallerDimension = minOf(minWidth, minHeight)
            val textSize = (smallerDimension * 0.5f).coerceAtLeast(24f)  // Minimum 24sp

            // Create RemoteViews
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Format and set price based on mode
            val priceText = if (isBitcoinStandardMode) {
                // Bitcoin Standard Mode: Show sats per dollar
                val satsPerDollar = (100_000_000.0 / price).toLong()
                String.format(Locale.US, "%,d/$", satsPerDollar)
            } else {
                // Fiat Mode: Show USD price
                val wholePrice = price.toLong()
                String.format(Locale.US, "$%,d", wholePrice)
            }

            views.setTextViewText(R.id.tvWidgetPrice, priceText)

            // Set dynamic text size based on widget dimensions (50% of smaller dimension)
            views.setTextViewTextSize(R.id.tvWidgetPrice, TypedValue.COMPLEX_UNIT_SP, textSize)

            // Set theme-aware colors using hardcoded values to avoid resource lookup issues
            val textPrimaryColor = if (isDarkMode) {
                Color.parseColor("#E0E0E0")  // text_primary_dark
            } else {
                Color.parseColor("#212121")  // text_primary_light
            }

            val backgroundColor = if (isDarkMode) {
                Color.parseColor("#121212")  // background_main dark
            } else {
                Color.parseColor("#FFFFFF")  // background_main light
            }

            views.setTextColor(R.id.tvWidgetPrice, textPrimaryColor)

            // Create a shape drawable with rounded corners (no border)
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            shape.setColor(backgroundColor)
            shape.cornerRadius = 32f  // Rounded corners

            // Create bitmap with proper aspect ratio for square shape
            val size = 400  // Square size for proper rounded corners
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            shape.setBounds(0, 0, canvas.width, canvas.height)
            shape.draw(canvas)

            views.setImageViewBitmap(R.id.widgetBackground, bitmap)

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