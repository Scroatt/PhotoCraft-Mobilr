package ai.storyteller.photocraft.android

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Abre o PhotoCraft web (embutido no APK) em tela cheia dentro de um WebView.
 */
class MainActivity : ComponentActivity() {

    private val isDebuggable: Boolean
        get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private lateinit var webView: WebView

    /** Callback do <input type="file"> pendente, respondido quando o seletor de arquivos fecha. */
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null

    /** Rede de fundo: checagem de atualização e download do APK. */
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        pendingFileCallback?.onReceiveValue(uris)
        pendingFileCallback = null
    }

    /** Mensagens do script da página: teclado (type "keyboard") ou downloads de arquivos. */
    private val nativeBridge = WebViewCompat.WebMessageListener { _, message, _, _, _ ->
        val payload = message.data
        if (payload != null) handleNativeMessage(payload)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(isDebuggable)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebBundlePathHandler(this))
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(0xFF262626.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // Necessário para o teclado: a WebView precisa poder receber foco.
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // O app não precisa de arquivos nem conteúdo do sistema por URL.
                allowFileAccess = false
                allowContentAccess = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                // O PhotoCraft abre links (Discord, Ajuda, GitHub) com window.open("_blank").
                // Sem isto o WebView ignora a chamada e o botão "não faz nada".
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    // Só o bundle do app: a página não acessa a internet, mesmo com a permissão.
                    if (uri.host != APP_HOST) return blockedResponse()
                    return assetLoader.shouldInterceptRequest(uri)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (uri.host == APP_HOST) return false
                    // Links externos (ex.: documentação) abrem no navegador, não dentro do editor.
                    openExternally(uri)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    // Diagnóstico: console da página no logcat (só builds de debug).
                    if (isDebuggable) {
                        Log.d("PhotoCraftWeb", "${message?.message()} (${message?.sourceId()}:${message?.lineNumber()})")
                    }
                    return true
                }

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    // Janela temporária: só recebe a URL do link, que vai para o navegador externo.
                    val popup = WebView(this@MainActivity)
                    popup.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                            request?.url?.let { openExternally(it) }
                            // Destruir só depois que o WebView terminar este callback.
                            v?.let { view -> view.post { view.destroy() } }
                            return true
                        }
                    }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: WebChromeClient.FileChooserParams?,
                ): Boolean {
                    if (filePathCallback == null || fileChooserParams == null) return false
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = filePathCallback
                    return try {
                        filePicker.launch(fileChooserParams.createIntent())
                        true
                    } catch (e: ActivityNotFoundException) {
                        pendingFileCallback = null
                        false
                    }
                }
            }
        }

        // Scripts e canal nativo só para a origem do app.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, DownloadBridge.DOCUMENT_START_SCRIPT, setOf(APP_ORIGIN))
            WebViewCompat.addDocumentStartJavaScript(webView, KeyboardBridge.DOCUMENT_START_SCRIPT, setOf(APP_ORIGIN))
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, DownloadBridge.OBJECT_NAME, setOf(APP_ORIGIN), nativeBridge)
        }

        // Mantém a área do editor fora das barras do sistema (o Android 15 desenha de ponta a ponta).
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF262626.toInt())
            addView(webView)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl(START_URL)
        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        backgroundExecutor.shutdown()
        webView.destroy()
        super.onDestroy()
    }

    private fun handleNativeMessage(payload: String) {
        val json = try {
            JSONObject(payload)
        } catch (e: JSONException) {
            return
        }
        if (json.optString("type") == KeyboardBridge.TYPE) {
            KeyboardBridge.onMessage(this, webView, json.optString("value"))
        } else {
            DownloadBridge.onMessage(this, payload)
        }
    }

    // region Atualização

    /** Checa em segundo plano; se houver versão nova, pergunta ao usuário. Sem rede, não faz nada. */
    private fun checkForUpdates() {
        val skipped = getSharedPreferences(AppUpdater.PREFS, MODE_PRIVATE).getString(AppUpdater.KEY_SKIPPED, null)
        backgroundExecutor.execute {
            val release = try {
                AppUpdater.findNewerRelease(BuildConfig.VERSION_NAME, skipped)
            } catch (e: Exception) {
                null
            }
            if (release != null) {
                runOnUiThread { if (!isFinishing) askToUpdate(release) }
            }
        }
    }

    private fun askToUpdate(release: AppUpdater.Release) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_title)
            .setMessage(getString(R.string.update_message, release.version, BuildConfig.VERSION_NAME))
            .setPositiveButton(R.string.update_now) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(R.string.update_later, null)
            .setNeutralButton(R.string.update_skip) { _, _ ->
                getSharedPreferences(AppUpdater.PREFS, MODE_PRIVATE).edit()
                    .putString(AppUpdater.KEY_SKIPPED, release.version)
                    .apply()
            }
            .show()
    }

    private fun downloadAndInstall(release: AppUpdater.Release) {
        val progress = AlertDialog.Builder(this)
            .setMessage(R.string.update_downloading)
            .setCancelable(false)
            .show()
        backgroundExecutor.execute {
            val apk = try {
                AppUpdater.download(this@MainActivity, release)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                progress.dismiss()
                if (apk == null) {
                    Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
                } else if (!AppUpdater.install(this, apk)) {
                    Toast.makeText(this, R.string.update_allow_sources, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // endregion

    /** Resposta vazia para qualquer requisição de rede que a página tente fazer. */
    private fun blockedResponse() = WebResourceResponse(
        "text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    private fun openExternally(uri: Uri) {
        if (uri.scheme !in setOf("http", "https", "mailto")) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // Nenhum app para abrir o link: não há o que fazer.
        }
    }

    private companion object {
        const val APP_HOST = "appassets.androidplatform.net"
        const val APP_ORIGIN = "https://$APP_HOST"
        const val START_URL = "$APP_ORIGIN/assets/web/index.html"
    }
}
