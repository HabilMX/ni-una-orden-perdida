# Lição 26 — Acompanhar um pedido

> **O que será alcançado:** responder «o que aconteceu com o pedido 4006?» com uma única consulta: cada canal, cada tentativa,
> o que saiu em cada uma por inteiro, o que o parceiro respondeu e se o pedido ficou completo. E servir essa resposta
> por HTTP, buscando pelo número do pedido.

**Antes de começar é preciso ter:** as lições 15 e 25 concluídas e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

São onze lições guardando coisas na base de estado: o pedido (15), cada operação e cada tentativa (15), os
conflitos (18), as confirmações do parceiro (21), os alertas e o que está completo (25). Cada peça responde a uma pergunta pequena.
Hoje elas são reunidas para responder à grande, a que faz um cliente ou alguém do suporte: **«meu pedido não chegou, o que
aconteceu com ele?»**. E responde-se pela **chave de negócio**, o número do pedido, que é a única coisa que quem pergunta traz.

**A imagem:** o rastreamento de um pacote na página de uma transportadora. Digita-se o código de rastreio e vê-se a história
completa: recebido na agência, em trânsito, tentativa de entrega frustrada porque não havia ninguém, entregue. Não se vê só
o estado de hoje: vê-se **cada passo**, com a hora. Se dissesse apenas «entregue», não daria para saber que houve uma tentativa frustrada,
nem por que demorou dois dias.

---

## 1. Prepare a pasta

```bash
cd lecciones/26-seguir-una-orden
cp -R inicio mio
```

O router tem dois arquivos novos:

| Arquivo | O que é | É para escrever? |
|---|---|---|
| `orden-router/recorrido.camel.yaml` | Ao iniciar, declara a view com o que `recorrido.sql` disser. E serve `GET /ordenes/{orden}/recorrido` | Não, só leia |
| `orden-router/recorrido.sql` | A view `recorrido` da base de estado | **Sim** |

Uma **view** é uma consulta guardada com nome. Lê-se como se fosse uma tabela (`SELECT * FROM recorrido WHERE
orden = '4006'`), mas não guarda nada próprio: cada vez que é lida, volta a juntar as tabelas de verdade. Por isso não
fica desatualizada.

E há uma tabela nova a partir de hoje, `payload`. Cada tentativa guarda em `intento` a **impressão digital** (`sha256`) e o
**tamanho**, e o corpo completo do que saiu vive em `payload`, uma vez por impressão digital. Se duas tentativas mandaram a
mesma coisa, compartilham a linha.

Abra `infra/postgres/01-estado.sql` e localize as tabelas que serão juntadas: `operacion`, `intento`, `payload`, `acuse`,
`alerta` e `completitud`.

## 2. Faça a previsão

Responda em um papel:

1. Um cliente diz que o pedido 4006 não chegou. O que consultar primeiro: o log do router, a API, o ERP ou a base de
   estado? Por quê?
2. A API estava fora do ar quando o 4006 chegou; depois alguém fez um rerun. Se a view mostra só a última
   tentativa de cada canal, que parte da história se perde?
3. O pedido 4007 traz 150 linhas. O log do router o mostra por inteiro?

Veja como está hoje:

```bash
./verifica inicio
```

A view existe e o `GET` responde, mas é um **painel de hoje**: uma linha por operação, com a última tentativa. A
história do canal rest do 4006 aparece como um único `aceptada`, sem a primeira tentativa em que não se soube nem quem fez o
rerun. E a coluna `cuerpo` sai vazia: não diz o que saiu.

## 3. Monte o percurso

Abra `mio/orden-router/recorrido.sql`. É um `CREATE VIEW` com um `SELECT` que já traz as colunas com o nome
final; algumas saem em `NULL` por enquanto. Tem três TODO:

| TODO | O que mudar | Para quê |
|---|---|---|
| 1 | O `LEFT JOIN LATERAL (… LIMIT 1) u` por um `LEFT JOIN intento i ON i.operacion = p.id`, e as colunas `u.` por `i.` | Uma linha para **cada** tentativa, não só a última |
| 2 | Um `LEFT JOIN payload d ON d.sha256 = i.sha256`, e `cuerpo` sai de `d.cuerpo` | O que saiu em cada tentativa, por inteiro |
| 3 | Três colunas que hoje são `NULL`: `acuses`, `alertas` e `completa` | O que veio depois do envio |

Para o TODO 3, duas das colunas são uma **subconsulta** que junta várias linhas em um texto com `string_agg`:

```sql
(SELECT string_agg(a.codigo, ',' ORDER BY a.id) FROM acuse a WHERE a.operacion = p.id) AS acuses,
```

Essa dá, por exemplo, `A` ou `R`: o que o parceiro disse dessa operação. A de `alertas` é igual, sobre a tabela
`alerta`, para o mesmo pedido, o mesmo evento do ciclo de vida e o mesmo canal, com `l.tipo || ' en ' || l.estado`. E `completa`
sai de um `LEFT JOIN completitud c ON c.orden = p.orden AND c.momento = p.momento`, com `c.completa`.

Por que `LEFT JOIN` e não `JOIN`? Porque uma operação que ainda não tem tentativas, ou uma tentativa sem confirmação, ou um
evento que não se completou, **também tem de aparecer**. Com `JOIN` desapareceriam, e o que falta é justamente o
que mais importa ao acompanhar um pedido.

E por que subconsultas para confirmações e alertas, e não mais `JOIN`? Porque um `JOIN` contra uma tabela com várias linhas por
operação **multiplica** as linhas: cada tentativa sairia uma vez para cada confirmação. A subconsulta as junta em uma única
célula.

