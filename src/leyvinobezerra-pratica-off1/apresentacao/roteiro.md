# Roteiro da apresentação

## Abertura

- Apresente o objetivo: demonstrar nomeação, descoberta de serviços, busca P2P e
  distribuição de conteúdo em sistemas distribuídos.
- Informe que as implementações usam Java, sockets TCP/UDP e threads, sem bibliotecas
  externas.

## MiniDNS

- Explique nome, endereço e binding dinâmico.
- Execute `minidns.MiniDnsServer` e clientes `minidns.MiniDnsClient` nos modos `visitante`
  e `registrador`: mostre consultas e notificações UDP depois de uma atualização.
- Comente que nomes desconhecidos e comandos/IPs inválidos recebem uma resposta de erro.
- Explique que `ConcurrentHashMap` e conjuntos por nome mantêm registros e inscrições
  compartilhados sem duplicatas; `GET`/`SET` identificam o papel no protocolo, mas não
  autenticam o registrador.

## Service Discovery

- Explique o registro de servidores no diretório e a seleção round robin.
- Execute `discovery.ServiceDiscoveryDemo`: acompanhe três chamadas de soma para ver a
  rotação entre as calculadoras; demonstre também multiplicação e divisão.
- Comente que serviços indisponíveis, operações desconhecidas, números inválidos e
  divisão por zero são tratados.
- Relacione o catálogo concorrente e round robin do diretório à separação entre descoberta
  (plano de controle) e execução direta na calculadora (plano de dados).

## P2P

- Explique a topologia em anel, as faixas de arquivos e o encaminhamento sem diretório
  central.
- Execute `p2p.RingDemo`: destaque o fluxo P0 → P1 → P2 → P3 para arquivo35.ext e os logs
  de envio/recebimento.
- Mostre que arquivo99.ext e arquivoABC.ext são rejeitados sem encaminhamento nem parada
  dos nós.
- Explique como as faixas locais removem a necessidade de um índice central e o limite de
  saltos garante que a busca termine mesmo sem encontrar o arquivo.

## CDN

- Apresente Origin, Edges, GeoDNS UDP e cliente; explique cache hit/miss, TTL e invalidação.
- Siga o README da CDN para iniciar os componentes e execute os cenários documentados:
  primeira requisição e repetição do mesmo arquivo, expiração de TTL e atualização da
  origem com invalidação dos Edges.
- Compare os logs de hit/miss e mostre que a invalidação força nova busca na origem.
- Explique a LRU limitada, o pool fixo de atendentes e a geração de invalidação que evita
  armazenar uma resposta antiga depois de uma atualização concorrente.

## Encerramento

- Resuma como a localização é abstraída por nomes/diretório, pelo anel ou pelo GeoDNS.
- Comente dificuldades de concorrência, validação de entradas e consistência de cache,
  além de possíveis falhas de rede como timeout e serviço indisponível.
