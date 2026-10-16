package io.github.tomsadeh.puki.brain

import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTPS to the family PC. Its certificate is self-signed (brain/tls.py), so instead of a
 * certificate authority the app trusts exactly one certificate, by its SHA-256 fingerprint:
 * the one in the pairing QR code, or, when the address was typed in by hand, the one it first
 * saw (remembered in the settings). Any other certificate is refused before anything is sent,
 * so the pairing code, the child's name and her recordings only ever go to the paired PC.
 */
object PinnedTls {
    /** The PC answered with a different certificate than the paired one. */
    class WrongPc : CertificateException("not the paired PC")

    /** SHA-256 of the certificate (DER), lowercase hex, as brain/tls.py computes it. */
    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    /**
     * Opens [url] over HTTPS. With a [pin], only that certificate is accepted (else [WrongPc]
     * is thrown on connecting); with none, any certificate is, and [seen] gets its fingerprint
     * so the caller can pin it once the PC has accepted the pairing code.
     */
    fun open(url: String, pin: String, seen: (String) -> Unit = {}): HttpsURLConnection {
        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.sslSocketFactory = if (pin.isEmpty()) firstUse(seen) else pinned.getOrPut(pin) { factory(pin, {}) }
        // The certificate names no host (a home PC's address changes); the fingerprint is the identity
        conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
        return conn
    }

    private val pinned = ConcurrentHashMap<String, SSLSocketFactory>()

    private fun firstUse(seen: (String) -> Unit) = factory("", seen)

    private fun factory(pin: String, seen: (String) -> Unit): SSLSocketFactory {
        val trust = object : X509TrustManager {
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val fp = fingerprint(chain.firstOrNull() ?: throw CertificateException("no certificate"))
                if (pin.isNotEmpty() && !MessageDigest.isEqual(fp.toByteArray(), pin.toByteArray())) throw WrongPc()
                seen(fp)
            }

            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                throw CertificateException("not a server")

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }.socketFactory
    }
}
