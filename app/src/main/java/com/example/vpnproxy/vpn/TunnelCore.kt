package com.example.vpnproxy.vpn

class TunnelCore {

    val internalSocksPort = 10800

    private var running = false

    fun start(tunFd: Int, configJson: String) {
        if (running) return
        running = true
        // TODO: هنا يتم الربط الفعلي مع مكتبة libv2ray.aar لاحقًا
    }

    fun stop() {
        if (!running) return
        running = false
        // TODO: Libv2ray.stopLoop()
    }

    fun isRunning() = running

    fun buildConfig(
        serverAddress: String,
        serverPort: Int,
        userId: String,
        alterId: Int = 0
    ): String {
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
              "protocol": "vmess",
              "settings": {
                "vnext": [
                  {
                    "address": "$serverAddress",
                    "port": $serverPort,
                    "users": [ { "id": "$userId", "alterId": $alterId } ]
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()
    }
}
