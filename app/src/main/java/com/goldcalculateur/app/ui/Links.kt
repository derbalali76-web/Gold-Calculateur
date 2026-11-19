package com.goldcalculateur.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens external pages. Nothing here sends calculator data anywhere. */
object Links {
    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // No handler installed: ignore silently.
        }
    }

    fun openUrl(context: Context, url: String) {
        if (url.isBlank()) return
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    fun sendEmail(context: Context, address: String) {
        if (address.isBlank()) return
        start(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")))
    }

    fun rateApp(context: Context) {
        val id = context.packageName
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$id")
        }
    }
}
