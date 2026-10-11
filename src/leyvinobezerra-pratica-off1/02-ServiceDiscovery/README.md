# 02 — Service Discovery

Descoberta de serviços via diretório TCP. Três servidores de calculadora registram as
quatro operações; o diretório escolhe os endereços em round robin e três clientes
descobrem e executam soma, multiplicação e divisão.

Execute a partir da raiz do repositório:

```powershell
javac -d out (Get-ChildItem src\leyvinobezerra-pratica-off1\02-ServiceDiscovery\src -Recurse -Filter *.java).FullName
java -cp out discovery.ServiceDiscoveryDemo
```

As portas TCP são alocadas dinamicamente para permitir execução local sem depender de
portas fixas. Os servidores processam conexões em threads independentes.
