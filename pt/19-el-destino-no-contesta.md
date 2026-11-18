# Lição 19 — O destino não responde

> **O que será alcançado:** que, quando a API não responde, o router tente de novo sozinho, cada vez com mais pausa,
> até o pedido chegar. E que enquanto isso o embarque desse pedido não passe à frente: a API tem de receber
> primeiro o pedido e depois o embarque dele.

**Antes de começar é preciso ter:** a lição 18 concluída e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na lição 15 o router aprendeu a guardar antes de responder, e na 18 a reconhecer uma mensagem repetida. Hoje acontece o
contrário da 18: a mensagem **não chega**, porque o destino não responde. O router não tem como saber se a API
recebeu o pedido; o que ele pode fazer é tentar de novo. E como já sabe reconhecer repetidos (a API também: viu-se
isso na lição 02 com a chave), tentar de novo não duplica nada.

**A imagem:** ligar para alguém que não atende. Não se liga a cada segundo durante uma hora: liga-se, espera-se um
minuto, liga-se de novo, esperam-se dois, depois quatro. Dá-se tempo para a pessoa sair da reunião. E não se conta a
segunda coisa antes da primeira: se a ligação era para avisar que o pacote já saiu, não se manda antes uma mensagem
de «já chegou?».

---

## 1. Prepare a pasta

```bash
cd lecciones/19-el-destino-no-contesta
cp -R inicio mio
```

Hoje todo o trabalho vai em um arquivo novo do router, `orden-router/reintentos.camel.yaml`. Abra-o e leia o
comentário do alto por inteiro. Ele traz um desenho:

```
intento 1 ✗ ─ 1 s ─ intento 2 ✗ ─ 2 s ─ intento 3 ✗ ─ 4 s ─ intento 4 ✓     (más o menos 15 % al azar)
```

E três regras que já estão no código:

| Regra | O que significa | Onde vive |
|---|---|---|
| **Só o router tenta de novo** | Os canais (int-rest, int-bd, int-edi) respondem o que lhes aconteceu e pronto | Os canais não têm novas tentativas |
| **Só se repete o que ficou «não se sabe»** | Sem resposta, ou um erro 5xx. Um 4xx desta API (o 422 dela) é um «não» do destino e não se repete (na lição 20 vai-se ver que nem todos os 4xx são um «não») | `direct:intentar` lança uma `IOException` só nesse caso |
| **Cada tentativa deixa a própria linha** | Antes de chamar o canal, `intentar` escreve na tabela `intento`; depois anota como foi | `reparto.camel.yaml`, desde a lição 15 |

## 2. Faça a previsão

Responda em um papel:

1. A API está desligada quando chega o pedido 4001, e volta em poucos segundos. Enquanto isso, o armazém deixa o
   embarque do 4001. Em que ordem o pedido e o embarque deveriam chegar à API? E se chegassem ao contrário?
2. Se o router tenta de novo a cada 100 milissegundos, o que acontece com uma API que está iniciando?
3. O ERP e o parceiro estavam no ar. Quantas vezes deveriam receber o 4001?

Veja como está hoje:

```bash
./verifica inicio
```

A loja recebe o 202, porque o router guardou. Mas o canal rest tenta **uma única vez**: a API estava
desligada, a tentativa fica em `no_se_sabe` e ninguém tenta de novo. A API volta alguns segundos depois e o
4001 nunca chega a ela.

## 3. Tente de novo com espera crescente

Abra `mio/orden-router/reintentos.camel.yaml`. No alto há um `routeConfiguration` com um `onException` que captura
a `IOException` que `intentar` lança. Hoje ele só anota no log que desistiu. O trabalho é dizer a ele **quantas vezes
e com que pausa** tentar de novo, com uma `redeliveryPolicy` dentro do `onException`:

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | `maximumRedeliveries: 5` e `redeliveryDelay: 1000` | Até cinco novas tentativas; a primeira, depois de um segundo |
| 2 | `useExponentialBackOff: true` e `backOffMultiplier: 2` | Que cada pausa seja o dobro da anterior: 1, 2, 4, 8, 16 segundos. É a espera crescente (backoff) |
| 3 | `useCollisionAvoidance: true` e `collisionAvoidanceFactor: 0.15` | Que cada pausa varie 15% ao acaso. É a variação aleatória (jitter) |
| 4 | `retryAttemptedLogLevel: WARN` | Que cada nova tentativa apareça no log |

A `redeliveryPolicy` vai no mesmo nível de `handled` e `steps`, dentro do `onException`. O esqueleto fica
assim:

```yaml
- onException:
    exception:
      - java.io.IOException
    redeliveryPolicy:
      maximumRedeliveries: 5
      …
    handled:
      constant:
        expression: "true"
    steps:
      - log: …
```

Repare em duas coisas:

- **A variação aleatória do TODO 3 parece um capricho e não é.** Imagine três réplicas do router (a lição 12) que perdem a API ao
  mesmo tempo. Sem variação, as três tentam de novo no segundo exato, depois aos dois, depois aos quatro: a API
  volta e recebe três golpes juntos a cada vez. Com 15% de variação, os golpes se separam.
- **A nova tentativa é «no lugar».** A fila `seda:repartir` tem um único consumidor, e a pausa o detém. O que
  vem atrás na fila, como o embarque do 4001, espera a vez. É isso que conserva a ordem.

## 4. Quebre um de propósito

Com as novas tentativas concluídas, mude `maximumRedeliveries` para `1` e rode `./verifica`.

