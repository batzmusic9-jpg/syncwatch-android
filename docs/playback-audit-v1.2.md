# Auditoria anterior à implementação — 1.2

Base auditada: `b505a90e89adf07a87c6d4507de8e8e9adbe33a4`, Android 1.1.0 (versionCode 2).

## Diagnóstico

Arquivo relatado: Matroska/MKV, HEVC 1920×804 a 23.976 fps, AC3/A52 5.1 a 48 kHz, várias legendas internas. Funciona no VLC Android; falha no SyncWatch 1.1. O arquivo e o log do aparelho não foram fornecidos. Portanto a causa exata, decoder e erro original NÃO foram reproduzidos.

O ExoPlayer 1.6.1 aceita Matroska. O contêiner, isoladamente, não explica a falha. A configuração do app usa apenas os decoders Android disponíveis, sem extensões FFmpeg. HEVC depende de perfil, nível e decoder do aparelho. AC3 é a suspeita principal: suporte não é universal no Android e não há extensão de áudio no projeto. Legendas internas podem também depender de seu codec. MIME incorreto no provedor de documentos pode impedir que o seletor `video/*` mostre o MKV, antes mesmo de decodificar.

O listener anterior exibe somente `PlaybackException.getErrorCodeName()`, sem registrar errorCode, causa, renderer, formato e decoder. Não é possível recuperar esses dados retroativamente. A migração não é apresentada como prova do diagnóstico.

Referência oficial: https://developer.android.com/media/media3/exoplayer/supported-formats (Matroska, decoders da plataforma e extensão FFmpeg/AC3).

## Mapa das ligações existentes

- `SyncProtocol`: JSON, Hello/List/Set/State, prontidão e contadores `ignoringOnTheFly`. Não depende de Media3. Deve permanecer idêntico.
- `SyncConnection`: TCP, STARTTLS, validação de hostname/certificado, heartbeat, entrega no executor principal. Não depende do player. Deve permanecer idêntico.
- `SyncPolicy`: segundos→milissegundos, compensação de latência limitada a 2 s; seek somente no primeiro estado, seek explícito ou diferença ≥1 s. Deve permanecer idêntico. 600000 vs 599700 não provoca seek.
- `MainActivity`: conecta estado local ao protocolo e aplica play/pausa/seek remoto com `applyingRemote`; anuncia nome/tamanho/duração; abre URI, restaura posição e pausa no onStop.
- Player/View: ExoPlayer/PlayerView, eventos de comando, estado READY/ENDED/erro, surface, fullscreen/landscape e controles.
- Legendas externas: reconstroem MediaItem e recarregam o filme para aplicar offset. Substituir por trilha externa nativa e `setSpuDelay`, em microssegundos.
- Instrumentação: usa ExoPlayer/Player/CueGroup diretamente. Adaptar à abstração e verificar LibVLC real; manter testes de protocolo, TCP e SubtitleTiming.

## Escolha e limites

Dependência oficial estável `org.videolan.android:libvlc-all:3.7.7`, publicada no Maven Central; API conferida no sources JAR dessa mesma versão. Versões 4.0 eap foram descartadas por serem prévias. LibVLC inclui demuxers e decoders nativos; habilitar aceleração quando disponível, com fallback, e desabilitar passthrough para AC3 decodificado no aparelho.

https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/

Os callbacks nativos são assíncronos. A abstração emitirá eventos de COMANDO somente de suas chamadas públicas, de modo síncrono. Eventos nativos apenas atualizarão prontidão/estado/trilhas/erro. Assim o guard da Activity cobre os comandos remotos e callbacks tardios não geram ecos. Posição sempre em milissegundos.

O evento EncounteredError do LibVLC não fornece PlaybackException/cause/decoder Java. Registrar o código/nome nativo, metadata de trilhas, MIME declarado, candidatos Android e exceções Java; preservar logcat VLC para informações de demux/decoder. Não inventar nomes de decoders selecionados.

Validação automática com arquivos sintéticos é distinta da aceitação do filme real e de dois aparelhos físicos. Ainda será necessário testar esse MKV e a sincronização nos celulares.
