# 04 — CDN

CDN de demonstração em Java puro, organizada em quatro componentes executáveis:
`Cliente` inicia a requisição; `GeoDnsServer` resolve a localização para o edge
por UDP; `EdgeServer` responde com `HIT` se o conteúdo estiver no cache ou busca
na origem e responde com `MISS`; `OriginServer` mantém o conteúdo e invalida os
caches dos edges registrados quando um arquivo é atualizado.

## Requisitos e compilação

Requer JDK 8 ou superior. No PowerShell, a partir desta pasta:

```powershell
javac -d out (Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object { $_.FullName })
```

Todos os arquivos de classe serão criados em `out`.

## Executar os componentes separados

Abra um terminal por processo, sempre nesta pasta (compile apenas uma vez):

```powershell
java -cp out origin.OriginServer 5000
java -cp out edge.EdgeServer 6001 localhost 5000 localhost 100 10
java -cp out edge.EdgeServer 6002 localhost 5000 localhost 100 10
java -cp out geodns.GeoDnsServer 7000 sul=localhost:6001 norte=localhost:6002
```

Os dois edges registram automaticamente o endereço anunciado na origem. O GeoDNS
usa as rotas passadas como `localização=host:porta`. Em outro terminal, solicite
o mesmo arquivo duas vezes para observar `MISS` e depois `HIT` nos logs do edge:

```powershell
java -cp out cliente.Cliente localhost 7000 sul file-0001.txt
java -cp out cliente.Cliente localhost 7000 sul file-0001.txt
```

Para observar invalidação ativa, atualize o arquivo através da origem; a
solicitação seguinte deve apresentar `MISS` e retornar o novo conteúdo:

```powershell
java -cp out cdn.Main update localhost 5000 file-0001.txt "conteudo atualizado"
java -cp out cliente.Cliente localhost 7000 sul file-0001.txt
```

Os nomes válidos vão de `file-0001.txt` a `file-1000.txt`. O TTL e o limite do
cache podem ser alterados nos argumentos do edge (em segundos e entradas,
respectivamente). `Ctrl+C` encerra cada processo.

## Comandos

- `origin.OriginServer [porta]` — servidor de origem (porta padrão 5000).
- `edge.EdgeServer <porta-edge> <host-origem> <porta-origem> <host-anunciado> [capacidade] [ttl-segundos]`
- `geodns.GeoDnsServer <porta-udp> <localizacao=host:porta>...` — resolução UDP (7000 é uma sugestão).
- `cliente.Cliente <host-geodns> <porta-udp> <localizacao> <arquivo>`
- `cdn.Main update <host-origem> <porta-origem> <arquivo> <novo-conteudo>` — atualiza o conteúdo e invalida os edges.

O comando integrado `cdn.Main` continua disponível como atalho para iniciar origem,
edge e GeoDNS com os comandos `origin`, `edge` e `geodns` (o nome antigo `router`
também é aceito), além de executar `client` e `update`.

## Decisões de implementação

- O cliente usa UDP para localizar o edge a partir de uma localização e TCP para transferir
  requisições e conteúdo. A consulta GeoDNS é pequena e sem estado; o fluxo de conteúdo
  confiável usa conexão.
- O cache do edge combina TTL com LRU limitado: o TTL restringe a idade máxima dos dados e
  a política LRU remove primeiro o conteúdo menos recentemente usado quando a capacidade
  acaba. A origem também envia invalidação ativa após atualizar um arquivo.
- A busca na origem ocorre fora do lock do cache para que uma falha de cache não bloqueie
  outros clientes. Uma geração de invalidação por arquivo impede que a resposta antiga de
  uma busca em andamento seja armazenada depois que uma invalidação já chegou.
- Um pool fixo de atendentes limita o número de threads por edge e origem, permitindo
  concorrência sem crescimento ilimitado de recursos.
- `Requisicao` e `Resposta` compartilham um protocolo TCP binário baseado em campos
  `DataInputStream`/`DataOutputStream`; a representação tipada mantém a serialização igual
  entre cliente, edge e origem e diferencia erros, metadados `HIT`/`MISS` e conteúdo.
