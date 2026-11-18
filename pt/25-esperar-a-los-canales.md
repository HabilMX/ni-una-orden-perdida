# Lição 25 — Esperar pelos canais

> **O que será alcançado:** saber quando um pedido está **completo**: quando cada canal que lhe cabe chegou ao que se
> espera dele, incluído o 997 do parceiro. E que um canal que demora demais gere um **alerta** sem dar o pedido por
> fechado, para que o que chegar tarde ainda o complete, mesmo que o router tenha sido reiniciado no meio.

**Antes de começar é preciso ter:** as lições 21 e 22 concluídas e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Desde a lição 10, o router **espalha**: manda cada evento do ciclo de vida do pedido para os canais dele. Nunca o **reúne**: não há um
lugar que diga «este pedido já terminou». E na lição 21 viu-se que «terminar» não é o mesmo para todos: a API e o ERP
terminam quando aceitam, o parceiro quando manda o 997. Espalhar e depois reunir as respostas é um padrão com
nome, *Scatter-Gather*, e a parte de reunir se chama *Aggregator*. Hoje ele é construído, com uma ideia que vem da
lição 15: **o que é preciso reunir já está na base.**

**A imagem:** organizar a ceia de fim de ano com três primos que trazem alguma coisa. A tia não dá a ceia por pronta quando
cada primo disse «estou indo»: dá por pronta quando a comida está na mesa. Se às nove o da sobremesa não
chegou, a tia não risca a sobremesa nem declara a ceia completa: telefona para perguntar. E se a sobremesa chega às
nove e meia, a ceia se completa nessa hora. Se a tia sai um pouco para o quintal, não perde a conta: a mesa continua
ali com o que já chegou.

---

## 1. Prepare a pasta

```bash
cd lecciones/25-esperar-a-los-canales
cp -R inicio mio
```

Abra primeiro `mio/orden-router/reglas/maquina-de-estados.yaml` e procure a seção `completa`. Ali está o que se
espera de cada canal e por quanto tempo se espera:

| Canal | `llega_a` | `plazo_s` | Por quê |
|---|---|---|---|
| `rest` (a API) | `aceptada` | 10 | O «sim» dela chega na mesma chamada |
| `bd` (o ERP) | `aceptada` | 10 | Igual |
| `edi` (o parceiro) | `confirmada` | 20 | O «sim» dele é o 997, e chega depois (lição 21) |

Agora abra `mio/orden-router/juntar.camel.yaml`. No alto há um desenho do pedido 4001 a meio caminho: a API e o ERP já
chegaram, o parceiro ainda não. E há uma rota, `vigilar`, já feita: um **relógio** (`timer`) que a cada 2 segundos lê
as regras e chama duas rotas:

| Rota | O que faz | Onde anota |
|---|---|---|
| `juntar` | Dá por COMPLETO cada evento cujos canais chegaram todos ao próprio `llega_a` | Tabela `completitud`, uma linha por pedido e evento |
| `vencidos` | Alerta sobre cada canal que passou do prazo sem chegar | Tabela `alerta` e um `WARN` no log |

Por que um relógio, em vez de esperar que algo avise? Porque **o que não chega não avisa**. Um 997 que nunca vem não
dispara evento nenhum. Alguém tem de olhar o relógio.

Repare no que não há: não há um agregador que guarde na memória do router quais canais já responderam. Cada
operação e o estado dela vivem na tabela `operacion` desde a lição 15. **Reunir é perguntar à base.**

## 2. Faça a previsão

Responda em um papel:

1. A API e o ERP aceitaram o pedido 4001, e o 850 ficou depositado. O parceiro não mandou o 997. O pedido está
   completo?
2. O pedido 3001 é de uma loja própria: não leva EDI (lição 11). Quando está completo?
3. Passam 20 segundos e o 997 do pedido 4002 não chega. O que é melhor: dar o pedido por completo para que pare de
   atrapalhar, ou alertar e deixá-lo aberto? O que acontece em cada caso se o 997 chegar aos 30 segundos?
4. O router é reiniciado enquanto o 4002 espera o 997. Perde-se o que já estava reunido?

Veja como está hoje:

```bash
./verifica inicio
```

Sai errado desde a primeira marca: o 4001 fica COMPLETO **antes** do 997, porque a versão de `inicio/` dá por
completo um evento quando nenhum canal continua `pendiente`, e um 850 depositado está `aceptada`. E o 4002, ao vencer,
é fechado à força: fica completo sem que o parceiro tenha respondido nada.

## 3. Reúna e vigie

