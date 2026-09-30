package com.satcop.smartvisitor.kiosk.ui.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.satcop.smartvisitor.kiosk.MainActivity
import com.satcop.smartvisitor.kiosk.data.model.HostFeedItem

/** System notification for a new pending visitor (high importance, heads-up). */
object HostAlertNotifier {
    const val CHANNEL_ID = "host_new_visitor"
    const val EXTRA_OPEN_VISIT_ID = "openVisitId"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New visitor requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when a visitor is waiting for your approval"
                enableVibration(true)
            },
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun needsPermissionRequest(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission") // guarded by canPost()
    fun show(context: Context, item: HostFeedItem) {
        ensureChannel(context)
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_VISIT_ID, item.visitId ?: "")
        }
        val pi = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val name = item.visitorName ?: "A visitor"
        val text = listOfNotNull(item.purpose, item.gateLabel).filter { it.isNotBlank() }.joinToString(" · ")
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New visitor: $name")
            .setContentText(text.ifBlank { "Waiting for your approval" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).notify(item.id.hashCode(), n)
        }
    }
}
