#!/usr/bin/env bash
# Baixa o build web do PhotoCraft (asset photocraft-web-<versão>.zip da release do GitHub),
# confere o SHA-256 publicado na mesma release e extrai o site em
# android/app/src/main/assets/web/, que é empacotado dentro do APK.
#
# Uso:   scripts/fetch-web.sh [versão]
#        (padrão: $PHOTOCRAFT_WEB_VERSION ou a versão fixada abaixo)
# Precisa: gh (GitHub CLI) autenticado ou com GH_TOKEN, unzip e sha256sum.
set -euo pipefail

VERSION="${1:-${PHOTOCRAFT_WEB_VERSION:-0.5.0}}"
REPO="${PHOTOCRAFT_REPO:-storytold/photocraft}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/android/app/src/main/assets/web"
ZIP="photocraft-web-$VERSION.zip"

die() { echo "erro: $*" >&2; exit 1; }

command -v gh >/dev/null 2>&1 || die "gh (GitHub CLI) não encontrado"
command -v unzip >/dev/null 2>&1 || die "unzip não encontrado"
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$ ]] || die "versão inválida: '$VERSION'"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Baixando $ZIP de $REPO (tag v$VERSION)…"
gh release download "v$VERSION" --repo "$REPO" --dir "$WORK" \
  --pattern "$ZIP" --pattern SHA256SUMS.txt

# Confere só a linha do nosso zip (o arquivo de somas tem todos os assets da release).
awk -v f="$ZIP" '{ n = $2; sub(/^\*/, "", n); if (n == f) print }' "$WORK/SHA256SUMS.txt" > "$WORK/expected.txt"
[ -s "$WORK/expected.txt" ] || die "$ZIP não aparece em SHA256SUMS.txt"
(cd "$WORK" && sha256sum --check --strict expected.txt)

unzip -q "$WORK/$ZIP" -d "$WORK/unzipped"
SRC="$WORK/unzipped/photocraft-web-$VERSION"
[ -f "$SRC/index.html" ] || die "index.html ausente dentro de $ZIP"
ls "$SRC"/*.wasm >/dev/null 2>&1 || die "nenhum .wasm dentro de $ZIP"

rm -rf "$DEST"
mkdir -p "$(dirname "$DEST")"
cp -R "$SRC" "$DEST"
# Arquivos de configuração de servidor não servem para o APK.
rm -f "$DEST/_headers" "$DEST/.htaccess" "$DEST/HOSTING.md"

echo "Bundle web do PhotoCraft $VERSION extraído em: ${DEST#"$ROOT"/}"
du -sh "$DEST"
