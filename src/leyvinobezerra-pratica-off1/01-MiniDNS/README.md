# 01 — MiniDNS

MiniDNS sobre UDP: mantém os dez nomes iniciais e identifica automaticamente o papel do
cliente pelo comando recebido. `GET` identifica um visitante, que consulta um nome e fica
inscrito para receber datagramas `UPDATE`; `SET` identifica um registrador, que altera o
endereço de um nome cadastrado e notifica os visitantes inscritos.

Execute a partir da raiz do repositório:

```powershell
javac -d out (Get-ChildItem src\leyvinobezerra-pratica-off1\01-MiniDNS\src -Recurse -Filter *.java).FullName
```

Inicie primeiro o servidor, que escuta por padrão na porta UDP 5354 em loopback:

```powershell
java -cp out minidns.MiniDnsServer
```

Opcionalmente, informe a porta e o endereço de bind do servidor:
`java -cp out minidns.MiniDnsServer 5354 0.0.0.0`.

Em outro terminal, inicie um visitante indicando um dos nomes cadastrados. Ele consulta o nome e
permanece aguardando notificações; o endereço e a porta do servidor são opcionais e assumem
`127.0.0.1` e `5354`:

```powershell
java -cp out minidns.MiniDnsClient visitante servidor1
java -cp out minidns.MiniDnsClient visitante servidor1 127.0.0.1 5354
```

Um registrador informa o nome e o novo endereço IPv4. Após responder ao `SET`, o servidor envia
`UPDATE` aos visitantes que consultaram aquele nome:

```powershell
java -cp out minidns.MiniDnsClient registrador servidor1 192.168.0.11
java -cp out minidns.MiniDnsClient registrador servidor1 192.168.0.11 127.0.0.1 5354
```

O servidor reconhece o papel pela operação UDP (`GET` para visitante e `SET` para registrador);
essa identificação de protocolo não autentica a origem do registrador.

## Decisões de implementação

- UDP foi escolhido para consultas pequenas e notificações sem manter uma conexão TCP por
  cliente. Como datagramas podem se perder, o cliente aplica timeout e o servidor responde
  erros explícitos para comandos e nomes inválidos.
- O servidor mantém os registros em um `ConcurrentHashMap` e os assinantes em conjuntos
  concorrentes por nome: isso permite atualizar um registro sem duplicar o mesmo endpoint
  inscrito e mantém a estrutura pronta para tratamento concorrente.
- A inscrição acontece no `GET` bem-sucedido, usando o endpoint de origem do datagrama como
  destino das notificações. A classificação por `GET`/`SET` é didática, não um mecanismo de
  autenticação.
