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
