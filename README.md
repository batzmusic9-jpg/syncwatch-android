# SyncWatch Android 1.2

Vídeos locais sincronizados pelo protocolo Syncplay. Android 8.0 ou superior.

## Instalar e assistir

1. Baixe `SyncWatch-1.2.0.apk` no artefato **SyncWatch-APK**, em **Actions → Android APK**.
2. Instale o APK. Se a assinatura da versão anterior for diferente, desinstale a anterior antes.
3. Cada pessoa escolhe sua cópia do mesmo vídeo, informa seu nome e usa o mesmo nome de sala.
4. Toque **Entrar na sala** e **Estou pronto**. Play, pausa e saltos são sincronizados.
5. Use **Tela cheia** ou gire o aparelho na horizontal. Voltar ou **Sair** no player sai de tela cheia.

O serviço `syncplay.pl:8997` é configurado internamente e usa TLS com certificado e hostname verificados. Não há campos de servidor/porta nem chat. Para participar pelo Syncplay no computador, use esse serviço e a mesma sala. Cada pessoa precisa de uma cópia de duração idêntica. Prontidão informa o estado, sem bloquear play.

## Player e trilhas

LibVLC **3.7.7** oficial do Maven Central, com aceleração disponível e fallback nativo. Suporta MKV/Matroska, HEVC e AC3 5.1 sem transcodificação. Perfis incomuns e desempenho ainda precisam ser verificados no aparelho. O seletor aceita MIME genérico e preserva acesso SAF, sem permissão ampla de armazenamento.

O controle **Áudio** seleciona uma trilha, mostrando idioma, codec e canais quando disponíveis. **Legendas** oferece **Nenhuma**, trilhas internas e **Adicionar legenda externa**. Escolha `.srt` ou `.vtt` em UTF-8 de até 2 MB. O mesmo menu ajusta o tempo: +500 ms atrasa meio segundo e -500 ms adianta meio segundo. Usa o atraso nativo em microssegundos, sem recarregar o vídeo e sem afetar outros participantes. A legenda externa é removida ao trocar de vídeo; escolha-a novamente na próxima sessão.

Interface escura, sala/nome, participantes e convite. Tela cheia imersiva, controles que somem durante a reprodução e proporção original preservada. Filmes de outra proporção podem ter faixas pretas. Ao sair do app, o vídeo pausa; reconexão é manual. Streaming, segundo plano, playlists compartilhadas e salas controladas estão fora deste projeto.

## Compilar e validar

JDK 17, compileSdk 36, targetSdk 35, minSdk 26, Gradle 8.11.1 e AGP 8.9.2.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
gradle --no-daemon connectedDebugAndroidTest
```

Actions mantém os testes de protocolo/TCP e SubtitleTiming; adiciona testes de comandos versus callbacks tardios, seek durante preparação e drift de 300 ms. No emulador Android 11 com LibVLC real verifica play/pausa/seek nos dois sentidos, ausência de eco, pixels de legenda externa, offset nativo positivo/negativo, tela cheia e onStop. Um MKV sintético HEVC 1920×804/23.976 fps + AC3 5.1/48 kHz verifica buffers de vídeo e áudio decodificados, duas trilhas de áudio, duas legendas internas e seek.

Testes sintéticos NÃO comprovam o filme real em dois celulares. Consulte [auditoria e limites](docs/playback-audit-v1.2.md). Logs nativos e relatórios ficam no artefato **Validation-reports**. O APK universal é assinado com chave de debug para instalação e testes; `apksigner` verifica a assinatura e SHA256SUMS identifica o arquivo. A assinatura pode variar entre runners.

Referências: [Syncplay](https://github.com/Syncplay/syncplay/blob/master/syncplay/protocols.py), [LibVLC Android](https://code.videolan.org/videolan/libvlc-android), [LibVLC 3.7.7 no Maven Central](https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/). LibVLC é distribuído sob LGPL; fontes e licença estão disponíveis no projeto oficial e no sources JAR dessa versão.
