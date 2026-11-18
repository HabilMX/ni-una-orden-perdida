# Lição 28 — Um tópico, um grupo por integração

> **O que será alcançado:** que o router deixe de chamar cada canal e de esperar a resposta dele. Em vez disso, publica cada
> evento do ciclo de vida do pedido em um **tópico** do Kafka, e cada integração o lê no próprio ritmo. Se o parceiro fica parado uma
> hora, a API e o ERP continuam, e ao voltar o parceiro fica em dia sozinho: ninguém lhe reenvia nada.

**Antes de começar é preciso ter:** as lições 12, 17 e 27 concluídas e o ambiente da 00 ligado. E o Kafka, que
a partir de hoje é ligado à parte (uma vez):

```bash
docker compose -f infra/compose.yaml --profile kafka up -d --wait
```

## A novidade de hoje, em uma frase

Até a lição 27, o router fala com cada canal por HTTP. Se o int-edi está parado, o router tenta de novo, abre o
disjuntor e no fim deixa a operação em conciliação: alguém tem de fazer um rerun à mão quando o int-edi
volta. O router carrega o peso de cada canal estar no ar. Hoje isso muda: o router **publica** um evento por evento do ciclo de vida
no tópico `ordenes`, e cada integração o **lê** quando pode. Duas ideias fazem isso funcionar: a **chave** do
evento e o **grupo** de cada integração.

**A imagem:** o jornal do bairro. O entregador deixa um exemplar em cada casa, não um para a rua inteira.
Se em uma casa saíram de férias, os jornais se acumulam na porta e, na volta, são lidos em ordem; as
outras casas não esperaram ninguém. E em uma casa com três irmãos, chega **um** jornal: lê quem o pegar
primeiro, não os três.

| Na imagem | No curso |
|---|---|
| O jornal de cada dia | Um evento: um evento do ciclo de vida de um pedido |
| Cada casa | Um grupo: `int-rest`, `int-bd`, `int-edi` |
| Os irmãos de uma casa | As 3 réplicas do int-bd, que compartilham o grupo |
| Os jornais acumulados na porta | O que o grupo ainda não leu: o Kafka guarda para ele |

---

## 1. Prepare a pasta

```bash
cd lecciones/28-un-topico-un-grupo-por-integracion
cp -R inicio mio
```

A partir de hoje há peças novas. Leia-as antes de escrever:

| Arquivo | O que faz | É para escrever? |
|---|---|---|
| `orden-router/reparto.camel.yaml` | Já não chama os canais: publica um evento no tópico `ordenes` | **Sim, o TODO 1** |
| `int-rest/kafka.camel.yaml`, `int-bd/…`, `int-edi/…` | A **ponte** de cada integração: lê o tópico e entrega o evento à rota de sempre (`/integrar`, `/embarque`, `/entrega`) | **Sim, o TODO 2** |
| `orden-router/desenlaces.camel.yaml` | O que cada canal respondeu volta por outro tópico, `desenlaces`, e só o router o lê | Não, só leia |

O que cada canal faz com o destino dele **não mudou**: só a forma como o trabalho chega. O router continua sendo o único
que escreve na base de estado, e continua decidindo a que canais cabe cada pedido (lição 11): o evento leva a
operação e a tentativa de cada canal a que ele cabe, e uma ponte que não encontra as dela o deixa passar.

Duas coisas que se notam a partir de hoje:

- **O disjuntor do router (lição 22) já não está.** O trabalho dele era não insistir com um canal fora do ar; agora o router
  não fala com o canal, então não há com quem insistir. O Kafka guarda o evento até que o canal o leia.
- **O rerun (lição 23) responde `202` «enviado»** assim que o Kafka recebeu o evento, não quando o canal terminou.

Abra `infra/compose.yaml` e procure `KAFKA_NUM_PARTITIONS: 3`, e no `compose.yaml` da lição, `replicas: 3`.

## 2. Faça a previsão

Responda em um papel:

1. As três integrações leem com o **mesmo** grupo, `integraciones`. Quantas delas recebem o pedido 4001?
2. Se o int-edi está parado e chegam o 4002 e o 4003, o que acontece com a API e o ERP? E o que acontece com os 850 do
   parceiro quando o int-edi volta?
