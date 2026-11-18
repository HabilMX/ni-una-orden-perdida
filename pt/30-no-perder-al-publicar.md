# Lição 30 — Não perder ao publicar

> **O que será alcançado:** fechar a última brecha do curso. Se o Kafka está fora do ar justo quando chega um pedido, o
> pedido é guardado, a loja recebe o `202` e, quando o Kafka volta, o pedido sai sozinho para os três canais dele, sem que
> ninguém faça um rerun.

**Antes de começar é preciso ter:** as lições 17 e 29 concluídas, o ambiente da 00 ligado e o Kafka no ar
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## A novidade de hoje, em uma frase

Desde a lição 28 o router faz duas coisas seguidas: **guarda** o pedido na base de estado e **depois** publica o
evento no Kafka. São dois sistemas diferentes, e nenhuma transação abrange os dois. Se o Kafka está fora do ar nesse
instante, o pedido ficou guardado e a loja já tem o `202`, mas o evento não saiu, e ninguém volta a
tentar. Ao contrário também não serve: publicar antes de guardar deixa um evento de um pedido que, se a base falhar, não
existe. Hoje o router guarda **o que é preciso publicar** na mesma transação do pedido, em uma tabela chamada
**outbox**, e uma sondagem o publica quando o Kafka pode recebê-lo. O padrão se chama *Transactional Outbox*.

**A imagem:** a bandeja de «a enviar» de um escritório. Quem atende o cliente não sai correndo para a transportadora
com cada envelope: preenche o formulário e, **no mesmo instante**, deixa o envelope na bandeja. O mensageiro passa de tempos em
tempos e leva o que houver. Se um dia a transportadora está fechada, os envelopes ficam na bandeja, não no
bolso de alguém, e saem quando ela abrir.

```
antes:  guardar (transacción) → 202 → publicar en Kafka           ← si Kafka está caído aquí, se pierde
hoy:    guardar orden + operaciones + outbox (UNA transacción) → 202
        cada 1 s: outbox pendiente → publicar → ¿Kafka lo recibió? sí → «publicado» · no → espera la siguiente vuelta
```

---

## 1. Prepare a pasta

```bash
cd lecciones/30-no-perder-al-publicar
cp -R inicio mio
```

Abra `infra/postgres/01-estado.sql` e procure `CREATE TABLE outbox`: cada linha aponta para uma `operacion` e tem uma
coluna `publicado`, vazia até que o Kafka tenha recebido o evento.

A sondagem já está feita: é a rota `sondeo-outbox` de `mio/orden-router/reparto.camel.yaml`. Leia-a. A cada segundo
ela pega o que está pendente **na ordem em que entrou** e, se algo não sai, o que vem depois espera a volta seguinte
(`stopOnException`): assim o que é de um mesmo pedido não sai fora de ordem. A varredura da lição 17 já deixa no outbox o que
libera.

O trabalho fica em dois arquivos:

| TODO | Arquivo | O que fazer |
|---|---|---|
| 1 | `mio/orden-router/estado.camel.yaml` | Ao guardar um pedido novo, deixar no outbox uma linha para cada operação (evento do ciclo de vida `creada`) |
| 2 | O mesmo | Ao guardar um evento novo (embarque, entrega), o mesmo para as operações que nasceram `pendiente` |
| 3 | `mio/orden-router/orden-router.camel.yaml` | Tirar o envio direto a `seda:repartir`: a partir de hoje, só o outbox publica |

## 2. Faça a previsão

Responda em um papel:

1. Com a versão da lição 29, o Kafka está fora do ar e chegam o 4002 e o 4003. A loja recebe o `202`? Quantas linhas
   há no outbox? O que acontece com os pedidos quando o Kafka volta?
2. Por que a linha do outbox tem de ser escrita na **mesma** transação da operação, e não logo
   depois?
3. Se o envio direto ficar e além dele houver o outbox, o que acontece com cada evento? A API o criaria duas vezes?

Veja como está hoje:

```bash
./verifica inicio
```

Com o Kafka fora do ar, o router guarda os dois pedidos e responde `202`, mas o outbox fica **vazio**. Quando o Kafka
volta, as 6 operações continuam pendentes: ninguém volta a mandá-las.

## 3. A bandeja de «a enviar»

### TODO 1: o pedido novo

