package org.lsposed.manager.util

import android.net.SSLCertificateSocketFactory
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

class NoSniFactory : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String?>? {
        return defaultFactory.getDefaultCipherSuites()
    }

    override fun getSupportedCipherSuites(): Array<String?>? {
        return defaultFactory.getSupportedCipherSuites()
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket? {
        return config(defaultFactory.createSocket(s, host, port, autoClose))
    }

    @Throws(IOException::class)
    override fun createSocket(host: String?, port: Int): Socket? {
        return config(defaultFactory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket? {
        return config(defaultFactory.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress?, port: Int): Socket? {
        return config(defaultFactory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket? {
        return config(defaultFactory.createSocket(address, port, localAddress, localPort))
    }

    private fun config(socket: Socket?): Socket? {
        try {
            openSSLSocket.setHostname(socket, null)
            openSSLSocket.setUseSessionTickets(socket, true)
        } catch (ignored: IllegalArgumentException) {
        }
        return socket
    }

    companion object {
        private val defaultFactory = getDefault() as SSLSocketFactory

        @Suppress("deprecation")
        private val openSSLSocket = SSLCertificateSocketFactory
            .getDefault(1000) as SSLCertificateSocketFactory
    }
}
