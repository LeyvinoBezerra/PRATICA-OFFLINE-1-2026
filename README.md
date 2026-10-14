# Prática Offline 1 — Sistemas Distribuídos

Implementações didáticas em Java usando sockets TCP/UDP e threads. Cada exercício é
independente e possui instruções de compilação e execução em seu README:

- [01 — MiniDNS](src/leyvinobezerra-pratica-off1/01-MiniDNS/README.md): consultas UDP,
  binding dinâmico e notificações para três clientes.
- [02 — Service Discovery](src/leyvinobezerra-pratica-off1/02-ServiceDiscovery/README.md):
  diretório TCP, três calculadoras, operações e balanceamento round robin.
- [03 — P2P](src/leyvinobezerra-pratica-off1/03-P2P/README.md): seis processos TCP em
  anel, busca distribuída e validação das entradas.
- [04 — CDN](src/leyvinobezerra-pratica-off1/04-CDN/README.md): cliente, GeoDNS UDP,
  serviço de edge, servidor de origem, TTL e invalidação ativa.
- [Roteiro de apresentação](src/leyvinobezerra-pratica-off1/apresentacao/roteiro.md).

Requer Java 17 ou superior. Compile cada módulo separadamente; os exemplos usam portas
dinâmicas, exceto o MiniDNS (UDP 5354). Na CDN, os exemplos usam Origin TCP 5000,
GeoDNS UDP 7000 e portas de Edge 6001/6002; consulte o README do módulo para alterar
esses valores.