3. O tópico tem 3 partições e o int-bd roda em 3 réplicas. Se um evento **não leva chave**, os eventos do
   mesmo pedido podem cair em partições diferentes? Por que isso importaria?

Veja como está hoje:

```bash
./verifica inicio
```

A primeira marca de entrega sai em vermelho: o 4001 chega a **um** dos três canais dele, e os outros dois ficam em
`enviada`. Com um único grupo, o Kafka trata o int-rest, o int-bd e o int-edi como três réplicas da mesma coisa, e dá cada
evento a uma só. E a última marca também: nenhum evento leva o pedido como chave.

## 3. Ponha a chave e separe os grupos

### TODO 1: a chave do evento

Em `mio/orden-router/reparto.camel.yaml`, procure o TODO 1, logo antes do `to: kafka:ordenes`. O componente kafka
do Camel toma a chave do cabeçalho `CamelKafkaKey`. O pedido já está na variável `orden`:

```yaml
- setHeader:
    name: CamelKafkaKey
    expression:
      simple:
        expression: "${variable.orden}"
```

O Kafka manda para a **mesma partição** tudo o que leva a mesma chave, e dentro de uma partição a ordem é
respeitada. Assim, o creada, o embarcada e o entregada do 4001 são lidos na ordem em que foram publicados. Uma
partição é lida por uma única réplica de cada grupo por vez: se o grupo é rebalanceado (uma réplica cai ou chega outra),
outra réplica pode continuá-la, mas na mesma ordem. Sem chave, cada evento cai em qualquer lugar, e duas réplicas diferentes poderiam atender dois eventos
do mesmo pedido, cada uma no próprio ritmo.

### TODO 2: um grupo por integração

Nos três `kafka.camel.yaml`, troque `groupId: integraciones` pelo nome da integração:

| Arquivo | `groupId` |
|---|---|
| `mio/int-rest/kafka.camel.yaml` | `int-rest` |
| `mio/int-bd/kafka.camel.yaml` | `int-bd` |
| `mio/int-edi/kafka.camel.yaml` | `int-edi` |

Com isso se têm as duas formas de distribuir que o Kafka oferece, ao mesmo tempo:

| | Entre grupos diferentes | Entre réplicas do mesmo grupo |
|---|---|---|
| Quem recebe um evento | **Todos** os grupos, cada um a própria cópia | **Uma** única réplica |
| O padrão se chama | *Publish-Subscribe* | *Competing Consumers* |
| No curso | A API, o ERP e o parceiro recebem cada pedido | As 3 partições são atribuídas às 3 réplicas do int-bd |

Cada grupo mantém **a própria conta** de até onde leu: o *offset*. Por isso um grupo parado não para os
demais, e ao voltar continua de onde ficou.

## 4. Quebre um de propósito

Com a versão própria concluída, tire o `setHeader` de `CamelKafkaKey` e rode `./verifica`.

Antes, faça a previsão: as três integrações têm cada uma o próprio grupo. Os pedidos chegam?

Chegam, e quase todas as marcas saem verdes. Só sai em vermelho a última: os eventos já não levam o pedido como
chave. Com um pedido por vez não se nota nada, e por isso este erro é traiçoeiro: aparece no dia em que dois
eventos do mesmo pedido chegam juntos e duas réplicas diferentes do int-bd os atendem. A máquina de estados da lição
17 estaciona o que chega antes da hora, mas a chave evita que isso aconteça. **Funcionar hoje não prova que a ordem
esteja garantida.** Devolva a chave.

## 5. Confira

```bash
./verifica
```

O `verifica` recria o Kafka vazio, manda o 4001 com tudo no ar, **para o int-edi**, manda o 4002 e o 4003, confere
que a API e o ERP continuaram e que o parceiro não tem os 850 dele, volta a ligar o int-edi e espera que fique em
dia. No fim lê o tópico inteiro com um leitor à parte para conferir a chave de cada evento. Está concluída quando
aparecem as sete marcas verdes:

```
✅ Arrancaron el router (que publica en Kafka), los tres canales con su puente y el sondeo
✅ La 4001 llegó por Kafka a sus tres canales: la API, el ERP y el socio
✅ Con int-edi detenido, la API y el ERP recibieron la 4002 y la 4003 (API 2 de 2, ERP 2 de 2)
✅ El socio todavía no tiene sus 850, y Kafka se los guarda: el grupo int-edi va 2 eventos atrás
✅ int-edi volvió y se puso al día solo: 2 archivos 850 en el socio, y el router no reenvió nada (2 intentos para 2 operaciones)
✅ Los tres grupos (int-rest, int-bd, int-edi) quedaron al día: 0 eventos por leer
✅ Los 3 eventos del tópico ordenes llevan la orden como llave
```

