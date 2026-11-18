# Lição 29 — Não perder ao consumir

> **O que será alcançado:** que um evento que uma integração leu mas não terminou (entregou o pedido, mas não conseguiu
> deixar o desfecho) **seja lido de novo**, em vez de ser dado por lido. E que lê-lo duas vezes não crie o pedido duas
> vezes na API.

**Antes de começar é preciso ter:** as lições 18 e 28 concluídas, o ambiente da 00 ligado e o Kafka no ar
(`docker compose -f infra/compose.yaml --profile kafka up -d --wait`).

## A novidade de hoje, em uma frase

Na lição 28 cada grupo mantém a conta de até onde leu: o **offset**. Até hoje essa conta é confirmada pelo Kafka sozinho
(`autoCommitEnable`, que vem ligado), com o que o consumidor já leu, tenha terminado ou não. E quando um evento
falha na metade, o Camel o anota no log e segue com o seguinte: a conta avança **por cima** do que falhou.
A API já tem o pedido, mas o router nunca fica sabendo como foi, e ninguém volta a ler esse evento. Hoje o
offset é confirmado **à mão**, e só quando o trabalho terminou.

**A imagem:** o marcador de página de um livro. Se ele é avançado a cada página virada, mesmo que o sono tenha vencido
na metade do capítulo, amanhã se começa no capítulo seguinte e o que não foi lido já não se lê. Se ele só é movido
quando o capítulo **terminou**, o pior que acontece é reler algumas páginas.

```
leer el evento → entregarlo al canal → publicar el desenlace → confirmar el offset
                                                                ↑ si algo falla antes de aquí, se vuelve a leer
```

---

## 1. Prepare a pasta

```bash
cd lecciones/29-no-perder-al-consumir
cp -R inicio mio
```

O trabalho fica nas três pontes: `mio/int-rest/kafka.camel.yaml`, `mio/int-bd/kafka.camel.yaml` e
`mio/int-edi/kafka.camel.yaml`. As três mudam do mesmo jeito.

Antes, abra `mio/orden-router/desenlaces.camel.yaml`: o router **já** lê o tópico `desenlaces` assim. Serve de
modelo.

## 2. Faça a previsão

Responda em um papel:

1. A API recebeu o pedido 2001 e respondeu `201` ao int-rest, mas o Kafka não aceitou o desfecho que o int-rest quis
   publicar. Com o commit automático de offset (auto-commit), o router fica sabendo que o 2001 chegou? Alguém volta a ler esse
   evento?
2. Se o offset é confirmado só no fim, e o int-rest é morto depois de entregar mas antes de confirmar, o que acontece na
   volta? O 2001 é criado duas vezes na API? (Dica: a lição 18.)
3. Se um evento não pode ser terminado nunca, o que acontece com os que vêm atrás dele na partição?

Veja como está hoje:

```bash
./verifica inicio
```

O `verifica` arma uma armadilha para o Kafka: faz com que o tópico `desenlaces` não aceite nada. Manda três pedidos, vê que a
API e o ERP os recebem e que o router não fica sabendo de nenhum. Depois tira a armadilha. Com o commit
automático, o router fica sem saber de **nenhuma** das 6 operações: os eventos já estavam confirmados.

## 3. Confirme quando terminar

Em cada ponte há três TODO.

### TODO 1: as três opções do consumidor

No `from: kafka:ordenes`, junto a `groupId`:

```yaml
autoCommitEnable: false      # Kafka ya no confirma solo
allowManualCommit: true      # el puente confirma cada evento cuando terminó con él
breakOnFirstError: true      # si un evento falla, se vuelve a leer ése, no se sigue con el siguiente
```

As três andam juntas:

| Se faltar… | O que acontece |
|---|---|
| `autoCommitEnable: false` | O Kafka confirma sozinho o que foi lido, e a confirmação à mão não muda nada |
| `allowManualCommit: true` | O Camel não entrega com o que confirmar (o TODO 2 não encontra nada) |
| `breakOnFirstError: true` | O Camel segue com o evento seguinte e, ao confirmar esse, confirma também o que falhou |