Deixe os nomes das colunas como estão: o `GET` os usa.

## 4. Quebre um de propósito

Com a view concluída, tire o `LEFT JOIN completitud` e deixe `completa` como `CAST(NULL AS timestamptz)`. Rode
`./verifica`.

Antes, faça a previsão: o 4007 se completou. O que diz o percurso?

Que não. Sai em vermelho a última marca: a view diz que **nem** o 4007 **nem** o 4006 ficaram completos. Nada falhou:
a view simplesmente não olhava essa tabela, e uma célula vazia se lê como «não aconteceu». **Um percurso que não mostra algo
não prova que não aconteceu.** Por isso ele é montado com `LEFT JOIN` contra tudo o que a base sabe. Devolva a linha.

## 5. Confira

```bash
./verifica
```

O `verifica` desliga a API, manda o 4006 (que pede um produto que o parceiro não conhece) e o 4007 (grande, com 150
linhas); volta a ligar a API, faz um rerun do canal rest dos dois e pede o percurso. Está concluída
quando aparecem as oito marcas verdes:

```
✅ Arrancaron el router (con su GET /ordenes/{orden}/recorrido), los tres canales y el sondeo
✅ El router declaró la vista recorrido al arrancar
✅ GET /ordenes/4006/recorrido: (…) filas, una por cada intento de la 4006
✅ El canal rest de la 4006 cuenta su historia: (…)
✅ El canal del socio de la 4006: rechazada, con su 997 de rechazo (R) en el recorrido
✅ Cada intento del recorrido trae lo que salió en él (cuerpo)
✅ La 4007 (grande): su cuerpo está entero en el recorrido, (…) bytes con su huella y sus 150 renglones
✅ El recorrido dice que la 4007 quedó COMPLETA y la 4006 no
```

Agora peça o percurso à mão:

```bash
curl -s localhost:8080/ordenes/4006/recorrido | python3 -m json.tool | less
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/recorrido.sql solucion/orden-router/recorrido.sql` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `No existe la vista recorrido` | O `recorrido.sql` tem um erro de SQL: o router o diz no log, embaixo dessa marca |
| `la vista tiene … y la 4006 tiene … intentos` | Continua o `LATERAL … LIMIT 1`, ou o `JOIN` de uma confirmação multiplicou as linhas |
| `El canal rest de la 4006 en el recorrido: «aceptada/manual»` sem o que veio antes | Só aparece a última tentativa: falta o TODO 1 |
| `intentos del recorrido sin su cuerpo` | Falta o `LEFT JOIN payload`, ou ele une por outra coluna: é `sha256` |
| `El canal del socio de la 4006 … (esperaba «rechazada R»)` | A coluna `acuses` continua em `NULL` |
| `Completa en el recorrido` | Falta o `LEFT JOIN completitud` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

Se `recorrido.sql` for alterado com os serviços no ar, reinicie o router: a view é declarada ao iniciar.

---

## O que isto ensina fora do curso

**Busca-se pelo que quem pergunta traz.** Ninguém do suporte sabe o `id` de uma operação nem o número de controle
de um X12. Sabe o número do pedido. O percurso é montado a partir daí.

**O log não é o registro.** O log corta os corpos grandes, mistura-se com o de outros pedidos e vai embora com o
contêiner. A base guarda cada tentativa com o que saiu, por inteiro, enquanto ninguém o apagar de propósito.

**A história vale mais que o estado.** «Aceito» diz como terminou; a história diz que primeiro não se soube, que o
disjuntor não o mandou (lição 22), que alguém da mesa de controle fez um rerun e que a API respondeu 201. No
`verifica` aparece assim: `no_se_sabe/auto no_enviado/auto aceptada/manual`. Com isso se
explica por que demorou.

**Uma view não fica desatualizada.** Não copia os dados: volta a juntá-los a cada vez. É a forma mais barata de ter
uma resposta nova sem uma segunda fonte de verdade.

## Exercício: o pedido problema

Com a versão própria concluída, rode `./verifica` e depois:

1. Peça o percurso do 4006 e, **só com ele**, escreva um parágrafo para o cliente que diz que o pedido dele «não
   chegou». Chegou à API? Ao ERP? Ao parceiro? O que aconteceu com o parceiro, em que linha e com que produto? (Dica:
   o corpo da tentativa do parceiro e o 997 da lição 21.)
2. Procure o 4007 no log do router: `DONDE=mio docker compose -f compose.yaml logs orden-router | grep 4007`. Dá para ver
   as 150 linhas dele? Compare com a coluna `tamano` do percurso.
3. Escreva a consulta que seria entregue a alguém do suporte para ver **só os canais que não terminaram bem** de um
   pedido, com o último desfecho. Comece com `SELECT canal, estado_canal, …  FROM recorrido WHERE orden = '4006'`.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O percurso é uma view](../decisiones/30-el-recorrido-es-una-vista.md)

## Revisão

1. O que é uma view, e por que não fica desatualizada?
2. Por que o percurso é buscado pelo número do pedido?
3. O que a tabela `payload` guarda, e como ela se une a cada tentativa?
4. Por que `LEFT JOIN` e não `JOIN`? Por que subconsultas para as confirmações?
5. **A pergunta de encerramento:** um cliente diz que o pedido dele não chegou. O que consultar primeiro, e por que o percurso e
   não o log?

**A seguir:** na lição 27 volta-se a olhar o percurso do 4008. Ele traz o nome, o endereço, o telefone e o
e-mail de uma pessoa. Quem deveria poder vê-los?
