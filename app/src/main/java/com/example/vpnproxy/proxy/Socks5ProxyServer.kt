package com.example.vpnproxy.proxy

import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class Socks5ProxyServer(
    private val listenPort: Int,
    private val bindAddress: String = "0.0.0.0",
    private val upstreamHost: String = "127.0.0.1",
    private val upstreamPort: Int
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
                val client = try {
                    serverSocket?.accept()
                } catch (e: Exception) {
                    null
                } ?: break
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
                val output = sock.getOutputStream()

                val ver = input.read()
                if (ver != 0x05) return@withContext
                val nMethods = input.read()
                val methods = ByteArray(nMethods)
                readFully(input, methods)
                output.write(byteArrayOf(0x05, 0x00))
                output.flush()

                val reqVer = input.read()
                val cmd = input.read()
                input.read()
                val addrType = input.read()

                val targetHost: String
                when (addrType) {
                    0x01 -> {
                        val b = ByteArray(4); readFully(input, b)
                        targetHost = InetAddress.getByAddress(b).hostAddress
                    }
                    0x03 -> {
                        val len = input.read()
                        val b = ByteArray(len); readFully(input, b)
                        targetHost = String(b)
                    }
                    0x04 -> {
                        val b = ByteArray(16); readFully(input, b)
                        targetHost = InetAddress.getByAddress(b).hostAddress
                    }
                    else -> return@withContext
                }
                val portBytes = ByteArray(2); readFully(input, portBytes)
                val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

                if (cmd != 0x01) {
                    sendReply(output, 0x07)
                    return@withContext
                }

                val upstream = try {
                    Socket(upstreamHost, upstreamPort)
                } catch (e: Exception) {
                    sendReply(output, 0x01)
                    return@withContext
                }

                upstream.use { up ->
                    val upIn = up.getInputStream()
                    val upOut = up.getOutputStream()

                    upOut.write(byteArrayOf(0x05, 0x01, 0x00)); upOut.flush()
                    val upGreetReply = ByteArray(2); readFully(upIn, upGreetReply)

                    val hostBytes = targetHost.toByteArray()
                    val req = mutableListOf<Byte>(0x05, 0x01, 0x00, 0x03)
                    req.add(hostBytes.size.toByte())
                    req.addAll(hostBytes.toList())
                    req.add(((targetPort shr 8) and 0xFF).toByte())
                    req.add((targetPort and 0xFF).toByte())
                    upOut.write(req.toByteArray()); upOut.flush()

                    val upReplyHeader = ByteArray(4); readFully(upIn, upReplyHeader)
                    val skipLen = when (upReplyHeader[3].toInt()) {
                        0x01 -> 4 + 2
                        0x03 -> (upIn.read() + 2).also { }
                        0x04 -> 16 + 2
                        else -> 6
                    }
                    if (upReplyHeader[3].toInt() != 0x03) {
                        val rest = ByteArray(skipLen); readFully(upIn, rest)
                    }

                    sendReply(output, 0x00)

                    val job1 = scope.launch { pipe(input, upOut) }
                    val job2 = scope.launch { pipe(upIn, output) }
                    joinAll(job1, job2)
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun sendReply(output: OutputStream, status: Int) {
        output.write(byteArrayOf(0x05, status.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw java.io.IOException("Stream closed")
            offset += read
        }
    }

    private fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (e: Exception) {
        }
    }
}