Essa última linha é a que se esquece. Confirmar um evento diz ao Kafka «li até aqui» na partição dele: confirmar
o seguinte cobre o que falhou.

### TODO 2: guardar com o que confirmar

Com `allowManualCommit`, o Camel deixa no cabeçalho `CamelKafkaManualCommit` o objeto que confirma **este** evento.
Guarde-o em uma variável, como primeiro passo, porque a entrega ao canal limpa os cabeçalhos:

```yaml
- setVariable:
    name: confirmar
    expression:
      simple:
        expression: "${header.CamelKafkaManualCommit}"
```

### TODO 3: confirmar no fim

No fim dos passos, depois do `filter` (ou seja, quando o desfecho já ficou no Kafka, ou o evento não era para
este canal), chame `commit()` sobre essa variável. O Camel tem um passo, `script`, que avalia uma expressão só pelo
que ela faz:

```yaml
- script:
    expression:
      simple:
        expression: "${variable.confirmar.commit()}"
```

Antes disso, não: o que é confirmado já não volta a ser lido. Repita os três TODO nas outras duas pontes.

## 4. Quebre um de propósito

Com as três pontes concluídas, na do int-rest mova o `script` do TODO 3 **para cima** do `filter`, para que
confirme antes de entregar. Rode `./verifica`.

Antes, faça a previsão: as três opções continuam postas. O que falhou volta a ser lido?

Não. Sai em vermelho a marca das 6 operações: o router não fica sabendo das da API. O evento já estava
confirmado quando o desfecho dele falhou, de modo que `breakOnFirstError` volta ao offset confirmado, que já está **depois**
dele. **As opções dizem como se confirma; o lugar do `script` diz quando.** E o quando é o que importa.
Devolva-o ao fim.

## 5. Confira

```bash
./verifica
```

O `verifica` faz dois experimentos. No primeiro arma a armadilha em `desenlaces`, manda o 2001, o 2003 e o 2004, e
a tira. No segundo para o int-rest, manda o 2006, o 2008 e o 2009, deixa a API lenta (2,5 s por pedido),
liga o int-rest e o **mata** na metade de uma entrega, para voltar a ligá-lo. No terceiro arma a armadilha de novo e
manda o 4002, que é de franquia e cabe ao parceiro. Está concluída quando aparecem as nove marcas verdes:

```
✅ Arrancaron el router, los tres canales con su puente y el sondeo
✅ La 4001 llegó a sus tres canales
✅ Control: la API tiene (…) y el ERP (…) de las 3, y el router no se enteró de ninguna (sus desenlaces no se pudieron escribir)
✅ Kafka volvió a aceptar y el router se enteró de las 6 operaciones: lo que falló se volvió a leer
✅ En la API se creó cada una UNA vez (3 creaciones para 3 órdenes)
✅ Tras matar int-rest, la API tiene las 3 y las 3 operaciones quedaron aceptadas
✅ Cada una se creó UNA vez en la API (…)
✅ Control: el 850 de la 4002 ya está en el socio y el router no se enteró (su desenlace no se pudo escribir)
✅ int-edi releyó la 4002, reconoció su 850 como el mismo y quedó aceptada: un solo 850 en el socio
```

A terceira é um **controle**: antes de tirar a armadilha, o `verifica` confere que de fato algo falhou. Se o router
já soubesse dos pedidos, a quarta marca não provaria nada.

Repare na terceira: a API pode ter 1, 2 ou 3 dos pedidos. Com a versão própria, um evento que não pode ser terminado
**para a partição dele**: o Camel tenta de novo, e o que vem atrás nessa partição espera. Esse é o custo de não
perder.

A última marca tem duas formas. Se o corte do int-rest deixou um evento sem confirmar, o int-rest o leu de novo e a
API o reconheceu pela chave de idempotência (lição 18): respondeu `200`, não `201`, e não criou nada novo. Se o corte caiu
logo depois de confirmar, não houve nada para reler. As duas estão corretas.

