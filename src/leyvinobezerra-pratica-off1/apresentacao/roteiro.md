# Roteiro da apresentação

## Abertura

- Apresente o objetivo: demonstrar nomeação, descoberta de serviços, busca P2P e
  distribuição de conteúdo em sistemas distribuídos.
- Informe que as implementações usam Java, sockets TCP/UDP e threads, sem bibliotecas
  externas.

## MiniDNS

- Explique nome, endereço e binding dinâmico.
- Execute `minidns.MiniDnsDemo`: mostre as consultas iniciais dos três clientes e as
  notificações UDP depois das atualizações do registrador para servidor1, servidor4 e
  servidor9.
- Comente que nomes desconhecidos e comandos/IPs inválidos recebem uma resposta de erro.

## Service Discovery

- Explique o registro de servidores no diretório e a seleção round robin.
- Execute `discovery.ServiceDiscoveryDemo`: acompanhe três chamadas de soma para ver a
  rotação entre as calculadoras; demonstre também multiplicação e divisão.
- Comente que serviços indisponíveis, operações desconhecidas, números inválidos e
  divisão por zero são tratados.

## P2P

- Explique a topologia em anel, as faixas de arquivos e o encaminhamento sem diretório
  central.
- Execute `p2p.RingDemo`: destaque o fluxo P0 → P1 → P2 → P3 para arquivo35.ext e os logs
  de envio/recebimento.
- Mostre que arquivo99.ext e arquivoABC.ext são rejeitados sem encaminhamento nem parada
  dos nós.

## CDN

- Apresente Origin, Edges, Router e cliente; explique cache hit/miss, TTL e invalidação.
- Siga o README da CDN para iniciar os componentes e execute os cenários documentados:
  primeira requisição e repetição do mesmo arquivo, expiração de TTL e atualização da
  origem com invalidação dos Edges.
- Compare os logs de hit/miss e mostre que a invalidação força nova busca na origem.

## Encerramento

- Resuma como a localização é abstraída por nomes/diretório, pelo anel ou pelo Router.
- Comente dificuldades de concorrência, validação de entradas e consistência de cache,
  além de possíveis falhas de rede como timeout e serviço indisponível.