Em `mio/orden-router/estado.camel.yaml` procure o TODO 1. Está dentro da transação que guarda o pedido e as
operações dele. Ali mesmo, um `INSERT … SELECT` deixa uma linha para cada operação que acaba de ser guardada:

```yaml
- to:
    uri: "sql:INSERT INTO outbox (operacion) SELECT id FROM operacion WHERE orden = :#${variable.orden} AND momento = 'creada' ORDER BY id"
    parameters:
      dataSource: "#estado"
```

O `ORDER BY id` faz as linhas entrarem na ordem em que as operações nasceram, e a sondagem publica nessa
ordem.

### TODO 2: um evento novo

Mais abaixo, na mesma transação que guarda o embarque ou a entrega, o TODO 2. É o mesmo `INSERT`, com duas
diferenças:

| | TODO 1 | TODO 2 |
|---|---|---|
| Que evento | `'creada'` | `:#${variable.momento}` |
| Que operações | Todas | Só as que nasceram `pendiente`: `AND estado = 'pendiente'` |

Por que só as pendentes? Porque as `estacionada` (lição 17) ainda não podem sair. Essas quem deixa no outbox é a
varredura, quando as libera.

### TODO 3: um único caminho

Em `mio/orden-router/orden-router.camel.yaml`, na rota `contestar-y-repartir`, tire o passo `to: seda:repartir`.
Se ele ficar, cada evento sai por **dois** caminhos: o direto e o outbox. A API o reconheceria pela chave (lição 18),
mas cada canal receberia dois eventos e o router anotaria duas tentativas. Um único caminho é mais fácil de explicar e de
rastrear.

## 4. Quebre um de propósito

Com a versão própria concluída, apague o `INSERT` do TODO 1 e rode `./verifica`.

Antes, faça a previsão: o Kafka está no ar no começo. O 4001 chega?

Não. O `verifica` para na segunda marca: o 4001 não chega a nenhum dos canais dele. O envio
direto já foi tirado (TODO 3), então o outbox é **o único caminho**, e um pedido que não deixa a própria linha ninguém publica. Antes
do outbox, «o Kafka está no ar» bastava; agora o que manda é a linha guardada. Devolva-o.

## 5. Confira

```bash
./verifica
```

O `verifica` manda o 4001 com tudo no ar, **para o Kafka**, manda o 4002 e o 4003, confere o outbox e a API
durante alguns segundos, volta a subir o Kafka e espera que tudo saia. Está concluída quando aparecem as oito marcas
verdes:

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Con Kafka caído, el router guardó la 4002 y la 4003 y contestó 202 a las dos
✅ En el outbox esperan 6 filas sin publicar, una por operación de la 4002 y la 4003
✅ Control: mientras Kafka está caído, la API no tiene ninguna de las dos
✅ Kafka volvió y lo guardado salió solo: la API, el ERP y el socio tienen la 4002 y la 4003
✅ En la API se creó cada una UNA vez (2 creaciones para 2 órdenes)
✅ El outbox quedó sin pendientes: todo lo que se guardó, salió
```

A quinta é um **controle**: se com o Kafka fora do ar a API já tivesse os pedidos, a sexta marca não provaria que o
outbox os tirou de lá.

Veja o outbox à mão, com os serviços no ar:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT x.id, p.orden, p.canal, x.publicado FROM outbox x JOIN operacion p ON p.id = x.operacion ORDER BY x.id"
```

