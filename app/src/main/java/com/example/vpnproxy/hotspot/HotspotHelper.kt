package com.example.vpnproxy.hotspot

import android.content.Context
import android.content.Intent
import android.provider.Settings

class HotspotHelper(private val context: Context) {

    fun openHotspotSettings() {
        val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun getLikelyHotspotIp(): String {
        return "192.168.43.1"
    }
}
