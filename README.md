# androCraft
Android mobile web embutido webview

## Android (WebView)

O app Android em [`android/`](android/README.md) abre a versão web do
[PhotoCraft](https://github.com/storytold/photocraft) dentro de um WebView, com o bundle web
embutido no APK.

```sh
scripts/fetch-web.sh      # baixa o bundle web da release do PhotoCraft
cd android && gradle :app:assembleDebug
```