Leia a quinta: «2 intentos para 2 operaciones». Até a lição 27, um canal fora do ar terminava em conciliação e em um rerun à
mão. Hoje ninguém fez nada: o int-edi leu o que o Kafka guardou para ele.

Veja à mão quanto cada grupo já leu, com os serviços no ar:

```bash
docker compose -f ../../infra/compose.yaml exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --all-groups
```

A coluna `LAG` é o que falta cada grupo ler em cada partição.

**Não roda?** `./verifica solucion` testa a nossa, e `diff -r mio solucion` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `Kafka no levantó` | Ligue-o à mão com o comando de «Antes de começar» e tente de novo |
| `A la base de estado le faltan tablas de lecciones anteriores` | A base é de antes: recrie-a com o comando que a mensagem traz (`--force-recreate -V estado`) |
| `La 4001 no llegó a sus tres canales` com algum em `enviada` | Esse canal não tem o próprio grupo: confira o `groupId` do `kafka.camel.yaml` dele |
| `Con int-edi detenido, la API y el ERP debían seguir` | Confira o `groupId` do int-rest e do int-bd: se compartilham grupo com outro, tiram os eventos um do outro |
| `el grupo int-edi va -1 atrás` | O grupo `int-edi` não existe: o int-edi lê com outro nome de grupo |
| `De … eventos del tópico ordenes, … llevan la orden como llave` | Falta o TODO 1, ou o `setHeader` ficou depois do `to: kafka:ordenes` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Publicar não é chamar.** Quem chama precisa que o outro esteja no ar; quem publica só precisa que o tópico
esteja no ar. O router deixou de carregar a disponibilidade de cada canal, e é isso que se chama **desacoplar**.

**O grupo decide quem recebe.** O mesmo tópico serve para distribuir trabalho entre réplicas (um grupo) e para avisar
vários sistemas (vários grupos). Errar o grupo é o erro mais comum ao começar com o Kafka, e não dá erro: os
eventos simplesmente chegam a outro.

**A chave decide a ordem.** O Kafka não promete ordem no tópico inteiro, só dentro de uma partição. Se algo tem
de ser lido em ordem, tem de levar a mesma chave.

**O que não foi lido fica guardado.** Um consumidor parado não perde nada enquanto o Kafka conservar o evento: ao
voltar, continua a partir do offset dele. Isso muda o que significa «o parceiro ficou fora do ar uma hora».

## Exercício: o jornal do bairro

1. Com os serviços no ar, pare o int-bd (`DONDE=mio docker compose -f compose.yaml stop int-bd`), mande um pedido
   e rode o comando de `kafka-consumer-groups.sh`. Que grupo tem `LAG`? Ligue-o e rode de novo.
2. Em papel: se amanhã o parceiro pedir para receber também os avisos de entrega, e alguém de finanças quiser um sistema
   novo que leia **todos** os pedidos, quantos grupos há? O que muda no router para o sistema novo?
3. As 3 partições são atribuídas às 3 réplicas do int-bd. O que aconteceria se rodassem 5 réplicas? (Dica: uma partição é
   lida por uma única réplica de cada grupo.)

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Um evento por evento do ciclo de vida em um tópico, com o pedido como chave, e um grupo de consumidores por integração](../decisiones/32-un-topico-un-grupo-por-integracion.md)

## Revisão

1. O que é um tópico, e o que muda para o router ao publicar em vez de chamar?
2. O que faz a chave de um evento? Por que a chave é o pedido?
3. Que diferença há entre duas réplicas do mesmo grupo e dois grupos diferentes?
4. O que é o offset, e por que um grupo parado não para os demais?
5. **A pergunta de encerramento:** o parceiro ficou parado uma hora. Explique, passo a passo, por que hoje não foi preciso um
   rerun e até a lição 27 foi.

**A seguir:** na lição 29 o problema passa para o outro lado: o que acontece se o int-bd lê um evento e não chega a
deixar o desfecho? O Kafka dá o evento por lido?