Antes, faça a previsão: o `verifica` deixa a API desligada por uns seis segundos. Com uma única nova tentativa depois de um segundo, o pedido chega
à API?

Não chega. A primeira tentativa falha, a nova tentativa um segundo depois também, e o router desiste enquanto a API continua
desligada. Sai em vermelho a marca das tentativas de rest e a da API. A operação fica em `no_se_sabe`, e isso está
correto: o router **não inventa** que a entregou. Mas mostra o custo de ficar curto: cinco novas tentativas com
espera crescente cobrem uns trinta segundos de queda; uma só, um segundo. Volte para `5`.

## 5. Confira

```bash
./verifica
```

O `verifica` desliga a API, manda o 4001, deixa o embarque dele na caixa postal, espera alguns segundos e liga a
API de novo. Está concluída quando aparecem as seis marcas verdes:

```
✅ Arrancaron el router (con sus reintentos), los tres canales y el sondeo
✅ Con la API apagada, la tienda recibió su 202: lo suyo quedó guardado
✅ Estado: el canal rest reintentó en el lugar: (…) intentos; esperas (…); desenlaces (…)
✅ Estado: el ERP y el socio no se repitieron: aceptados al primer intento (bd=aceptada/1 edi=aceptada/1)
✅ API: recibió la orden y después su embarque, y la 4001 quedó EMBARCADO
✅ ERP: la 4001 está EMBARCADO
```

Leia a terceira marca com calma. Ela diz quantas tentativas houve, quanto se esperou entre uma e outra e como terminou cada
uma. As esperas mudam a cada execução por causa da variação aleatória, mas sempre crescem, e a última tentativa é a única
`aceptada`.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/reintentos.camel.yaml solucion/orden-router/reintentos.camel.yaml` mostra em que são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `esperaba 3 o más intentos de rest` e há 1 | Falta a `redeliveryPolicy`, ou ela ficou fora do `onException` |
| `esperaba 3 o más intentos de rest` e as esperas não crescem | Falta `useExponentialBackOff: true` |
| `esperaba la orden antes que el embarque` e o pedido «no llegó» | Poucas novas tentativas ou pausas curtas demais: o router desistiu antes de a API voltar |
| `El ERP y el socio quedaron` com mais de uma tentativa | Há novas tentativas também em outro lugar: elas só vão em `reintentos.camel.yaml` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

Se o `verifica` for interrompido no meio, a API volta a ligar sozinha na saída. Se mesmo assim ela aparecer desligada:
`docker compose -f ../../infra/compose.yaml start api-pedidos`.

---

## O que isto ensina fora do curso

**Tentar de novo só é seguro se o destino reconhece os repetidos.** O router não sabe se a primeira tentativa chegou.
Se chegou e ele tenta de novo, a API recebe o pedido duas vezes, e o que impede a duplicação é a chave de idempotência dela (a
lição 02). Novas tentativas sem idempotência são duplicações com outro nome.

**A espera crescente com variação aleatória é a prática habitual**, e tem nome: *exponential backoff with jitter*. Os
grandes provedores de nuvem a recomendam para qualquer cliente dos serviços deles. Com espera fixa, ou se dá tempo
demais a quem já voltou ou se golpeia quem ainda está iniciando.

**Só um tenta de novo.** Se o router tenta de novo cinco vezes e o canal também cinco, um único envio pode virar
trinta e seis chamadas. Escolhe-se um lugar, e o resto responde o que lhe aconteceu.

**A ordem tem um preço.** Tentar de novo no lugar detém a fila: enquanto a API não responde, nada do que
vem atrás avança. Aqui vale a pena, porque um embarque antes do pedido é um erro. Quando um destino fora do ar
detém demais os outros, é preciso outra ferramenta: é a lição 22.

## Exercício: o log das tentativas

Com a versão própria concluída, rode `./verifica` e entre na base:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. Consulte as tentativas do canal rest para a criação do 4001, com a hora de cada uma:
   `SELECT i.n, i.inicio, i.desenlace FROM intento i JOIN operacion p ON p.id = i.operacion WHERE p.orden = '4001'
   AND p.momento = 'creada' AND p.canal = 'rest' ORDER BY i.n;`
   Subtraia à mão a hora de cada tentativa menos a da anterior. Parecem-se com 1, 2, 4 e 8 segundos? Por que não são
   exatas?
2. Some as cinco pausas da política (1 + 2 + 4 + 8 + 16). Se a API demorasse um minuto para voltar, o que aconteceria com
   o pedido? Em que estado ficaria, e o que seria preciso para ele chegar? (Quem resolve é a lição 23.)
3. Explique com as próprias palavras por que o embarque do 4001 chegou **depois** do pedido, embora o armazém o tenha deixado
   enquanto a API estava desligada.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Tenta-se de novo no lugar, só no router, com espera crescente](../decisiones/23-reintentar-en-el-lugar.md)

## Revisão

1. Que respostas do destino levam a uma nova tentativa e quais não? Por que o 422 desta API não?
2. Para que serve cada pausa ser o dobro da anterior?
3. Para que servem os 15% de variação aleatória? Explique com três réplicas do router.
4. Por que tentar de novo não duplicou o pedido na API?
5. **A pergunta de encerramento:** tentar de novo «no lugar» conserva a ordem. Por quê, e quanto custa? Use o embarque do
   4001 como exemplo.

**A seguir:** na lição 20 a API responde, mas nem sempre diz «sim». Às vezes diz «não», e às vezes demora
tanto que a chamada é cortada. Escreveu ou não escreveu?
