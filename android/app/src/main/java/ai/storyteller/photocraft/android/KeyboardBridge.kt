package ai.storyteller.photocraft.android

import android.app.Activity
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Teclado virtual para os campos de texto do editor.
 *
 * O egui (motor de interface do PhotoCraft) foca um <input> oculto quando um campo de texto
 * precisa de digitação. Esse foco vem do código da página, fora do toque do usuário, e o
 * Chromium do WebView não mostra o teclado nesse caso. O script abaixo avisa o app nativo
 * depois de cada toque, se há um campo de texto focado, e o app pede o teclado ao sistema.
 */
object KeyboardBridge {

    private const val TAG = "PhotoCraftKeyboard"

    /** Valor do campo "type" das mensagens de teclado (as de download não têm esse campo). */
    const val TYPE = "keyboard"

    /** Roda antes de qualquer script da página, no origin do app. */
    const val DOCUMENT_START_SCRIPT = """
(function () {
  if (window.__photocraftKeyboardBridge) return;
  window.__photocraftKeyboardBridge = true;

  const NOT_TEXT = ['button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset', 'submit'];
  let lastTouch = 0;

  function isTextField(el) {
    if (!el || !(el instanceof HTMLElement)) return false;
    if (el.isContentEditable || el.tagName === 'TEXTAREA') return true;
    return el.tagName === 'INPUT' && !NOT_TEXT.includes((el.type || 'text').toLowerCase());
  }

  function send(value) {
    console.log('[keyboard] ' + value);
    if (window.photocraftAndroid) {
      window.photocraftAndroid.postMessage(JSON.stringify({ type: 'keyboard', value: value }));
    }
  }

  function showIfTextFocused() {
    if (isTextField(document.activeElement)) send('show');
  }

  // Depois de cada toque, o egui move o foco para o campo de texto no próximo quadro.
  // Checar aqui cobre o caso em que o campo oculto já estava focado (então não há focusin).
  document.addEventListener('touchstart', function () {
    lastTouch = Date.now();
  }, true);

  document.addEventListener('touchend', function () {
    lastTouch = Date.now();
    setTimeout(showIfTextFocused, 300);
  }, true);

  // Foco em campo de texto logo após um toque (o foco do startup, sem toque, é ignorado).
  document.addEventListener('focusin', function (e) {
    if (isTextField(e.target) && Date.now() - lastTouch < 2500) send('show');
  }, true);

  document.addEventListener('focusout', function () {
    // Espera o foco pousar: trocar de campo não deve fechar e reabrir o teclado.
    setTimeout(function () {
      if (!isTextField(document.activeElement)) send('hide');
    }, 150);
  }, true);
})();
"""

    fun onMessage(activity: Activity, webView: WebView, value: String) {
        Log.d(TAG, "keyboard: $value")
        val imm = activity.getSystemService(InputMethodManager::class.java) ?: return
        when (value) {
            "show" -> {
                webView.requestFocus()
                // Reconecta a entrada da WebView para o sistema saber qual campo está editando.
                imm.restartInput(webView)
                // Flag 0: pedido explícito. SHOW_IMPLICIT pode ser ignorado pelo sistema.
                imm.showSoftInput(webView, 0)
            }
            "hide" -> imm.hideSoftInputFromWindow(webView.windowToken, 0)
        }
    }
}
