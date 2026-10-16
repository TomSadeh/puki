package io.github.tomsadeh.puki.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.URI
import java.net.URLDecoder

/** Where the PC is and its pairing code, as read from the QR code on the brain's dashboard. */
data class PairingLink(val host: String, val code: String)

private val HOST = Regex("^[A-Za-z0-9.-]{1,253}:([0-9]{1,5})$")
private val CODE = Regex("^[A-Z0-9]{8,20}$")

/** Reads `puki://pair?host=<ip>:<port>&code=<CODE>`; anything else (or a bad host or code) is null. */
fun parsePairingLink(text: String?): PairingLink? {
    val uri = runCatching { URI(text?.trim() ?: return null) }.getOrNull() ?: return null
    if (!uri.scheme.equals("puki", ignoreCase = true) || !uri.host.equals("pair", ignoreCase = true)) return null
    val params = (uri.rawQuery ?: return null).split("&").mapNotNull { pair ->
        val i = pair.indexOf('=')
        if (i <= 0) null
        else runCatching { pair.substring(0, i) to URLDecoder.decode(pair.substring(i + 1), "UTF-8") }.getOrNull()
    }.toMap()
    val host = params["host"]?.trim() ?: return null
    val port = HOST.matchEntire(host)?.groupValues?.get(1)?.toIntOrNull() ?: return null
    if (port !in 1..65535) return null
    // Case, spaces and dashes don't matter, as on the PC (doorbell.normalize_code)
    val code = params["code"]?.uppercase()?.filter { it.isLetterOrDigit() } ?: return null
    if (!CODE.matches(code)) return null
    return PairingLink(host, code)
}

/**
 * Opens the camera to scan the dashboard's QR code (ZXing, no Play services or network needed;
 * the camera permission is asked for only now). [onResult] gets the link, or null when the code
 * scanned isn't Puki's; nothing is called if the parent backs out.
 */
@Composable
fun rememberPairingScanner(prompt: String, onResult: (PairingLink?) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@rememberLauncherForActivityResult
        onResult(parsePairingLink(contents))
    }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(prompt)
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }
}
