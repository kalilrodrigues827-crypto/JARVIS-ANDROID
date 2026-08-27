JARVIS V0.3 UPDATE

Mudanças:
- Controle direto de MediaSession do Android via NotificationListenerService.
- playFromSearch() para Spotify quando a sessão estiver disponível.
- Abre o Spotify e tenta novamente após 1,8s se necessário.
- Busca do Spotify fica como fallback.
- Botão para ativar "Jarvis Media Control" nas configurações do Android.
- Versão do app atualizada para 0.3.0 / versionCode 3.

Instalação sobre o repositório existente:
cd ~/JarvisRepo
unzip -o ~/storage/downloads/JarvisAndroid_V0_3_Update.zip
git add .
git commit -m "JARVIS V0.3 direct media control"
git push
