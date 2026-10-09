package ai.storyteller.photocraft.android

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Salvar arquivos (exportar, "Salvar como") no WebView.
 *
 * O PhotoCraft web baixa arquivos com `URL.createObjectURL(blob)` + `<a download>.click()`.
 * O WebView não sabe baixar blob: URLs, então:
 *  1. um script injetado antes da página guarda o Blob de cada URL criada e, no clique em um
 *     `<a download>`, lê o conteúdo e o envia ao app pelo canal `photocraftAndroid`;
 *  2. o app grava o arquivo em Downloads (MediaStore), sem permissão de armazenamento.
 */
object DownloadBridge {

    /** Nome do objeto JavaScript que recebe as mensagens (window.photocraftAndroid). */
    const val OBJECT_NAME = "photocraftAndroid"

    /** Roda antes de qualquer script da página, em todos os documentos do origin do app. */
    const val DOCUMENT_START_SCRIPT = """
(function () {
  if (window.__photocraftDownloadBridge) return;
  window.__photocraftDownloadBridge = true;

  // Blob por URL criada, até a URL ser revogada (o app revoga ~10 s depois do clique).
  const blobs = new Map();
  const createObjectURL = URL.createObjectURL.bind(URL);
  const revokeObjectURL = URL.revokeObjectURL.bind(URL);
  URL.createObjectURL = function (obj) {
    const url = createObjectURL(obj);
    if (obj instanceof Blob) blobs.set(url, obj);
    return url;
  };
  URL.revokeObjectURL = function (url) {
    blobs.delete(url);
    return revokeObjectURL(url);
  };

  const originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    const name = this.download;
    const blob = blobs.get(this.href);
    if (name && blob && window.photocraftAndroid) {
      const reader = new FileReader();
      reader.onload = function () {
        const dataUrl = String(reader.result);
        window.photocraftAndroid.postMessage(JSON.stringify({
          name: name,
          mime: blob.type || 'application/octet-stream',
          data: dataUrl.substring(dataUrl.indexOf(',') + 1)
        }));
      };
      reader.readAsDataURL(blob);
      return;
    }
    return originalClick.call(this);
  };
})();
"""

    private val io: ExecutorService = Executors.newSingleThreadExecutor()

    /** Mensagem vinda do JavaScript: {"name", "mime", "data" (base64)}. Roda na thread principal. */
    fun onMessage(activity: Activity, payload: String?) {
        val json = try {
            JSONObject(payload ?: return)
        } catch (e: JSONException) {
            toast(activity, "Não foi possível salvar o arquivo.")
            return
        }
        // File(...).name descarta qualquer caminho que a página tenha enviado.
        val name = File(json.optString("name")).name.ifBlank { "PhotoCraft" }
        val mime = json.optString("mime").ifBlank { "application/octet-stream" }
        val data = json.optString("data")

        io.execute {
            try {
                saveToDownloads(activity, name, mime, Base64.decode(data, Base64.DEFAULT))
                toast(activity, "Salvo em Downloads: $name")
            } catch (e: Exception) {
                toast(activity, "Não foi possível salvar $name.")
            }
        }
    }

    private fun saveToDownloads(context: Context, name: String, mime: String, bytes: ByteArray) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore recusou $name")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("sem stream de saída para $name")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun toast(activity: Activity, text: String) {
        activity.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    }
}
