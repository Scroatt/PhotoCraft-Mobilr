# PhotoCraft Android (WebView)

Aplicativo Android que abre a **versão web do PhotoCraft** em tela cheia dentro de um
`WebView`. O build web (WebAssembly) vem embutido no APK, então o editor funciona offline.
A internet é usada só para checar e baixar atualizações do próprio app; a página do editor
não consegue acessar a rede.

Fonte do build web: [storytold/photocraft](https://github.com/storytold/photocraft), asset
`photocraft-web-<versão>.zip` das releases.

## Como funciona

| Parte | Arquivo | Função |
|---|---|---|
| Download do bundle | `scripts/fetch-web.sh` | Baixa o zip da release, confere o SHA-256 e extrai em `app/src/main/assets/web/` |
| Servir os arquivos | `WebBundlePathHandler.kt` | Entrega o bundle em `https://appassets.androidplatform.net/assets/web/` com MIME correto (`application/wasm` para o `.wasm`) |
| Tela | `MainActivity.kt` | WebView em tela cheia, seletor de arquivos, links externos no navegador, bloqueio de rede da página |
| Salvar arquivos | `DownloadBridge.kt` | Intercepta os downloads do editor e grava em **Downloads** (MediaStore) |
| Teclado | `KeyboardBridge.kt` | Mostra o teclado virtual quando um campo de texto do editor ganha foco |
| Atualização | `AppUpdater.kt` | Procura nas releases `android-v*` uma versão nova, baixa o APK e abre o instalador |

Por que `https://appassets...` e não `file://`: a página fica em contexto seguro (necessário
para WebGPU) e o navegador consegue compilar o `.wasm` em streaming.

## Teclado

O editor usa um campo de texto oculto para receber a digitação. O Chromium do WebView só
mostra o teclado quando o foco nasce de um toque do usuário, e o editor move o foco pelo
código. O `KeyboardBridge` cuida disso: quando um campo de texto ganha foco logo após um toque,
o app pede o teclado ao sistema, e o fecha quando o foco sai do campo.

## Atualizações

Ao abrir, o app consulta as releases do repositório. Se houver uma tag `android-v<versão>` mais
nova que a versão instalada, aparece um aviso com três opções:

- **Atualizar:** baixa o APK e abre o instalador do Android. A atualização entra por cima do app
  instalado, sem desinstalar, e os dados do app são mantidos.
- **Depois:** pergunta de novo na próxima abertura.
- **Ignorar esta versão:** não pergunta mais sobre essa versão.

Na primeira atualização, o Android pede para permitir a instalação de apps para o PhotoCraft.

**Requisito:** o Android só instala uma atualização se ela estiver assinada com **a mesma chave**
da versão instalada e tiver um código de versão maior. Por isso os APKs publicados usam uma chave
de release fixa (veja "Assinatura"). Os APKs de debug geram uma chave diferente a cada build, então
não atualizam uns aos outros: quem instalou um debug precisa desinstalar uma vez e instalar a
primeira versão de release (isso apaga os dados locais do app).

## Assinatura (uma vez só)

A chave de release é sua. Guarde o arquivo e as senhas em local seguro: **se a chave for perdida,
não há como atualizar o app** nos celulares que já o instalaram.

1. Crie a chave (requer o JDK 17, que vem com o Android Studio):

   ```sh
   keytool -genkeypair -v -keystore photocraft-release.jks -alias photocraft \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Guarde o keystore como secret do GitHub, em base64, e as senhas. Em
   **Settings › Secrets and variables › Actions** do repositório, crie:

   | Secret | Valor |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | conteúdo de `base64 -w0 photocraft-release.jks` (Linux) ou `base64 -i photocraft-release.jks` (macOS) |
   | `ANDROID_KEYSTORE_PASSWORD` | senha do keystore |
   | `ANDROID_KEY_ALIAS` | `photocraft` (o alias usado no passo 1) |
   | `ANDROID_KEY_PASSWORD` | senha da chave |

   Com a CLI: `gh secret set ANDROID_KEYSTORE_PASSWORD` (e assim por diante). Não coloque o
   keystore nem as senhas no Git.

## Publicar uma versão

1. Faça o merge das mudanças na branch principal.
2. Crie e envie a tag no commit desejado:

   ```sh
   git tag android-v0.5.1
   git push origin android-v0.5.1
   ```

3. O workflow `.github/workflows/android-release.yml` gera o APK assinado e cria a GitHub Release
   `android-v0.5.1` com o arquivo `photocraft-android-0.5.1.apk`. Os usuários recebem o aviso de
   atualização na próxima abertura do app.

A versão do app vem da tag (`0.5.1`) e o código de versão vem do número da execução do CI, que
sempre cresce. O bundle web usado é o da versão fixada em `scripts/fetch-web.sh`.

## Pré-requisitos para gerar o APK localmente

- JDK 17
- Android SDK (API 35), ou o Android Studio, que instala tudo
- Gradle 8.10+ (ou o Android Studio)
- GitHub CLI (`gh`) autenticado, para o `scripts/fetch-web.sh`

## Gerar o APK de debug

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

## CI

- `.github/workflows/android.yml`: gera o APK de debug como artefato a cada push que mexe em
  `android/` (`photocraft-android-debug`).
- `.github/workflows/android-release.yml`: gera o APK assinado e publica a Release, ao receber uma
  tag `android-v*`.

## Limitações conhecidas

- **Android 10 (API 29) ou superior.** Os downloads usam MediaStore, que exige essa versão.
- **Renderização:** o PhotoCraft web usa WebGPU quando o WebView oferece e cai para WebGL2
  caso contrário. O desempenho depende da versão do Android System WebView instalada.
- **Downloads:** o editor salva em `Downloads/` e mostra uma mensagem com o nome do arquivo.
  Se já existir um arquivo com o mesmo nome, o Android pode renomear o novo.
- **Rotação de tela** não recria a atividade (`configChanges`), para não perder o trabalho em
  andamento.
- A atualização reinicia o app ao final da instalação; trabalho não salvo no editor se perde.
- **Teste em aparelho:** a compilação é validada pelo CI, mas o teclado, a atualização e o
  salvamento precisam ser testados num celular real antes de cada publicação.
