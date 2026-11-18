# Lição 16 — Chega a entrega

> **O que será alcançado:** receber o aviso que a transportadora manda quando entrega um pacote, de modo que o pedido
> fique ENTREGADO na API e no ERP, e que o parceiro receba o 214 dele. E distinguir um aviso assim de uma requisição.

**Antes de começar é preciso ter:** a lição 15 concluída e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Já existem dois eventos do ciclo de vida do pedido: ele é **criado** (a loja chama) e é **embarcado** (o armazém deixa um arquivo). Hoje
chega o terceiro: ele é **entregue**. E chega de uma terceira forma: a transportadora **chama sozinha** quando entregou, em
um endereço da integração. Isso é um **webhook**, e o que ele traz é um **evento**: não pede nada, conta que algo já
aconteceu.

**A imagem:** a mensagem da transportadora no celular, «O pacote foi entregue». Ela não pede que se faça nada nem
espera uma resposta sobre o que será feito. Só quer saber que a mensagem chegou. O que se faz depois (avisar
quem o esperava, conferir que é o pacote certo) é assunto de quem a recebeu.

---

## 1. Prepare a pasta

```bash
cd lecciones/16-llega-la-entrega
cp -R inicio mio
```

Abra o aviso de exemplo, `datos/webhooks/entrega-4001.json`. Ele traz o evento (`entregado`), a transportadora, **o
código de rastreio**, o pedido, quando foi entregue e quem recebeu.

Tudo o que é de saída já está feito. Cada canal tem o próprio `entrega.camel.yaml`:

| Canal | O que faz com a entrega |
|---|---|
| int-rest | Conta à API que o pedido foi entregue, como um evento com a chave dele |
| int-bd | Registra a entrega no ERP e passa o pedido para ENTREGADO |
| int-edi | Monta um **214**, o status de embarque do X12, com o código de rastreio no segmento `B10`, e o deixa no SFTP |

A distribuição da 15 já sabe mandar o evento `entregada`. **Falta a porta de entrada.**

## 2. Faça a previsão

Responda em um papel:

1. A transportadora avisa que entregou o 4001. O que tem de ficar na base de estado, e o que em cada destino?
2. O que o router responde à transportadora: o que disseram a API, o ERP e o parceiro, ou só «recebido»? Por quê?
3. Chega um aviso do pedido 9999, que a loja nunca criou. O que o router deveria responder?

Veja como está hoje:

```bash
./verifica inicio
```

O webhook responde **501**, que em HTTP quer dizer «isso eu não sei fazer», e não guarda nada. O 4001 fica em
EMBARCADO para sempre, embora o cliente já tenha o pacote.

## 3. Abra a porta de entrada

Abra `mio/orden-router/paqueteria.camel.yaml`. A rota `webhook-paqueteria` escuta em
`POST /webhooks/paqueteria`, e hoje só responde 501. O modelo é o `embarque-router` de
`orden-router.camel.yaml`: faz quase o mesmo para o embarque. Leia-o antes de começar.

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | `convertBodyTo` para `String` | Que o aviso seja guardado tal como chegou |
| 2 | Duas variáveis: `orden` (de `$.orden`, com `jsonpath`) e `momento` (`entregada`). E chame `direct:hilo` | Saber de que pedido é e que evento é, com o fio da 09 |
| 3 | Chame `direct:cargar-orden`. Com um `choice`: se `${variable.tienda} == null`, para `direct:no-existe`; se não, para `direct:canales`, `direct:guardar-momento` e `direct:contestar-y-repartir` | Um pedido que não conhecemos é rejeitado; um que conhecemos é guardado, respondido e distribuído |

E apague os dois passos que hoje respondem 501.

Repare que **não se escreve nada novo de fundo**: `cargar-orden`, `canales`, `guardar-momento` e
`contestar-y-repartir` já existem desde a 15. A entrega entra por uma porta diferente, mas dentro segue o mesmo
caminho do pedido e do embarque: **guardar, responder, distribuir**.

## 4. Quebre um de propósito

Com o webhook concluído, tire o `choice` e deixe só o que está no ramo `otherwise`: como se todo pedido existisse.
Suba os serviços e mande um aviso de um pedido que não existe:

```bash
DONDE=mio docker compose -f compose.yaml up -d
curl -i -XPOST localhost:8080/webhooks/paqueteria -H 'Content-Type: application/json' \
  -d '{"evento":"entregado","paqueteria":"PAQX","guia":"PAQX770009999","orden":"9999"}'
```

Antes, faça a previsão: o que o router responde?

