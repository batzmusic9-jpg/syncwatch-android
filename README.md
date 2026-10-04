# SyncWatch Android

Assista a vídeos locais com outras pessoas, sincronizando reprodução pelo protocolo Syncplay.
Android 8.0 ou superior. App nativo em Java, player Media3/ExoPlayer e seletor de documentos do Android.

## Instalar e usar

1. Baixe `SyncWatch-1.0.0.apk` do artefato `SyncWatch-APK` em uma execução bem-sucedida de **Actions → Android APK**.
2. Autorize o Android a instalar o APK pelo navegador/gerenciador de arquivos utilizado.
3. Cada pessoa escolhe sua própria cópia do mesmo vídeo. Não há transmissão ou upload do filme.
4. Digitem o mesmo servidor (padrão `syncplay.pl`), porta `8997` e nome de sala. Cada pessoa usa um nome diferente.
5. Entrem na sala, confiram os participantes, marquem **Estou pronto** e usem o player para play, pausa e saltos.

Também pode participar alguém com Syncplay no computador. Use salas comuns; salas controladas, senhas de servidor e playlists compartilhadas não são suportadas nesta versão. A marcação de pronto informa o estado aos participantes; não bloqueia o play.

## Funcionalidades

- Vídeos locais pelo seletor Android, sem permissão de acesso amplo ao armazenamento.
- Play, pausa e seek compartilhados; correção de deriva a partir de um segundo e compensação de latência.
- Lista de participantes, arquivo selecionado e prontidão; chat e compartilhamento dos dados da sala.
- Negociação STARTTLS com validação de certificado e hostname. Se o servidor não oferecer TLS, a conexão comum é permitida, exceto quando **Exigir TLS** estiver marcado. O status mostra a proteção usada.
- Dados de conexão e acesso ao último vídeo salvos no aparelho. Nenhuma senha ou vídeo é enviado ao servidor; ele recebe nome, sala, nome/tamanho/duração do arquivo e comandos/chat.
- Ao ir para segundo plano, o app pausa a reprodução. Reconexão é manual pelo botão de entrar.

Formatos e codecs dependem do aparelho. MP4 com H.264/AAC é o formato recomendado para o primeiro teste. Para sincronização, mantenham cópias de duração idêntica. Legendas externas, streaming do arquivo e reprodução em segundo plano não estão incluídos.

## Compilar

JDK 17, Android SDK 35, Gradle 8.11.1, Android Gradle Plugin 8.9.2.

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

O workflow configura as ferramentas, executa testes de protocolo e conexão TCP simulada, roda o lint, compila um APK universal assinado com chave de debug e verifica a assinatura com `apksigner`. A chave de debug do runner muda entre builds: se uma atualização informar conflito de assinatura, desinstale o APK anterior antes de instalar o novo. Este APK é para instalação direta e testes; publicação em loja requer assinatura de release própria.

## Validação

Os testes automatizados cobrem confirmação de estado, prevenção de sobrescrita de comandos locais, seek remoto, heartbeat sem vídeo, prontidão/lista de sala, chat, cálculos de posição, negociação TLS e comunicação por socket real em localhost. O build sozinho não comprova a precisão em dois aparelhos físicos. Faça um teste com duas cópias do mesmo arquivo e os dois aparelhos na mesma sala, validando play/pausa/seek dos dois lados.

Referências: [protocolo Syncplay](https://github.com/Syncplay/syncplay/blob/master/syncplay/protocols.py), [Media3](https://developer.android.com/media/media3/exoplayer/hello-world).
