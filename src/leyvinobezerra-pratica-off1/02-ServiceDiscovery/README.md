# 02 — Service Discovery

Descoberta de serviços via diretório TCP, organizada em classes e processos independentes:
`DirectoryServer` mantém os registros e distribui endereços em round robin,
`CalculatorServer` registra e executa as operações, e `ServiceDiscoveryClient` consulta
o diretório e envia o cálculo ao servidor escolhido.

Execute a partir da raiz do repositório:

```powershell
javac -d out (Get-ChildItem src\leyvinobezerra-pratica-off1\02-ServiceDiscovery\src -Recurse -Filter *.java).FullName
```

Para executar cada componente em processos/terminais separados, inicie primeiro o diretório
(porta padrão `5000`):

```powershell
java -cp out discovery.DirectoryServer
```

Inicie um ou mais servidores de calculadora em outros terminais. Cada servidor registra
automaticamente soma, subtração, multiplicação e divisão no diretório. A porta `0` seleciona
uma porta disponível; o servidor anuncia automaticamente a porta escolhida:

```powershell
java -cp out discovery.CalculatorServer Calculadora-A
java -cp out discovery.CalculatorServer Calculadora-B
```

Execute clientes em outros terminais. Os serviços disponíveis são `soma`, `subtracao`,
`multiplicacao` e `divisao`:

```powershell
java -cp out discovery.ServiceDiscoveryClient soma 8 5
java -cp out discovery.ServiceDiscoveryClient divisao 20 4 127.0.0.1 5000
```

Argumentos opcionais: `DirectoryServer [porta] [endereco-bind]`;
`CalculatorServer [nome] [porta] [diretorio-host] [diretorio-porta] [bind] [host-anunciado]`;
`ServiceDiscoveryClient operacao numero1 numero2 [diretorio-host] [diretorio-porta]`.
Os valores padrão são loopback e porta `5000` para o diretório. Para acesso por outros
computadores, configure o bind e o host anunciado da calculadora para endereços alcançáveis
pelos clientes.

O exemplo integrado anterior continua disponível para executar tudo em uma única JVM:

```powershell
java -cp out discovery.ServiceDiscoveryDemo
```

Os servidores processam conexões em threads independentes. Em execução separada, inicie
sempre o diretório antes dos servidores de calculadora.

## Decisões de implementação

- O diretório separa o plano de descoberta do plano de execução: o cliente consulta uma vez
  o serviço, recebe um endpoint e faz o cálculo diretamente na calculadora, sem manter o
  diretório no caminho de cada operação.
- O catálogo usa um mapa concorrente e listas copy-on-write: registros são pouco frequentes
  frente às leituras de descoberta, e cada consulta pode percorrer os servidores sem bloquear
  as demais. Um contador atômico por operação implementa round robin entre instâncias.
- Cada conexão é tratada independentemente para que uma requisição lenta não bloqueie o
  acceptor. Os timeouts TCP limitam o tempo de espera por um componente indisponível.
