# 03 — P2P

Anel P2P com seis processos TCP. Cada processo armazena dez arquivos consecutivos,
conhece seu antecessor e sucessor e encaminha buscas até o nó responsável. Entrada
malformada ou fora da faixa de 1 a 60 é rejeitada antes de entrar no anel.

Execute a partir da raiz do repositório:

```powershell
javac -d out (Get-ChildItem src\leyvinobezerra-pratica-off1\03-P2P\src -Recurse -Filter *.java).FullName
java -cp out p2p.RingDemo
```

O demo executa a busca válida por `arquivo35.ext`, a busca fora do intervalo por
`arquivo99.ext` e a entrada inválida `arquivoABC.ext`. Cada nó imprime os eventos de
recebimento, encaminhamento e resposta.

## Decisões de implementação

- A faixa contígua de dez arquivos por nó permite determinar localmente se ele é responsável,
  sem consultar um índice central. O sucessor modular liga o último nó ao primeiro e forma
  um anel simples de percorrer.
- O identificador do arquivo é validado antes da busca e o contador de saltos limita o
  encaminhamento a uma volta; assim, uma chave ausente termina com `NOT_FOUND` em vez de
  circular para sempre.
- Cada nó aceita conexões TCP e encaminha a busca ao sucessor, mantendo o protocolo e as
  responsabilidades locais simples para observar o fluxo distribuído no demo.
