package com.niagaraclone

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

// Helper to list installed apps
fun getInstalledApps(context: Context): List<ResolveInfo> {
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return context.packageManager.queryIntentActivities(mainIntent, 0)
        .sortedBy { it.loadLabel(context.packageManager).toString().lowercase() }
}

// Icon Pack Stub
class IconPackManager(private val context: Context) {
    fun getAppIcon(app: ResolveInfo): Drawable? {
        return app.loadIcon(context.packageManager)
    }
    fun setCustomIcon(packageName: String, drawableName: String) {}
}

// Icon Dialog Stub
@Composable
fun IconPickerDialog(
    app: ResolveInfo,
    iconPackManager: IconPackManager,
    onDismiss: () -> Unit,
    onIconSelected: (String) -> Unit
) {
    onDismiss()
}

// Widget Stub
@Composable
fun WidgetView(appWidgetHost: AppWidgetHost, info: AppWidgetProviderInfo) {
    Box {
        Text("Widget: ${info.label}")
    }
}
