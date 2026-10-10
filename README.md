# SyncWatch Android 1.2

Vídeos locais sincronizados pelo protocolo Syncplay. Android 8.0 ou superior.

## Instalar e assistir

1. Baixe `SyncWatch-1.2.0.apk` no artefato **SyncWatch-APK**, em **Actions → Android APK**.
2. Instale o APK. A assinatura de debug muda entre runners; se houver conflito com a versão anterior, desinstale-a antes.
3. Cada pessoa escolhe sua cópia do mesmo vídeo, informa seu nome e usa o mesmo nome de sala.
4. Toque **Entrar na sala** e **Estou pronto**. Play, pausa e saltos são sincronizados.
5. Use **Tela cheia**, o ícone no player ou gire o aparelho na horizontal. Voltar ou o ícone do player sai de tela cheia.

O serviço `syncplay.pl:8997` é configurado internamente e usa TLS com certificado e hostname verificados. Não há campos de servidor/porta nem chat. Para participar pelo Syncplay no computador, use esse serviço e a mesma sala. O vídeo não é transmitido; cada pessoa precisa de uma cópia de duração idêntica. Prontidão informa o estado, sem bloquear play.

## Legendas

Após abrir o vídeo, toque **Adicionar legenda** e escolha um arquivo `.srt` ou `.vtt` em UTF-8, de até 2 MB. O botão da legenda permite trocar, remover ou ajustar seu tempo em milissegundos: valores positivos atrasam e negativos adiantam. O ajuste afeta só este aparelho e preserva a posição do vídeo. O botão de legendas no player também permite selecionar/desativar trilhas. Ao trocar de vídeo, a legenda externa é removida; escolha-a novamente na próxima sessão.

## Player e interface

Interface escura com botões arredondados, nome/sala, participantes e convite. Tela cheia imersiva com controles que somem durante a reprodução. A proporção original do filme é preservada, portanto filmes de outra proporção podem ter faixas pretas. Ao sair do app, o vídeo pausa; reconexão é manual. O player usa **LibVLC 3.7.7** oficial do Maven Central, com acelera��o dispon�vel e fallback nativo. Aceita MKV/Matroska, HEVC e AC3 5.1 sem transcodifica��o; perfis incomuns e desempenho ainda precisam ser verificados no aparelho. O seletor aceita MIME gen�rico e preserva a permiss�o de leitura SAF, sem permiss�o ampla de armazenamento. Streaming, reprodução em segundo plano, playlists compartilhadas e salas controladas não estão incluídos.

## Compilar e validar

JDK 17, SDK 35, Gradle 8.11.1 e AGP 8.9.2.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions executa testes de protocolo/TCP, testes de deslocamento de legendas, lint e build. No emulador Android 11, com LibVLC real, verifica play/pausa/seek nos dois sentidos, renderização de legenda externa, remoção/ajuste da legenda e transição de tela cheia preservando o player. O APK é universal, assinado com chave de debug para instalação direta e testes, com assinatura verificada por `apksigner`. A publicação em loja exige uma chave de release própria.

Referências: [Syncplay](https://github.com/Syncplay/syncplay/blob/master/syncplay/protocols.py), [LibVLC Android](https://code.videolan.org/videolan/libvlc-android), [depend�ncia 3.7.7](https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/). LibVLC � distribu�do sob LGPL; fontes e licen�a est�o dispon�veis no projeto oficial e no sources JAR da mesma vers�o..