Em `juntar.camel.yaml` há dois TODO, um em cada rota. Os dois são uma consulta SQL que já está quase inteira.

### TODO 1: completo é «cada canal chegou ao que lhe cabe»

A consulta de `juntar` junta cada operação com a regra do canal dela e agrupa por pedido e evento:

```sql
INSERT INTO completitud (orden, momento)
SELECT p.orden, p.momento
FROM operacion p JOIN jsonb_each(CAST(:#${variable.reglas} AS jsonb)) AS r(canal, regla) ON r.canal = p.canal
WHERE NOT EXISTS (SELECT 1 FROM completitud c WHERE c.orden = p.orden AND c.momento = p.momento)
GROUP BY p.orden, p.momento
HAVING bool_and(p.estado NOT IN ('pendiente', 'estacionada'))     -- ← esta condición
ON CONFLICT DO NOTHING
RETURNING orden, momento
```

Lê-se assim:

| Peça | O que faz |
|---|---|
| `jsonb_each(…) AS r(canal, regla)` | Converte as regras em linhas: uma por canal, com o `llega_a` e o `plazo_s` de cada um |
| `JOIN … ON r.canal = p.canal` | Cola em cada operação a regra do canal dela |
| `GROUP BY p.orden, p.momento` | Reúne as operações de cada evento de cada pedido |
| `HAVING bool_and(…)` | O evento passa só se a condição for verdadeira **para todas** as operações dele |
| `ON CONFLICT DO NOTHING` | Um evento se completa uma única vez |

O trabalho é a condição do `HAVING`. Hoje ela diz «nenhuma continua pendente nem estacionada». Tem de dizer **«cada
uma está em um estado do próprio `llega_a`»**:

```sql
HAVING bool_and(jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado)))
```

`r.regla -> 'llega_a'` é a lista da regra, por exemplo `["confirmada"]`; `to_jsonb(p.estado)` é o estado da
operação como valor JSON; `jsonb_contains` pergunta se o segundo está no primeiro.

Repare em algo que sai de graça: **o 3001 não tem operação `edi`**, porque é de uma loja própria. Como não tem
linha, não conta: ninguém espera um 997 que não lhe cabe.

### TODO 2: vencido alerta e não fecha

A consulta de `vencidos` hoje **escreve em `completitud`**: fecha o evento ao vencer. Mude-a para que escreva um
alerta em `alerta`, uma vez por canal:

| Mudança | O que escrever |
|---|---|
| Para onde | `INSERT INTO alerta (orden, momento, canal, tipo, estado, espera)` |
| O quê | `SELECT p.orden, p.momento, p.canal, 'vencido', p.estado, r.regla ->> 'llega_a'` (sem o `DISTINCT`) |
| Quais | Acrescente ao `WHERE`: `NOT jsonb_contains(r.regla -> 'llega_a', to_jsonb(p.estado))`, para alertar só sobre os canais que **não** chegaram |
| Uma vez | Deixe o `ON CONFLICT DO NOTHING`: a tabela `alerta` tem uma `UNIQUE (orden, momento, canal, tipo)` |
| Para o log | Acrescente `RETURNING orden, momento, canal, estado, espera` e `outputType: SelectList` |

E depois da consulta, um `split` sobre `${body}` com um `log` em `WARN` para cada alerta novo, como o `split` de
`juntar`. A mensagem começa com `VENCIDO orden ${body[orden]} momento ${body[momento]} canal ${body[canal]}`.

## 4. Quebre um de propósito

Com tudo concluído, tire do `WHERE` de `vencidos` a condição `NOT jsonb_contains(…)`. Rode `./verifica`.

Antes, faça a previsão: o 4002 espera o 997 por mais de 20 segundos. A API e o ERP o aceitaram em poucos segundos. Quantos
alertas são esperados?

Três em vez de um. Sem essa condição, o relógio alerta sobre **todo** canal que tenha passado do prazo, tenha chegado ou não:
a API e o ERP, que já aceitaram, também saem como vencidos. Sai em vermelho a marca do alerta do 4002. Um alerta
que dispara por algo que está bem ensina as pessoas a ignorar os alertas, e então o que importa se perde. Devolva a
condição.

## 5. Confira

```bash
./verifica
```

O `verifica` manda um pedido de loja própria e um de franquia, espera o 997; depois desliga o sistema do parceiro e
manda o 4002, espera que vença, **reinicia o router** e volta a ligar o parceiro. Está concluída quando aparecem as
sete marcas verdes:

