package com.blehmen.alarmnosnooze.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Opens "Display over other apps" for this app (Samsung calls it "Appear on top"). */
fun openOverlaySettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
