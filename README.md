# JARVIS V0.2 — Android / Termux Ready

Protótipo Android nativo em Kotlin + Jetpack Compose.

## O que já funciona

- Interface premium tecnológica.
- Comando manual.
- Reconhecimento de voz em português do Brasil.
- Entende frases como:
  - `Jarvis, toque Starboy`
  - `Abra o Spotify e toque Blinding Lights`
  - `Spotify, toca Eminem`
  - `Abra o Spotify`
- Tenta usar o comando padrão do Android `MEDIA_PLAY_FROM_SEARCH` direcionado ao Spotify.
- Se o autoplay não estiver disponível, abre a busca da música no Spotify.
- Não precisa de Client ID do Spotify nesta versão.
- GitHub Actions já configurado para gerar o APK sem computador.

## Compilar pelo GitHub Actions

Ao enviar este projeto para a branch `main`, o workflow `.github/workflows/build-apk.yml`
executa automaticamente.

Depois:
1. Abra `Actions` no repositório.
2. Abra `Build JARVIS APK`.
3. Aguarde o build terminar.
4. Em `Artifacts`, baixe `JARVIS-APK`.
5. Extraia o ZIP e instale `app-debug.apk`.

## Observação

A reprodução automática depende de o aplicativo de música instalado aceitar o intent Android
`MEDIA_PLAY_FROM_SEARCH`. Quando isso não acontecer, o Jarvis abre a pesquisa da música no Spotify.
