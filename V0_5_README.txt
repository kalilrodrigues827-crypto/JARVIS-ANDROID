JARVIS V0.5 - PERMANENT UPDATE FOUNDATION

IMPORTANTE
==========
A V0.4 e versões anteriores foram geradas como APK debug por runners descartáveis.
A V0.5 muda para uma chave de assinatura permanente.

POR ISSO:
- Você precisará desinstalar a V0.4 UMA ÚLTIMA VEZ para instalar a V0.5.
- Depois disso, NÃO perca a chave de assinatura.
- V0.6, V0.7, V1.0 e futuras versões poderão atualizar por cima da V0.5.

PASSO 1 - EXTRAIR A V0.5
========================
cd ~/JarvisRepo
unzip -o ~/storage/downloads/JarvisAndroid_V0_5_Update.zip

PASSO 2 - CRIAR A CHAVE PERMANENTE (SÓ UMA VEZ)
================================================
chmod +x setup-jarvis-signing.sh
./setup-jarvis-signing.sh

A chave será criada fora do repositório em:
~/jarvis-signing/

FAÇA BACKUP DESSA PASTA.
Ela NÃO deve ser enviada para o GitHub.

PASSO 3 - ENVIAR A V0.5
=======================
git add .
git commit -m "JARVIS V0.5 permanent updater"
git push

O GitHub Actions vai:
1. Restaurar a chave pelos GitHub Secrets.
2. Gerar um APK release assinado.
3. Salvar o APK como artifact.
4. Publicar o APK em GitHub Releases como JARVIS.apk.

ATUALIZAÇÕES FUTURAS
====================
O botão "VERIFICAR ATUALIZAÇÃO" consulta:
https://api.github.com/repos/kalilrodrigues827-crypto/JARVIS-ANDROID/releases/latest

Quando existir versão mais nova:
- O Jarvis mostra "ATUALIZAR AGORA".
- Baixa JARVIS.apk.
- Abre o instalador do Android.
- O Android atualiza por cima, preservando os dados.

O Android NÃO permite instalação totalmente silenciosa para um app normal.
Na primeira atualização pelo Jarvis você terá que liberar:
"Instalar apps desconhecidos" para o Jarvis.
Depois, a atualização fica em poucos toques.