```
✅ Arrancaron el router (con su reloj que junta y vigila), los tres canales y el sondeo
✅ La 4001: API y ERP aceptada y el 850 depositado, pero sin 997 todavía no está COMPLETA
✅ La 3001 (tienda propia): COMPLETA con la API y el ERP; el socio no le toca y no se le espera
✅ La 4001: llegó el 997, el socio quedó confirmada y la orden COMPLETA
✅ La 4002 sin 997 pasó su plazo: un solo aviso (vencido, en «aceptada», se espera confirmada) y sigue abierta
✅ Log: el aviso del vencido está en el log del router
✅ Tras reiniciar el router, el 997 tardío confirmó al socio y la 4002 quedó COMPLETA, con su único aviso como historia
```

Leia a última com calma. O router foi reiniciado **no meio da espera**, e o 4002 se completou do mesmo jeito. Nada foi escrito
para isso: o que estava sendo reunido nunca viveu na memória do router, vivia na base. Ao voltar, o relógio
perguntou outra vez e encontrou o mesmo, mais o 997 que acabava de chegar.

Veja as duas tabelas:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT * FROM completitud ORDER BY completa" -c "SELECT orden, canal, estado, espera, creada FROM alerta"
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/juntar.camel.yaml solucion/orden-router/juntar.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `La 4001 con el socio en «aceptada» ya quedó COMPLETA` | O `HAVING` continua com a condição de `inicio/`: troque-a pelo `jsonb_contains` |
| `La 3001 (tienda propia) no quedó COMPLETA` | O `JOIN` com as regras não está lá, ou `bool_and` foi trocado por outra coisa |
| `La 4002 sin 997: hay 0 avisos` e `completa=1` | `vencidos` continua escrevendo em `completitud`: é em `alerta` |
| `La 4002 sin 997: hay 3 avisos` | Falta o `NOT jsonb_contains(…)` no `WHERE` de `vencidos` |
| `no está el WARN del vencido de la 4002` | Falta o `RETURNING` com `outputType: SelectList`, ou o `split` com o `log` em `WARN` |
| `No está arriba socio-bot` | Suba o ambiente: `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Espalhar é fácil; reunir é onde está o trabalho.** Mandar algo para vários destinos qualquer multicast faz.
Saber quando todos terminaram, cada um com a própria ideia de «terminei», é o que diz ao negócio que o pedido já
está em toda parte.

**O que não chega não avisa.** Um sistema que só reage a eventos nunca fica sabendo do que falta. É preciso algo
que olhe o relógio e pergunte.

**Alertar não é fechar.** Fechar à força ao vencer diz «terminou» de algo que não terminou, e se o que faltava chegar
tarde já não muda nada. Alertar deixa que uma pessoa decida, e deixa que o que chega tarde ainda conte.

**Se o estado vive fora do processo, o reinício não é um problema.** Dissemos isso na lição 15 como teoria; hoje deu para ver:
o router foi reiniciado no meio da espera e não perdeu nada, sem uma linha extra de código.

## Exercício: a ceia de fim de ano

1. Antes de rodar qualquer coisa, escreva em papel a previsão para a pergunta 4 acima: perde-se o que foi reunido ao
   reiniciar? Depois de rodar `./verifica`, compare-a com o que aconteceu e escreva onde vivia o que foi reunido.
2. Mude nas regras o `plazo_s` do parceiro para `5` e rode `./verifica`. Que marcas mudam, e por quê? O que
   aconteceria com as pessoas que atendem os alertas se o prazo fosse tão curto na vida real? Devolva-o a `20`.
3. Imagine que o router tivesse três réplicas, cada uma com o próprio relógio. Um pedido seria completado três vezes? Um vencido geraria
   três alertas? (Dica: procure as duas `UNIQUE` e o `ON CONFLICT`.)

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Reunir é perguntar à base, e um vencido alerta sem fechar](../decisiones/29-juntar-es-preguntarle-a-la-base.md)

## Revisão

1. O que é um Scatter-Gather? Que parte do curso espalha e que parte reúne?
2. O que cada canal espera para se dar por terminado, e onde isso está escrito?
3. Por que é preciso um relógio para reunir?
4. Por que o 3001 se completa sem 997?
5. **A pergunta de encerramento:** ao vencer o prazo do parceiro, o router alerta e deixa o pedido aberto. Por que fechá-lo
   à força seria pior? Use o 997 tardio do 4002 na resposta.

**A seguir:** na lição 26 um cliente diz que o pedido dele não chegou. Há a base, o log e os destinos. O que
consultar primeiro, e como reconstruir a viagem completa de um pedido?
