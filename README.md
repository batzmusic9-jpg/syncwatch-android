# SyncWatch Android 1.1

Vídeos locais sincronizados pelo protocolo Syncplay. Android 8.0 ou superior.

## Instalar e assistir

1. Baixe `SyncWatch-1.1.0.apk` no artefato **SyncWatch-APK**, em **Actions → Android APK**.
2. Instale o APK. A assinatura de debug muda entre runners; se houver conflito com a versão anterior, desinstale-a antes.
3. Cada pessoa escolhe sua cópia do mesmo vídeo, informa seu nome e usa o mesmo nome de sala.
4. Toque **Entrar na sala** e **Estou pronto**. Play, pausa e saltos são sincronizados.
5. Use **Tela cheia**, o ícone no player ou gire o aparelho na horizontal. Voltar ou o ícone do player sai de tela cheia.

O serviço `syncplay.pl:8997` é configurado internamente e usa TLS com certificado e hostname verificados. Não há campos de servidor/porta nem chat. Para participar pelo Syncplay no computador, use esse serviço e a mesma sala. O vídeo não é transmitido; cada pessoa precisa de uma cópia de duração idêntica. Prontidão informa o estado, sem bloquear play.

## Legendas

Após abrir o vídeo, toque **Adicionar legenda** e escolha um arquivo `.srt` ou `.vtt` em UTF-8, de até 2 MB. O botão da legenda permite trocar, remover ou ajustar seu tempo em milissegundos: valores positivos atrasam e negativos adiantam. O ajuste afeta só este aparelho e preserva a posição do vídeo. O botão de legendas no player também permite selecionar/desativar trilhas. Ao trocar de vídeo, a legenda externa é removida; escolha-a novamente na próxima sessão.

## Player e interface

Interface escura com botões arredondados, nome/sala, participantes e convite. Tela cheia imersiva com controles que somem durante a reprodução. A proporção original do filme é preservada, portanto filmes de outra proporção podem ter faixas pretas. Ao sair do app, o vídeo pausa; reconexão é manual. Codecs dependem do aparelho. Para testar, use MP4 H.264/AAC. Streaming, reprodução em segundo plano, playlists compartilhadas e salas controladas não estão incluídos.

## Compilar e validar

JDK 17, SDK 35, Gradle 8.11.1 e AGP 8.9.2.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

GitHub Actions executa testes de protocolo/TCP, testes de deslocamento de legendas, lint e build. No emulador Android 11, verifica play/pausa/seek nos dois sentidos, renderização de legenda externa, remoção/ajuste da legenda e transição de tela cheia preservando o player. O APK é universal, assinado com chave de debug para instalação direta e testes, com assinatura verificada por `apksigner`. A publicação em loja exige uma chave de release própria.

Referências: [Syncplay](https://github.com/Syncplay/syncplay/blob/master/syncplay/protocols.py), [Media3](https://developer.android.com/media/media3/exoplayer/media-items).
