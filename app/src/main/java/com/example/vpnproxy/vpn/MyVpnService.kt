package com.example.vpnproxy.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.vpnproxy.proxy.HttpProxyServer
import com.example.vpnproxy.proxy.Socks5ProxyServer

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.vpnproxy.START"
        const val ACTION_STOP = "com.example.vpnproxy.STOP"
        const val NOTIF_CHANNEL = "vpn_channel"
        const val NOTIF_ID = 1

        const val SOCKS_LISTEN_PORT = 10808
        const val HTTP_LISTEN_PORT = 10809
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val tunnelCore = TunnelCore()
    private var socksServer: Socks5ProxyServer? = null
    private var httpServer: HttpProxyServer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification())
                startVpnAndProxies(intent)
            }
        }
        return START_STICKY
    }

    private fun startVpnAndProxies(intent: Intent?) {
        val vlessLink = intent?.getStringExtra("vless_link") ?: return

        establishVpn()

        val config = tunnelCore.buildConfigFromVlessLink(vlessLink)
        vpnInterface?.let { pfd ->
            tunnelCore.start(pfd.fd, config)
        }

        socksServer = Socks5ProxyServer(
            listenPort = SOCKS_LISTEN_PORT,
            upstreamPort = tunnelCore.internalSocksPort
        ).also { it.start() }

        httpServer = HttpProxyServer(
            listenPort = HTTP_LISTEN_PORT,
            upstreamSocksPort = tunnelCore.internalSocksPort
        ).also { it.start() }
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("VpnProxyApp")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        runCatching { builder.addDisallowedApplication(packageName) }

        vpnInterface = builder.establish()
    }

    private fun stopEverything() {
        tunnelCore.stop()
        socksServer?.stop()
        httpServer?.stop()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL, "VPN Service", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MyVpnService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("VPN متصل")
            .setContentText("Proxy: 127.0.0.1:$SOCKS_LISTEN_PORT (SOCKS5) / $HTTP_LISTEN_PORT (HTTP)")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(0, "إيقاف", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }
}            upstreamSocksPort = tunnelCore.internalSocksPort
        ).also { it.start() }
    }

    private fun establishVpn() {
        val builder = Builder()
            .setSession("VpnProxyApp")
            .addAddress("10.0.0.2", 24)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .setMtu(1500)

        runCatching { builder.addDisallowedApplication(packageName) }

        vpnInterface = builder.establish()
    }

    private fun stopEverything() {
        tunnelCore.stop()
        socksServer?.stop()
        httpServer?.stop()
        runCatching { vpnInterface?.close() }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL, "VPN Service", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MyVpnService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("VPN متصل")
            .setContentText("Proxy: 127.0.0.1:$SOCKS_LISTEN_PORT (SOCKS5) / $HTTP_LISTEN_PORT (HTTP)")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(0, "إيقاف", stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopEverything()
        super.onRevoke()
    }
}
