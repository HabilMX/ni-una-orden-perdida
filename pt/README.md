# Nenhum pedido perdido

**Por Dorian Chávez, fundador de Hábil y arquitecto de integración.**

**Para quem é:** quem já usa o terminal e o git, lê JSON e sabe SQL básico, e quer aprender integração de
sistemas. Não é preciso saber Java nem Camel: as rotas são YAML e se aprendem aqui.

**O que se sabe ao terminar:** conseguir, com as próprias mãos, que nenhum pedido se perca, que tudo fique rastreável, que
uma nova tentativa não duplique e que um destino fora do ar não derrube os demais.

**O que é preciso antes de começar:** terminal e git, ler JSON e SQL básico, e Docker com Compose. De Docker basta
`docker compose up` e `down`; o resto vem escrito. Funciona em Linux, macOS e Windows com WSL.

## O projeto

Uma loja inventada vende, e cada pedido precisa chegar a três sistemas que não se parecem entre si: uma API REST, um
ERP em MariaDB e um parceiro comercial que recebe documentos EDI X12 por SFTP. Tudo roda na própria máquina com Docker. É um
curso prático, aberto e gratuito, de integração de sistemas com [Apache Camel](https://camel.apache.org/).

## As lições

São 31 lições, da 00 à 30, todas em Docker Compose, mais um apêndice opcional só de leitura. Fazem-se em
ordem: cada uma parte do que a anterior deixou. Comece pela [00](00-ambiente.md), que sobe o ambiente.

### Parte 1 — Que chegue

| # | Lição |
|---|---|
| 00 | [Ambiente pronto](00-ambiente.md) |
| 01 | [Minha primeira rota roda](01-mi-primera-ruta.md) |
| 02 | [O pedido chega à API](02-la-orden-llega-a-la-api.md) |
| 03 | [Traduzir o pedido](03-traducir-la-orden.md) |
| 04 | [Ao ERP](04-al-erp.md) |
| 05 | [Tudo ou nada](05-todo-o-nada.md) |
| 06 | [Montar o EDI](06-armar-el-edi.md) |
| 07 | [Ao parceiro](07-al-socio.md) |
| 08 | [O router](08-el-router.md) |
| 09 | [O fio entre serviços](09-el-hilo-entre-servicios.md) |
| 10 | [Todos os canais](10-todos-los-canales.md) |
| 11 | [Nem tudo vai para todos](11-no-todo-va-a-todos.md) |
| 12 | [Crescer em réplicas](12-crecer-en-replicas.md) |

### Parte 2 — Que avance

| # | Lição |
|---|---|
| 13 | [Chega o embarque](13-llega-el-embarque.md) |
| 14 | [Um arquivo, vários pedidos](14-un-archivo-varias-ordenes.md) |
| 15 | [O estado vive fora do processo](15-el-estado-vive-fuera-del-proceso.md) |
| 16 | [Chega a entrega](16-llega-la-entrega.md) |
| 17 | [A entrega chegou antes do código de rastreio](17-la-entrega-llego-antes-que-la-guia.md) |

### Parte 3 — Que não falhe

| # | Lição |
|---|---|
| 18 | [Quando duas mensagens são a mesma?](18-cuando-dos-mensajes-son-el-mismo.md) |
| 19 | [O destino não responde](19-el-destino-no-contesta.md) |
| 20 | [Aceito, rejeitado ou não se sabe](20-aceptado-rechazado-o-no-se-sabe.md) |
| 21 | [Enviado não é confirmado](21-enviado-no-es-confirmado.md) |
| 22 | [Derrubar um canal](22-tirar-un-canal.md) |
| 23 | [Reprocessar o que falhou](23-reprocesar-lo-que-fallo.md) |
| 24 | [Não derrubar o destino](24-no-tumbar-al-destino.md) |
| 25 | [Esperar pelos canais](25-esperar-a-los-canales.md) |
| 26 | [Acompanhar um pedido](26-seguir-una-orden.md) |
| 27 | [O que a trilha não deve mostrar](27-lo-que-el-rastro-no-debe-mostrar.md) |

### Parte 4 — Que escale

| # | Lição |
|---|---|
| 28 | [Um tópico, um grupo por integração](28-un-topico-un-grupo-por-integracion.md) |
| 29 | [Não perder ao consumir](29-no-perder-al-consumir.md) |
| 30 | [Não perder ao publicar](30-no-perder-al-publicar.md) |

### Apêndice opcional

O curso termina na lição 30. O apêndice leva uma integração para o Kubernetes e a compara em JVM e em nativo; pede uma
máquina com mais memória e disco, e avisa isso logo no início.

| # | Lição |
|---|---|
| 31 | [De laboratório a projeto, no Kubernetes](31-de-laboratorio-a-kubernetes.md) |
| 32 | [JVM contra nativo, medido](32-jvm-contra-nativo.md) |

As decisões de design, com o porquê de cada uma, estão em [`decisiones/`](../decisiones/README.md).
