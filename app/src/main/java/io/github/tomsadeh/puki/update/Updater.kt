package io.github.tomsadeh.puki.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A newer game on GitHub: its version and where its APK is. */
data class Release(val version: String, val apkUrl: String, val size: Long, val sha256: String?)

/**
 * Updates from the public repo's GitHub releases: finds the newest release with an APK, downloads
 * it (checked against GitHub's SHA-256), and hands it to Android to install (the parent confirms).
 * The check only asks GitHub for the list of releases; nothing about the child is sent.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/TomSadeh/puki/releases?per_page=10"
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** The update waiting to be installed, if any (shown as a dot on the settings button). */
    val available = MutableStateFlow<Release?>(null)

    /** What happened to the last install, for the settings screen (null while nothing's pending). */
    val installMessage = MutableStateFlow<String?>(null)

    fun currentVersion(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    /** True if version [a] ("1.2.3") is newer than [b]. */
    fun isNewer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    /** The newest release that's newer than this game, or null. Throws if GitHub can't be reached. */
    suspend fun check(ctx: Context): Release? = withContext(Dispatchers.IO) {
        val conn = URL(RELEASES).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        var best: Release? = null
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val version = r.getString("tag_name").removePrefix("v")
            val assets = r.getJSONArray("assets")
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (!a.getString("name").endsWith(".apk")) continue
                val sha = a.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
                if (best == null || isNewer(version, best.version)) {
                    best = Release(version, a.getString("browser_download_url"), a.getLong("size"), sha)
                }
            }
        }
        val newer = best?.takeIf { isNewer(it.version, currentVersion(ctx)) }
        available.value = newer
        ctx.getSharedPreferences("updates", Context.MODE_PRIVATE).edit().putLong("last_check", System.currentTimeMillis()).apply()
        newer
    }

    /** Checks at most once a day (quietly: no network, no fuss). */
    suspend fun checkDaily(ctx: Context) {
        val last = ctx.getSharedPreferences("updates", Context.MODE_PRIVATE).getLong("last_check", 0)
        if (System.currentTimeMillis() - last < DAY_MS) return
        runCatching { check(ctx) }
    }

    /** Downloads [r] into the cache, checking its size and SHA-256. [progress] goes 0..1. */
    suspend fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "Puki-${r.version}.apk")
        val conn = URL(r.apkUrl).openConnection() as HttpURLConnection  // GitHub redirects to its file host
        conn.instanceFollowRedirects = true
        val md = MessageDigest.getInstance("SHA-256")
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    md.update(buf, 0, n)
                    done += n
                    progress((done.toFloat() / r.size).coerceIn(0f, 1f))
                }
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        if (file.length() != r.size || (r.sha256 != null && sha != r.sha256)) {
            file.delete()
            error("the download came out damaged")
        }
        file
    }

    /** Android only lets an app install updates once the parent allows it ("Install unknown apps"). */
    fun mayInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun askToAllowInstalls(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Hands [apk] to Android's installer; Android asks the parent to confirm, then restarts the game. */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("puki.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
        installMessage.value = null
    }
}

/** Android's answers while installing: first "ask the parent", then success or why it failed. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.installMessage.value = "ok"
            else -> Updater.installMessage.value =
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status"
        }
    }
}
