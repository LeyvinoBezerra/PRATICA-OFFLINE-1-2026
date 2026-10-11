# 04 — CDN

CDN de demonstração em Java puro: uma origem TCP mantém 1.000 arquivos simulados,
edges TCP usam cache LRU limitado a 100 entradas com TTL configurável, e um
roteador UDP direciona clientes conforme a localização fictícia. Quando um
arquivo é atualizado na origem, ela envia invalidação aos edges registrados.

## Requisitos e compilação

Requer JDK 8 ou superior. No PowerShell, a partir desta pasta:

```powershell
javac -d out (Get-ChildItem -Recurse -Path src -Filter *.java | ForEach-Object { $_.FullName })
```

Todos os arquivos de classe serão criados em `out`.

## Executar a demonstração

Abra um terminal por processo, sempre nesta pasta (compile apenas uma vez):

```powershell
java -cp out cdn.Main origin 5000
java -cp out cdn.Main edge 6001 localhost 5000 localhost 100 10
java -cp out cdn.Main edge 6002 localhost 5000 localhost 100 10
java -cp out cdn.Main router 7000 sul=localhost:6001 norte=localhost:6002
```

Os dois edges registram automaticamente o endereço anunciado na origem. O
roteador usa as rotas passadas como `localização=host:porta`. Em outro terminal,
solicite o mesmo arquivo duas vezes para observar `MISS` e depois `HIT` nos logs
do edge:

```powershell
java -cp out cdn.Main client localhost 7000 sul file-0001.txt
java -cp out cdn.Main client localhost 7000 sul file-0001.txt
```

Para observar invalidação ativa, atualize o arquivo através da origem; a
solicitação seguinte deve apresentar `MISS` e retornar o novo conteúdo:

```powershell
java -cp out cdn.Main update localhost 5000 file-0001.txt "conteudo atualizado"
java -cp out cdn.Main client localhost 7000 sul file-0001.txt
```

Os nomes válidos vão de `file-0001.txt` a `file-1000.txt`. O TTL e o limite do
cache podem ser alterados nos argumentos do edge (em segundos e entradas,
respectivamente). `Ctrl+C` encerra cada processo.

## Comandos

- `origin [porta]` — servidor de origem (porta padrão 5000).
- `edge <porta-edge> <host-origem> <porta-origem> <host-anunciado> [capacidade] [ttl-segundos]`
- `router <porta-udp> <localizacao=host:porta>...` — roteador UDP (7000 é uma sugestão).
- `client <host-router> <porta-router> <localizacao> <arquivo>`
- `update <host-origem> <porta-origem> <arquivo> <novo-conteudo>`
