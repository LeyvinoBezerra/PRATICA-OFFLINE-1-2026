# 01 — MiniDNS

MiniDNS sobre UDP: mantém os dez nomes iniciais, responde a consultas `GET` e aceita
atualizações de um registrador (`SET`). Clientes que consultaram um nome ficam inscritos
e recebem datagramas `UPDATE` quando seu endereço muda.

Execute a partir da raiz do repositório:

```powershell
javac -d out (Get-ChildItem src\leyvinobezerra-pratica-off1\01-MiniDNS\src -Recurse -Filter *.java).FullName
java -cp out minidns.MiniDnsDemo
```

O demo inicia o servidor e três clientes requisitantes, consulta `servidor1`, `servidor4` e
`servidor9`, e então demonstra as notificações enviadas após o binding dinâmico. O servidor
escuta na porta UDP 5354 em loopback.
