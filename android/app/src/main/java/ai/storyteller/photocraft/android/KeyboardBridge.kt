package ai.storyteller.photocraft.android

import android.app.Activity
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * Teclado virtual para os campos de texto do editor.
 *
 * O egui (motor de interface do PhotoCraft) foca um <input> oculto quando um campo de texto
 * precisa de digitação. Esse foco vem do código da página, fora do toque do usuário, e o
 * Chromium do WebView não mostra o teclado nesse caso. O script abaixo avisa o app nativo
 * quando um campo de texto ganha foco logo após um toque, e o app pede o teclado ao sistema.
 */
object KeyboardBridge {

    /** Valor do campo "type" das mensagens de teclado (as de download não têm esse campo). */
    const val TYPE = "keyboard"

    /** Roda antes de qualquer script da página, no origin do app. */
    const val DOCUMENT_START_SCRIPT = """
(function () {
  if (window.__photocraftKeyboardBridge) return;
  window.__photocraftKeyboardBridge = true;

  const NOT_TEXT = ['button', 'checkbox', 'color', 'file', 'hidden', 'image', 'radio', 'range', 'reset', 'submit'];
  let lastTouchEnd = 0;

  function isTextField(el) {
    if (!el || !(el instanceof HTMLElement)) return false;
    if (el.isContentEditable || el.tagName === 'TEXTAREA') return true;
    return el.tagName === 'INPUT' && !NOT_TEXT.includes((el.type || 'text').toLowerCase());
  }

  function send(value) {
    if (window.photocraftAndroid) {
      window.photocraftAndroid.postMessage(JSON.stringify({ type: 'keyboard', value: value }));
    }
  }

  document.addEventListener('touchend', function () {
    lastTouchEnd = Date.now();
  }, true);

  // O campo oculto do egui também ganha foco ao iniciar: só mostra o teclado depois de um toque.
  document.addEventListener('focusin', function (e) {
    if (isTextField(e.target) && Date.now() - lastTouchEnd < 1500) send('show');
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
        val imm = activity.getSystemService(InputMethodManager::class.java) ?: return
        when (value) {
            "show" -> {
                webView.requestFocus()
                imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            }
            "hide" -> imm.hideSoftInputFromWindow(webView.windowToken, 0)
        }
    }
}