Ele já não responde 404, responde um erro do servidor. `guardar-momento` tenta criar as operações de um pedido que
não está na tabela `orden`, a base impede e a transação é desfeita. Agora pense na transportadora: diante de um
erro do servidor, **um emissor de webhooks manda o aviso de novo**, e continuará mandando. O 404 diz a
verdade, «esse pedido eu não conheço», e o emissor para de insistir. Devolva o `choice` e desligue:

```bash
DONDE=mio docker compose -f compose.yaml down
```

## 5. Confira

```bash
./verifica
```

O `verifica` cria o 4001, o embarca, faz o papel de transportadora com `entrega-4001.json` e no final manda um aviso do
9999. Está concluída quando aparecem as nove marcas verdes:

```
✅ Arrancaron el router (con el webhook de la paquetería), los tres canales y el sondeo
✅ Antes de la entrega: la 4001 está EMBARCADO en la API y en el ERP
✅ La paquetería avisó la entrega de la 4001 y el router contestó 202 «recibida»
✅ Estado: tres operaciones de la 4001 con momento=entregada, aceptadas, y la orden va en «entregada»
✅ API: la 4001 está ENTREGADO, con la guía con la que salió
✅ ERP: la 4001 está ENTREGADO y su fila de entrega tiene la guía
✅ Socio: el 214 de la 4001 está en su SFTP y el lector X12 no le encontró fallas
✅ Socio: el 214 lleva la guía en el B10 y el número de control de la base (…)
✅ Un aviso de la orden 9999, que no existe: el router contestó 404 y no guardó nada
```

Abra o 214 que chegou ao parceiro, `mio/salida/214-4001.edi`, e procure o código de rastreio no segmento `B10`.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/paqueteria.camel.yaml solucion/orden-router/paqueteria.camel.yaml` diz em que elas são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `Esperaba 202 del webhook y el router contestó` … 501 | Ficaram os dois passos de `inicio/` que respondem 501: apague-os |
| `Un aviso de una orden que no existe contestó «500»` | Falta o `choice`, ou a condição não é `${variable.tienda} == null` |
| `las operaciones de la entrega quedaron «sin operaciones»` | A variável `momento` não tem esse nome ou não vale `entregada`, ou falta `direct:guardar-momento` |
| O 4001 foi guardado, mas nenhum destino o tem | Falta `direct:contestar-y-repartir` no final |
| `El puerto 8080 está ocupado` | Os serviços do passo 4 ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Um evento e uma requisição são respondidos de formas diferentes.** A uma requisição («crie este pedido») responde-se se foi possível.
A um evento («já entreguei») só se responde que ele chegou: o que aconteceu, já aconteceu, e não depende de quem recebe. Por isso o
router responde 202 e distribui depois.

**Quem manda um webhook espera pouco e tenta de novo.** Os emissores contam como entregue só o que recebeu um 2xx a
tempo, e mandam de novo o resto. Responder assim que se guardou cuida das duas coisas: não faz esperar e não provoca
novas tentativas a mais.

**O código de resposta é uma instrução para quem manda.** 202 é «já está comigo», 404 é «não insista, isso não
existe» e um erro do servidor é «tente depois». Escolher mal o código muda o que o outro sistema faz.

## Exercício: três portas, um caminho

Com a versão própria concluída:

1. Faça uma tabela com os três eventos: criado, embarcado e entregue. Para cada um: quem o manda, por onde entra
   (HTTP da loja, arquivo do armazém, webhook) e que rota do router o recebe. O que as três
   rotas têm em comum depois da entrada?
2. Na base de estado, consulte as operações do 4001 nos três eventos:
   `SELECT momento, canal, estado FROM operacion WHERE orden = '4001' ORDER BY id`. Quantas linhas são, e por quê?
3. O que aconteceria se a transportadora avisasse a entrega do 4002 **antes** de chegar o embarque dele? Escreva a
   resposta: é a lição 17.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O webhook da transportadora responde 202 assim que guardou](../decisiones/20-el-webhook-contesta-202.md)

## Revisão

1. O que é um webhook, e em que ele se distingue da forma como o pedido chega e da forma como o embarque chega?
2. O que é um 214, e o que ele leva no segmento `B10`?
3. Por que o router responde 202 à transportadora e não o que disseram a API, o ERP e o parceiro?
4. Por que um aviso de um pedido que não existe é respondido com 404 e não com um erro do servidor?
5. **A pergunta de encerramento:** «entreguei o pacote» é um evento; «crie este pedido» é uma requisição. Com um exemplo
   do curso para cada um, explique em que eles se distinguem e como muda o que o router responde.

**A seguir:** na lição 17 o mundo sai de ordem: a entrega do 4002 chega antes do código de rastreio dele. O router não
pode distribuí-la ainda, mas também não pode jogá-la fora. O que fazer com ela?
