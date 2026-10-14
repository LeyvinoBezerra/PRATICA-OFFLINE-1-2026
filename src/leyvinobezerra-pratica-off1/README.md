# Prática OFF1 — Sistemas Distribuídos

Repositório das atividades práticas de Sistemas Distribuídos.

## Módulos

- [01 — MiniDNS](01-MiniDNS/README.md)
- [02 — Service Discovery](02-ServiceDiscovery/README.md)
- [03 — P2P](03-P2P/README.md)
- [04 — CDN](04-CDN/README.md)
- [Roteiro da apresentação](apresentacao/roteiro.md)

## Decisões de projeto

Os módulos mantêm componentes com responsabilidades separadas e protocolos simples sobre
sockets. As escolhas de estruturas e concorrência são explicadas junto ao código nos pontos
em que afetam o comportamento, e resumidas na seção “Decisões de implementação” do README de
cada módulo. As explicações destacam os compromissos didáticos — por exemplo, latência e
confiabilidade de UDP, sincronização de estado compartilhado, limites de memória e
encaminhamento distribuído — sem tratar esses exemplos como mecanismos de produção.
