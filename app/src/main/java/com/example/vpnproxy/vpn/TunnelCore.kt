ppackage com.example.vpnproxy.vpn

import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

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
            controller?.startLoop(configJson, tunFd.toLong())
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
