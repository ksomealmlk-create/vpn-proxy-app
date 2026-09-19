package com.example.vpnproxy.vpn

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.net.URI
import java.net.URLDecoder

class TunnelCore {

    val internalSocksPort = 10800

    private var controller: CoreController? = null
    private var running = false

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    fun start(tunFd: Int, configJson: String) {
        if (running) return
        try {
            controller = Libv2ray.newCoreController(callbackHandler)
            controller?.startLoop(configJson, tunFd)
            running = true
        } catch (e: Exception) {
            running = false
        }
    }

    fun stop() {
        if (!running) return
        try {
            controller?.stopLoop()
        } catch (e: Exception) {
        }
        running = false
    }

    fun isRunning() = running

    fun buildConfigFromVlessLink(link: String): String {
        val uri = URI(link)
        val uuid = uri.userInfo
        val address = uri.host
        val port = if (uri.port != -1) uri.port else 443

        val params = mutableMapOf<String, String>()
        uri.query?.split("&")?.forEach { pair ->
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                params[key] = value
            }
        }

        val network = params["type"] ?: "tcp"
        val security = params["security"] ?: "none"
        val path = params["path"] ?: "/"
        val host = params["host"] ?: ""
        val encryption = params["encryption"] ?: "none"

        val streamSettings = StringBuilder()
        streamSettings.append("""{ "network": "$network", "security": "$security"""")
        if (network == "ws") {
            streamSettings.append(""", "wsSettings": { "path": "$path", "headers": { "Host": "$host" } }""")
        }
        streamSettings.append(" }")

        return """
        {
          "inbounds": [
            {
              "port": $internalSocksPort,
              "listen": "127.0.0.1",
              "protocol": "socks",
              "settings": { "auth": "noauth", "udp": true }
            }
          ],
          "outbounds": [
            {
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "$address",
                    "port": $port,
                    "users": [ { "id": "$uuid", "encryption": "$encryption" } ]
                  }
                ]
              },
              "streamSettings": $streamSettings
            }
          ]
        }
        """.trimIndent()
    }
}
