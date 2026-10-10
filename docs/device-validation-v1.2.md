# Aceitação em celulares — SyncWatch 1.2.0

**REAL MKV DEVICE TEST REQUIRED**

O filme real não está no repositório. O CI usa arquivos sintéticos, incluindo Matroska com HEVC 1920×804, 24000/1001 fps, AC3 5.1/48 kHz, áudio alternativo AAC e duas legendas SRT internas. Reprodução no emulador não comprova o perfil HEVC, desempenho, áudio audível ou sincronização no aparelho físico. Main10, E-AC3, DTS e outros tipos de legenda interna não têm fixtures nesta validação.

1. Instale `SyncWatch-1.2.0.apk` nos dois celulares e abra a mesma cópia do MKV HEVC/AC3 original. Confira imagem, áudio, duração, pause/play e seek. Se o Android recusar a atualização por assinatura diferente, desinstale a versão anterior e instale esta.
2. Em **Áudio**, troque as faixas. Em **Legendas**, teste as internas, **Nenhuma**, um SRT externo e um VTT externo. Aplique +500 ms e -500 ms e confira o sentido do ajuste sem reiniciar o filme.
3. Entre na mesma sala nos dois celulares, marque **Estou pronto** e confira os participantes. Faça play, pausa e seek em cada um; verifique se o outro acompanha, sem alternâncias espontâneas nem saltos repetidos. Se possível confira também com Syncplay no computador em `syncplay.pl:8997`.
4. Gire para landscape, use **Tela cheia**, volte e saia do app: confira proporção, continuidade dos controles e pausa no segundo plano. Reabra e confira a posição salva.

Caso falhe, informe modelo/Android, arquivo/trilha escolhida e o que ocorreu. Para diagnóstico por ADB: `adb logcat -d -v time` contém `SyncWatchPlayback` e os logs `VLC` com demux/decoder. EncounteredError não expõe a causa ou o decoder na API Java; os logs nativos são necessários.
