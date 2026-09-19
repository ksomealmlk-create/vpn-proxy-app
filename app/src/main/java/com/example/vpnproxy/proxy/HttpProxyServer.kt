package com.example.vpnproxy.proxy

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class HttpProxyServer(
    private val listenPort: Int,
    private val bindAddress: String = "0.0.0.0",
    private val upstreamSocksHost: String = "127.0.0.1",
    private val upstreamSocksPort: Int
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        serverSocket = ServerSocket(listenPort, 50, InetAddress.getByName(bindAddress))
        scope.launch {
            while (running) {
                val client = try { serverSocket?.accept() } catch (e: Exception) { null } ?: break
                launch { handleClient(client) }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        scope.coroutineContext.cancelChildren()
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        client.use { sock ->
            try {
                val input = sock.getInputStream()
                val reader = BufferedReader(InputStreamReader(input))
                val requestLine = reader.readLine() ?: return@withContext
                val parts = requestLine.split(" ")
                if (parts.size < 3) return@withContext
                val method = parts[0]
                val target = parts[1]

                val headers = mutableMapOf<String, String>()
                var line: String?
                while (true) {
                    line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                    val idx = line.indexOf(":")
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }

                if (method.equals("CONNECT", ignoreCase = true)) {
                    val (host, port) = target.split(":").let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 443) }
                    connectViaSocks(host, port)?.let { upstream ->
                        val output = sock.getOutputStream()
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                        output.flush()
                        relayBoth(input, output, upstream)
                    } ?: run {
                        sock.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                    }
                } else {
                    val hostHeader = headers["host"] ?: return@withContext
                    val (host, port) = if (hostHeader.contains(":"))
                        hostHeader.split(":").let { it[0] to it[1].toInt() }
                    else hostHeader to 80

                    connectViaSocks(host, port)?.let { upstream ->
                        val upOut = upstream.getOutputStream()
                        upOut.write("$requestLine\r\n".toByteArray())
                        headers.forEach { (k, v) -> upOut.write("$k: $v\r\n".toByteArray()) }
                        upOut.write("\r\n".toByteArray())
                        upOut.flush()

                        relayBoth(input, sock.getOutputStream(), upstream)
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun connectViaSocks(host: String, port: Int): Socket? {
        return try {
            val upstream = Socket(upstreamSocksHost, upstreamSocksPort)
            val out = upstream.getOutputStream()
            val inp = upstream.getInputStream()

            out.write(byteArrayOf(0x05, 0x01, 0x00)); out.flush()
            val greetReply = ByteArray(2); inp.read(greetReply)

            val hostBytes = host.toByteArray()
            val req = mutableListOf<Byte>(0x05, 0x01, 0x00, 0x03)
            req.add(hostBytes.size.toByte())
            req.addAll(hostBytes.toList())
            req.add(((port shr 8) and 0xFF).toByte())
            req.add((port and 0xFF).toByte())
            out.write(req.toByteArray()); out.flush()

            val replyHeader = ByteArray(4); inp.read(replyHeader)
            val skip = when (replyHeader[3].toInt()) {
                0x01 -> 4 + 2
                0x03 -> inp.read() + 2
                0x04 -> 16 + 2
                else -> 6
            }
            val rest = ByteArray(skip); inp.read(rest)

            upstream
        } catch (e: Exception) {
            null
        }
    }

    private fun relayBoth(clientIn: java.io.InputStream, clientOut: OutputStream, upstream: Socket) {
        val upIn = upstream.getInputStream()
        val upOut = upstream.getOutputStream()
        val job1 = scope.launch { pipe(clientIn, upOut) }
        val job2 = scope.launch { pipe(upIn, clientOut) }
        runBlocking { joinAll(job1, job2) }
        upstream.close()
    }

    private fun pipe(input: java.io.InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: Exception) { }
    }
}
