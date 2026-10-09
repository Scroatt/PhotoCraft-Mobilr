package ai.storyteller.photocraft.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualização do próprio app, sem desinstalar: procura nas releases do GitHub uma versão nova
 * (tag `android-v<versão>`), baixa o APK e entrega ao instalador do Android.
 *
 * O Android só instala por cima quando o APK novo tem a mesma assinatura do instalado e um
 * versionCode maior. Por isso os builds publicados são assinados com a mesma chave de release.
 */
object AppUpdater {

    const val PREFS = "updates"
    const val KEY_SKIPPED = "skipped_version"

    private const val REPO = "Scroatt/androCraft"
    private const val TAG_PREFIX = "android-v"
    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=20"

    class Release(val version: String, val apkUrl: String)

    /**
     * Devolve a release mais recente se for mais nova que [currentVersion] e não tiver sido
     * ignorada pelo usuário ([skipped]). Devolve null em qualquer outro caso, inclusive sem rede.
     * Chamar fora da thread principal.
     */
    fun findNewerRelease(currentVersion: String, skipped: String?): Release? {
        val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "PhotoCraft-Android")
            if (conn.responseCode != 200) return null

            val releases = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.optString("tag_name")
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
                if (!tag.startsWith(TAG_PREFIX)) continue

                // A lista vem da mais nova para a mais antiga: a primeira release Android decide.
                val version = tag.removePrefix(TAG_PREFIX)
                if (!isNewer(version, currentVersion) || version == skipped) return null

                val assets = release.optJSONArray("assets") ?: return null
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val url = asset.optString("browser_download_url")
                    if (asset.optString("name").endsWith(".apk") && url.startsWith("https://")) {
                        return Release(version, url)
                    }
                }
                return null
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Compara versões do tipo 0.5.1 (sufixos como -rc1 são ignorados). */
    internal fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /** Baixa o APK para o cache do app. Chamar fora da thread principal. */
    fun download(context: Context, release: Release): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val safeVersion = release.version.filter { it.isLetterOrDigit() || it == '.' || it == '-' }
        val target = File(dir, "photocraft-$safeVersion.apk")

        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.inputStream.use { input -> target.outputStream().use { out -> input.copyTo(out) } }
        } finally {
            conn.disconnect()
        }
        check(target.length() > 0) { "APK vazio" }
        return target
    }

    /**
     * Abre o instalador do Android com o APK. Se o app ainda não pode instalar pacotes, leva o
     * usuário à permissão. Devolve false nesse caso, para que a tela avise.
     */
    fun install(activity: Activity, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return false
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(intent)
        return true
    }
}
