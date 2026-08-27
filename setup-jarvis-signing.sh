#!/data/data/com.termux/files/usr/bin/bash
set -e

REPO="kalilrodrigues827-crypto/JARVIS-ANDROID"
SIGN_DIR="$HOME/jarvis-signing"
KEYSTORE="$SIGN_DIR/jarvis-release.jks"
BACKUP="$SIGN_DIR/KEEP_SAFE.txt"
ALIAS="jarvis"

echo "== JARVIS permanent signing setup =="

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI não encontrado. Instale com: pkg install gh -y"
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Você precisa estar logado no GitHub CLI."
  exit 1
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "Instalando Java/keytool..."
  pkg install openjdk-17 -y
fi

mkdir -p "$SIGN_DIR"
chmod 700 "$SIGN_DIR"

if [ -f "$KEYSTORE" ] && [ -f "$BACKUP" ]; then
  echo "Chave existente encontrada. Reutilizando a mesma chave."
  STORE_PASS=$(sed -n 's/^STORE_PASSWORD=//p' "$BACKUP")
  KEY_PASS=$(sed -n 's/^KEY_PASSWORD=//p' "$BACKUP")
else
  STORE_PASS=$(head -c 96 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32)
  KEY_PASS="$STORE_PASS"

  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 3072 \
    -validity 10000 \
    -dname "CN=JARVIS, OU=Rush Works, O=Rush Works, L=Private, ST=Private, C=BR"

  cat > "$BACKUP" <<EOF
JARVIS SIGNING BACKUP
=====================
KEYSTORE=$KEYSTORE
KEY_ALIAS=$ALIAS
STORE_PASSWORD=$STORE_PASS
KEY_PASSWORD=$KEY_PASS

NÃO APAGUE ESTA PASTA.
NÃO ENVIE ESTES DADOS PARA O GITHUB.
NÃO COMPARTILHE ESTE ARQUIVO.
Se esta chave for perdida, versões futuras não poderão atualizar as versões já instaladas.
EOF

  chmod 600 "$BACKUP" "$KEYSTORE"
fi

KEYSTORE_B64=$(base64 "$KEYSTORE" | tr -d '\n')

echo "Enviando a chave com segurança para GitHub Actions Secrets..."
gh secret set JARVIS_KEYSTORE_BASE64 --repo "$REPO" --body "$KEYSTORE_B64"
gh secret set JARVIS_KEYSTORE_PASSWORD --repo "$REPO" --body "$STORE_PASS"
gh secret set JARVIS_KEY_ALIAS --repo "$REPO" --body "$ALIAS"
gh secret set JARVIS_KEY_PASSWORD --repo "$REPO" --body "$KEY_PASS"

echo
echo "Pronto."
echo "A chave NÃO foi colocada no repositório."
echo "Faça backup da pasta:"
echo "  $SIGN_DIR"
echo
echo "Agora você pode fazer git push da V0.5."
