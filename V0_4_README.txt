JARVIS V0.4 UPDATE

Correções principais:
- Corrige o parser de voz/comandos:
  "abre o Spotify e toca..."
  "abrir o Spotify e tocar..."
  "tocar..."
  "reproduzir..."
- Não considera playFromSearch como sucesso automaticamente.
- Confere a metadata da sessão do Spotify para saber se a faixa pedida realmente começou.
- Se o Spotify ignorar a sessão de mídia, usa Jarvis Spotify Automation.
- A automação abre a busca do Spotify, procura um resultado com forte correspondência de texto e toca sozinho.
- O AccessibilityService é limitado ao pacote com.spotify.music.
- versionCode 4 / versionName 0.4.0.

Atualizar:
cd ~/JarvisRepo
unzip -o ~/storage/downloads/JarvisAndroid_V0_4_Update.zip
git add .
git commit -m "JARVIS V0.4 hybrid Spotify control"
git push
