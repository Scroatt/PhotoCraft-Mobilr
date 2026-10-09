# PhotoCraft Android (WebView)

Aplicativo Android que abre a **versão web do PhotoCraft** em tela cheia dentro de um
`WebView`. O build web (WebAssembly) vem embutido no APK, então o editor funciona offline e o
app não pede permissão de internet.

Fonte do build web: [storytold/photocraft](https://github.com/storytold/photocraft), asset
`photocraft-web-<versão>.zip` das releases.

## Como funciona

| Parte | Arquivo | Função |
|---|---|---|
| Download do bundle | `scripts/fetch-web.sh` | Baixa o zip da release, confere o SHA-256 e extrai em `app/src/main/assets/web/` |
| Servir os arquivos | `WebBundlePathHandler.kt` | Entrega o bundle em `https://appassets.androidplatform.net/assets/web/` com MIME correto (`application/wasm` para o `.wasm`) |
| Tela | `MainActivity.kt` | WebView em tela cheia, seletor de arquivos (abrir imagens), links externos no navegador |
| Salvar arquivos | `DownloadBridge.kt` | Intercepta os downloads do editor e grava em **Downloads** (MediaStore) |

Por que `https://appassets...` e não `file://`: a página fica em contexto seguro (necessário
para WebGPU) e o navegador consegue compilar o `.wasm` em streaming.

## Pré-requisitos

- JDK 17
- Android SDK (API 35) — ou o Android Studio, que instala tudo
- Gradle 8.10+ (ou o Android Studio)
- GitHub CLI (`gh`) autenticado, para o `scripts/fetch-web.sh`

## Gerar o APK

Na raiz do repositório:

```sh
scripts/fetch-web.sh            # baixa o bundle web (versão fixada no script)
scripts/fetch-web.sh 0.5.0      # ou uma versão específica
cd android
gradle :app:assembleDebug       # APK em app/build/outputs/apk/debug/
```

Se o bundle não estiver presente, o build para com uma mensagem apontando para o script.

Também dá para abrir a pasta `android/` no Android Studio: ao sincronizar, ele cria o
Gradle Wrapper automaticamente. O projeto não versiona o `gradlew` porque o
`gradle-wrapper.jar` é binário.

Para trocar a versão do PhotoCraft web, altere `PHOTOCRAFT_WEB_VERSION` no ambiente (ou o
padrão em `scripts/fetch-web.sh`) e o `versionName` em `app/build.gradle.kts`.

## CI

`.github/workflows/android.yml` roda em pushes/PRs que mexem em `android/` e gera o APK de
debug como artefato (`photocraft-android-debug`).

## Limitações conhecidas

- **Android 10 (API 29) ou superior.** Os downloads usam MediaStore, que exige essa versão.
- **Renderização:** o PhotoCraft web usa WebGPU quando o WebView oferece e cai para WebGL2
  caso contrário. O desempenho depende da versão do Android System WebView instalada.
- **Downloads:** o editor salva em `Downloads/` e mostra uma mensagem com o nome do arquivo.
  Se já existir um arquivo com o mesmo nome, o Android pode renomear o novo.
- **Rotação de tela** não recria a atividade (`configChanges`), para não perder o trabalho em
  andamento.
- Não testado em aparelho físico ou emulador neste repositório: a compilação do APK é
  validada pelo workflow de CI.
