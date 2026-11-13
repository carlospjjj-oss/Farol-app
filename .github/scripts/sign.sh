#!/usr/bin/env bash
# Assina o APK com rotação de chave (APK Signature Scheme v3):
#  - Android 9+ passa a confiar só na chave NOVA (a antiga não pode mais "voltar" o app: rollback desligado)
#  - Android 8 continua verificando pela chave antiga (v2)
# Entradas (env): OLD_KS_B64, OLD_PW, NEW_KS_B64, NEW_PW, IN (apk sem assinatura), OUT
set -euo pipefail
BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
echo "build-tools: $BT"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
echo "$OLD_KS_B64" | base64 -d > "$WORK/old.jks"
echo "$NEW_KS_B64" | base64 -d > "$WORK/new.jks"
OLD_ALIAS=$(keytool -list -keystore "$WORK/old.jks" -storepass "$OLD_PW" 2>/dev/null | awk -F, '/PrivateKeyEntry/{print $1; exit}')
NEW_ALIAS=$(keytool -list -keystore "$WORK/new.jks" -storepass "$NEW_PW" 2>/dev/null | awk -F, '/PrivateKeyEntry/{print $1; exit}')
echo "aliases: $OLD_ALIAS -> $NEW_ALIAS"
"$BT/zipalign" -p -f 4 "$IN" "$WORK/aligned.apk"
"$BT/apksigner" rotate --out "$WORK/lineage.bin" \
  --old-signer --ks "$WORK/old.jks" --ks-pass env:OLD_PW --ks-key-alias "$OLD_ALIAS" \
    --set-installed-data true --set-shared-uid true --set-permission true --set-rollback false --set-auth true \
  --new-signer --ks "$WORK/new.jks" --ks-pass env:NEW_PW --ks-key-alias "$NEW_ALIAS"
"$BT/apksigner" sign --v4-signing-enabled false \
  --ks "$WORK/old.jks" --ks-pass env:OLD_PW --ks-key-alias "$OLD_ALIAS" \
  --next-signer --ks "$WORK/new.jks" --ks-pass env:NEW_PW --ks-key-alias "$NEW_ALIAS" \
  --lineage "$WORK/lineage.bin" --rotation-min-sdk-version 28 \
  --out "$OUT" "$WORK/aligned.apk"
echo "=== verificação ==="
"$BT/apksigner" verify --verbose --print-certs "$OUT"
echo "=== linhagem ==="
"$BT/apksigner" lineage --in "$OUT" --print-certs -v
