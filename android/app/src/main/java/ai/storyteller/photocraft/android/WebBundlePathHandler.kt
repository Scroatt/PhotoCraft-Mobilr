package ai.storyteller.photocraft.android

import android.content.Context
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.FileNotFoundException
import java.util.Locale

/**
 * Serve o build web do PhotoCraft (assets/web/) pelo endereço
 * https://appassets.androidplatform.net/assets/web/index.html.
 *
 * Usar um endereço https (e não file://) mantém a página em contexto seguro, exigido pelo
 * WebGPU, e deixa o `.wasm` carregar em streaming, o que exige o MIME `application/wasm`.
 */
class WebBundlePathHandler(private val context: Context) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse? {
        var relative = path.trimStart('/')
        if (relative.isEmpty() || relative.endsWith("/")) relative += "index.html"
        // O bundle é plano e fixo: recusa qualquer caminho que tente sair da pasta.
        if (!relative.startsWith("web/") || relative.split('/').any { it == ".." || it.isEmpty() }) {
            return null
        }

        val stream = try {
            context.assets.open(relative)
        } catch (e: FileNotFoundException) {
            return null
        }

        val mime = mimeType(relative)
        val encoding = if (mime.startsWith("text/") || mime == "application/json") "utf-8" else null
        return WebResourceResponse(mime, encoding, stream)
    }

    private fun mimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "wasm" -> "application/wasm"
            "js", "mjs" -> "text/javascript"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "json" -> "application/json"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
    }
}