**Não roda?** `./verifica solucion` testa a nossa, e `diff -r mio solucion` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `A la base de estado le falta la tabla outbox` | A base é de antes da lição 30: recrie-a com o comando que a mensagem traz (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` | Falta o TODO 1: com o TODO 3 feito, sem linha no outbox não se publica nada |
| `En el outbox esperaba 6 filas sin publicar … y hay 0` | O `INSERT` do TODO 1 não roda: confira que esteja dentro da transação, depois de guardar as operações |
| `Kafka volvió y lo guardado no salió` | Veja que canal ficou em que estado (a mensagem diz) e confira a rota `sondeo-outbox` no log do router |
| `En la API hubo … creaciones para 2 órdenes` | A API criou algum duas vezes: a chave que o int-rest manda para ela foi alterada? (lição 18) |
| `El outbox tiene … filas sin publicar` | Algo ficou travado: confira o log do router, embaixo de `sondeo-outbox` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Dois sistemas não compartilham uma transação.** A base e o Kafka confirmam cada um o que é dele. Se uma ação tem de
ficar nos dois, guarda-se em um só (a base, com o pedido) e o outro é alimentado a partir dali.

**O que foi guardado é o que sai.** A sondagem publica o que o outbox diz, lendo a base. Se o router é reiniciado
na metade, a linha continua ali.

**Publicar a mais se tolera; publicar a menos, não.** Se o router cai na metade do envio, ou depois de
publicar e antes de marcar `publicado`, a linha sai outra vez. Por isso quem lê tem de reconhecer repetidos (lições 18 e 29). Perder um evento, ao contrário,
ninguém nota até que um cliente reclame.

**Um único caminho.** Manter o envio direto «por via das dúvidas» junto ao outbox duplica tudo e não conserta nada: o
outbox já cobre o caso em que o Kafka está no ar.

## Exercício: a bandeja

1. Pare o Kafka com os serviços no ar, mande um pedido e rode a consulta do passo 5. O que diz a coluna
   `publicado`? Suba o Kafka e rode-a de novo.
2. Em papel: o router guardou o pedido e a linha dele no outbox, a sondagem a publicou e o router caiu antes de marcar
   `publicado`. O que acontece na volta? Que peça evita que a API o crie duas vezes?
3. Faça uma lista das formas de perder um pedido vistas no curso (a loja que não recebe o `202`,
   o ERP fora do ar, o 997 que não chega, o evento confirmado sem desfecho…). Ao lado de cada uma, a lição que a
   fecha.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O que é preciso publicar é guardado na mesma transação (outbox), e uma sondagem o publica](../decisiones/34-el-outbox-publica-lo-guardado.md)

## Revisão

1. Que brecha fica entre guardar e publicar, e por que ela não se resolve publicando primeiro?
2. O que a tabela `outbox` guarda, e em que transação ela é escrita?
3. Por que o TODO 2 deixa de fora as operações `estacionada`?
4. O que aconteceria se o envio direto ficasse junto ao outbox?
5. **A pergunta de encerramento:** uma loja manda um pedido com o Kafka fora do ar. Conte, passo a passo, o que acontece desde o
   `202` até que o parceiro tenha o 850 dele, e em que tabelas fica a trilha.

**A seguir:** com esta lição termina o curso em Docker Compose. Se a equipe trabalha com Kubernetes ou quer
ver a integração sem contêineres, o apêndice (lições 31 e 32) é de leitura e é opcional.

---

> **O que a Hábil faz**
>
> Ao concluir este curso:
> - um aviso repetido não duplica nada;
> - se não se sabe se algo chegou, fica pendente de conciliar em vez de ser dado por falho;
> - o que foi depositado não é dado por confirmado até que o parceiro confirme o recebimento;
> - um canal fora do ar não freia os demais;
> - cada destino recebe só as chamadas que aguenta;
> - cada pedido pode ser acompanhado sem mostrar dados pessoais.
>
> Em uma empresa, isto costuma soar assim: «Não sabemos se essa operação chegou. Tenta-se de novo à mão, e às vezes
> duplica». E há falhas que não marcam erro: o envio saiu, mas a resposta nunca chegou.
>
> Na Hábil construímos sob medida a integração entre a loja, o ERP, os parceiros e os canais da empresa.
> **[Hábil MDW](https://www.habil.mx/es/habil-mdw/?utm_source=github&utm_medium=referral&utm_campaign=2026-11_ni-una-orden-perdida_es)**
> foi projetada para operar 24/7, com trilha de auditoria de cada operação: o que foi mandado a cada sistema, o que ele respondeu, a que horas
> e, se falhou, por quê. Cada envio fica com a resposta ou com o motivo da falha, e as novas tentativas são projetadas
> para não repetir o efeito. Um envio que saiu errado é tentado de novo pela equipe de operação da empresa como um envio novo, com
> trilha de quem o fez e sem mexer no banco de dados à mão.
>
> **[Peça a primeira conversa, sem custo.](https://wa.me/525667541323?text=Hola%2C%20vengo%20de%20GitHub%20%28Ni%20una%20orden%20perdida%29.%20Me%20interesa%20una%20primera%20conversaci%C3%B3n.%0AIndustria%3A%0APuesto%3A%0ASistemas%20principales%3A)**