As duas últimas são do parceiro, e esse destino não tem chave de idempotência: o SFTP não sobrescreve um arquivo que já
está lá (lição 07), então a segunda entrega do mesmo 850 falha. Quem o reconhece é o int-edi, que já vem assim no `mio/`:
se o arquivo existe, ele o lê e o compara com o que ia deixar. Como o nome e o conteúdo saem do pedido, número de controle
incluído, um repetido é **idêntico**: responde que já estava lá, e a operação fica aceita. Se fosse diferente, seria um
conflito, como na lição 18: responde `409` e não sobrescreve nada.

**Não roda?** `./verifica solucion` testa a nossa, e `diff -r mio solucion` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `Kafka no levantó` | Ligue-o à mão com o comando de «Antes de começar» |
| `No arrancaron todas las rutas` | Confira o YAML das pontes: o log que sai embaixo diz que arquivo e que linha |
| `El router se enteró de … de 6 operaciones: … se perdieron` | Falta alguma das três opções do TODO 1, ou o `script` não está no fim |
| `Control: esperaba al menos 1 en la API y en el ERP y 0 enteradas en el router` | Se o router ficou sabendo, a armadilha não foi armada: rode o `verifica` de novo. Se a API tem 0, confira que a ponte continua entregando |
| `En la API hubo … creaciones para 3 órdenes` | A API criou algum duas vezes: a chave que o int-rest manda para ela foi alterada? (lição 18) |
| `La 4002 quedó «…» en el canal del socio` | O int-edi não reconhece o próprio 850 ao relê-lo: compare o `mio/int-edi/int-edi.camel.yaml` com o de `solucion/` |
| `Tras matar int-rest, la API tiene … de 3` | Ao voltar, o int-rest não releu o que não confirmou: confira as opções do TODO 1 na ponte dele |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**«Lido» não é «terminado».** O commit automático diz o primeiro. O que importa é o segundo, e só o
sabe quem fez o trabalho.

**O que se perde nem sempre é o que se espera.** Nas nossas execuções, matar o consumidor na metade não perdeu
eventos nem com o commit automático: ao voltar, o Kafka lhe deu o que ainda não havia sido confirmado. O que se
perdeu, sim, foi o evento cujo desfecho não pôde ser escrito. Por isso o `verifica` provoca as duas coisas.

**Não perder obriga a tolerar repetidos.** Se um evento pode ser lido duas vezes, quem o recebe tem de reconhecê-lo.
A chave de idempotência da lição 18 é o que faz esta lição funcionar. E quando o destino não sabe reconhecer um
repetido, como uma pasta de arquivos, quem o reconhece é o canal: compara o que encontra com o que ia deixar.

**Não perder tem um custo.** Um evento que não pode ser terminado para a partição dele. Por isso a ponte deixa um
desfecho mesmo que o canal não responda («não se sabe»): quem tenta a entrega de novo é o router, não a ponte.

## Exercício: o marcador de página

1. Em papel, para cada caso diga se o evento se perde, se repete ou sai bem, com o commit automático e com a
   confirmação à mão: (a) o canal entrega e o desfecho é publicado; (b) o canal entrega e o desfecho não pode ser publicado;
   (c) a ponte é morta depois de entregar e antes de publicar.
2. Abra `mio/orden-router/desenlaces.camel.yaml`. Onde o router confirma? O que aconteceria se confirmasse antes de
   anotar o desfecho na base?
3. Explique por que a ponte não tenta ela mesma a entrega de novo quando o canal não responde. (Dica: a partição.)

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O offset é confirmado à mão, depois de deixar o desfecho, e o que falha volta a ser lido](../decisiones/33-el-offset-se-confirma-despues-del-desenlace.md)

## Revisão

1. O que é o offset, e o que faz o commit automático?
2. Para que serve cada uma das três opções do TODO 1?
3. Por que o `commit()` vai no fim e não no começo?
4. O que evita que um pedido lido duas vezes seja criado duas vezes na API?
5. **A pergunta de encerramento:** o Kafka não aceitou o desfecho do 2001. Explique o que acontece com esse evento com
   o commit automático e com a confirmação à mão, e o que a versão própria custa à partição.

**A seguir:** na lição 30 a brecha está do outro lado: o router guarda o pedido e depois publica. E se o Kafka
estiver fora do ar justo naquele instante?
