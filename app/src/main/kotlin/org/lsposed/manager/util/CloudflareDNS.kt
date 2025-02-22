package org.lsposed.manager.util

import android.os.Build
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.internal.platform.Platform
import org.lsposed.manager.App
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.UnknownHostException
import java.util.List
import javax.net.ssl.X509TrustManager

class CloudflareDNS : Dns {
    var DoH: Boolean = App.preferences.getBoolean("doh", false)
    var noProxy: Boolean = ProxySelector.getDefault().select(url.toUri()).get(0) === Proxy.NO_PROXY
    private val cloudflare: Dns

    init {
        val trustManager: X509TrustManager = Platform.get().platformTrustManager()
        var tls = ConnectionSpec.Companion.RESTRICTED_TLS
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            tls = ConnectionSpec.Builder(tls)
                .supportsTlsExtensions(false)
                .build()
        }
        val builder = DnsOverHttps.Builder()
            .resolvePrivateAddresses(true)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .client(
                OkHttpClient.Builder()
                    .cache(App.okHttpCache)
                    .sslSocketFactory(NoSniFactory(), trustManager)
                    .connectionSpecs(List.of<ConnectionSpec?>(tls))
                    .build()
            )
        try {
            builder.bootstrapDnsHosts(
                List.of<InetAddress?>(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    InetAddress.getByName("2606:4700:4700::1111"),
                    InetAddress.getByName("2606:4700:4700::1001")
                )
            )
        } catch (ignored: UnknownHostException) {
        }
        cloudflare = builder.build()
    }

    @Throws(UnknownHostException::class)
    override fun lookup(hostname: String): kotlin.collections.List<InetAddress> {
        if (DoH && noProxy) {
            return cloudflare.lookup(hostname)
        } else {
            return Dns.Companion.SYSTEM.lookup(hostname)
        }
    }

    companion object {
        private val url = "https://cloudflare-dns.com/dns-query".toHttpUrl()
    }
}
