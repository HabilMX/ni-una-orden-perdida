# Lição 22 — Derrubar um canal

> **O que será alcançado:** que, quando o SFTP do parceiro cai, a API e o ERP continuem recebendo os pedidos
> na hora. O router deixa de chamar o destino fora do ar, anota o que não mandou, e volta a testá-lo sozinho depois de
> um tempo.

**Antes de começar é preciso ter:** a lição 20 concluída e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na lição 10 o ERP foi desligado e viu-se que a API e o parceiro recebiam o pedido do mesmo jeito: «um canal fora do ar não detém os outros
dois». Desde a lição 19 isso já não é verdade. O router tenta de novo no lugar, com pausas que somam meio minuto, e **enquanto espera o
parceiro, a fila inteira espera com ele**. Um pedido que só tinha de chegar à API demora meio minuto por culpa de
um destino que não tem nada a ver. Hoje põe-se um **disjuntor** (circuit breaker) por canal: quando um destino falha seguidamente, o
router deixa de chamá-lo por um tempo.

**A imagem:** o disjuntor do quadro de luz de casa, um por área. Se o secador de roupa dá curto, desarma o disjuntor da
lavanderia e a cozinha continua com luz. Não se pede a ninguém que pare de usar o secador: o disjuntor corta sozinho. E
depois de conferir, religa-se e testa-se. Se houvesse um único disjuntor para a casa toda, um curto na lavanderia
deixaria a casa sem geladeira.

---

## 1. Prepare a pasta

```bash
cd lecciones/22-tirar-un-canal
cp -R inicio mio
```

Todo o trabalho vai em `mio/orden-router/reparto.camel.yaml`, na rota `intentar`, no passo 3: a chamada ao
canal. Leia-o antes de escrever. O comentário do alto descreve os três estados de um disjuntor:

| Estado | O que faz | Quando muda |
|---|---|---|
| **Fechado** | Chama o canal, como sempre | **Abre** se, das últimas 4 chamadas (com pelo menos 2), a metade ou mais falharam |
| **Aberto** | **Não** chama o canal: responde na hora que não se tentou | Passados 10 segundos, fica **entreaberto** |
| **Entreaberto** | Deixa passar **uma** chamada de teste | Se dá certo, **fecha**; se falha, abre de novo |

E há uma rota já feita, `sin-respuesta`, embaixo no mesmo arquivo. É a que roda quando o disjuntor não deixa
a chamada passar ou quando a chamada falhou. Ela distingue os dois casos, e isso importa:

| Caso | `codigo` | Desfecho da tentativa | A operação |
|---|---|---|---|
| O disjuntor estava aberto e **não se chamou** | `-1` | `no_enviado` | Continua `pendiente` |
| Chamou-se e falhou | `0` | `no_se_sabe` (a lição 20) | Tenta-se de novo |

`no_enviado` não é uma dúvida: o router sabe que não chamou. Por isso não se tenta de novo no lugar nem vai para conciliação.
Fica `pendiente`, e na lição 23 vai-se ver como é mandado de novo.

## 2. Faça a previsão

Responda em um papel:

1. O SFTP do parceiro é derrubado e chegam quatro pedidos de franquia. Sem disjuntor, quanto demora o quarto para
   chegar à API? (Dica: some as pausas da lição 19.)
2. Por que um disjuntor por canal e não um para o router inteiro?
3. O SFTP volta. Quem tem de «religar o disjuntor»?

Veja como está hoje:

```bash
./verifica inicio
```

O `verifica` desliga o SFTP, manda quatro pedidos e conta quantos chegaram à API e ao ERP em uns 25 segundos.
Chega **um de quatro**. O 4001 passa para a API e para o ERP, e depois a fila fica esperando as novas tentativas do parceiro;
os outros três não avançam embora a API e o ERP estejam perfeitos.

## 3. Ponha um disjuntor por canal

No passo 3 de `intentar` há um `doTry` que chama `direct:llamar-canal`. Esse `doTry` é o que vai ser substituído.
Tem três TODO:

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | Um `choice` por `${variable.canal}` com três ramos (`rest`, `bd`, `edi`), e em cada um um `circuitBreaker` com o próprio `id`: `cortacircuito-rest`, `cortacircuito-bd`, `cortacircuito-edi` | Um disjuntor por área |
| 2 | Em cada `circuitBreaker`, a `resilience4jConfiguration` dele | Quando abre e quanto demora para testar |
| 3 | Em cada um, um `onFallback` que chame `direct:sin-respuesta` | Que fique anotado se não se chamou ou se se chamou e falhou |

**Um** ramo fica assim. Os outros dois são iguais, cada um com o próprio canal e o próprio `id`:

```yaml
- choice:
    when:
      - expression:
          simple:
            expression: "${variable.canal} == 'rest'"
        steps:
          - circuitBreaker:
              id: cortacircuito-rest
              resilience4jConfiguration:
                slidingWindowSize: 4
                minimumNumberOfCalls: 2
                failureRateThreshold: 50
                waitDurationInOpenState: "10000"
                permittedNumberOfCallsInHalfOpenState: 1
                automaticTransitionFromOpenToHalfOpenEnabled: true
              steps:
                - to:
                    uri: direct:llamar-canal
              onFallback:
                steps:
                  - to:
                      uri: direct:sin-respuesta
      # … la rama de bd y la de edi
```

Cada linha da configuração é uma linha da tabela de estados:

| Parâmetro | Valor | O que diz |
|---|---|---|
| `slidingWindowSize` | 4 | Olha as últimas 4 chamadas |
| `minimumNumberOfCalls` | 2 | Não decide com menos de 2 |
| `failureRateThreshold` | 50 | Abre com 50% de falhas ou mais |
| `waitDurationInOpenState` | `"10000"` | Aberto por 10 segundos (vai em milissegundos e entre aspas) |
| `permittedNumberOfCallsInHalfOpenState` | 1 | Em entreaberto, uma única chamada de teste |
| `automaticTransitionFromOpenToHalfOpenEnabled` | `true` | Passa a entreaberto sozinho, sem esperar que alguém chame |

A configuração é escrita três vezes, igual. Daria para escrevê-la uma vez e reutilizá-la; aqui ela vai completa em cada canal
para que cada disjuntor se leia sozinho.

Uma nota para mais adiante: se um dia esta rota for compilada para nativo com Camel Quarkus, o disjuntor vem de outra
biblioteca, mas o `circuitBreaker` do YAML é o mesmo e nada muda.

## 4. Quebre um de propósito

Com os três disjuntores concluídos, mude o `id` dos três para um só, `cortacircuito`, como se houvesse um
único disjuntor para o router inteiro. Rode `./verifica`.

Antes, faça a previsão: a API e o ERP estão bem. Continuam em dia?

Não. O disjuntor conta as chamadas dos três canais juntas: as do parceiro falham, as da API e do ERP não,
e com a metade falhando ele abre **para todos**. A API e o ERP deixam de receber pedidos por causa de um destino que não é
o deles, e no log aparece aberto o disjuntor de `rest` e o de `bd`. Saem em vermelho a marca da API e do ERP
e a do log. Devolva os três `id`.

## 5. Confira

```bash
./verifica
```

O `verifica` desliga o SFTP, manda quatro pedidos, confere a API, o ERP e o estado; depois liga o SFTP de novo,
espera passarem os 10 segundos e manda um quinto. Ao sair, deixa o SFTP ligado. Está concluída quando aparecem as
cinco marcas verdes:

```
✅ Arrancaron el router (con un cortacircuito por canal), los tres canales y el sondeo
✅ Con el SFTP caído, la API y el ERP siguieron al día: las 4 órdenes en los dos, en (…) s
✅ El canal del socio: las 4 quedaron pendientes, su último intento «no_enviado», y de la 4002 en adelante con un solo intento (no esperaron)
✅ Log: se abrió el cortacircuito del socio, y solo ese
✅ Volvió el SFTP: la 4005 pasó por el cortacircuito ya cerrado y su 850 está en el socio
```

Leia a terceira com calma. O 4001 tentou várias vezes: o disjuntor ainda estava fechado, e as falhas
dessas tentativas são as que o abriram. Do 4002 em diante há uma única tentativa, `no_enviado`: o router nem
sequer chamou.

E veja o log do router enquanto isso acontece:

```bash
DONDE=mio docker compose -f compose.yaml logs orden-router | grep -i cortacircuito
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/reparto.camel.yaml solucion/orden-router/reparto.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `a los (…) s la API tiene 1 de 4` | Não há disjuntor, ou o do parceiro nunca abre: confira que o ramo `edi` existe e que `failureRateThreshold` é 50 |
| `El canal del socio quedó` com 0 não enviadas | Falta o `onFallback` para `direct:sin-respuesta`: sem ele, nada anota o `no_enviado` |
| `esperaba el cortacircuito de edi abierto y los de rest y bd cerrados` | Dois ramos compartilham o `id`: cada canal leva o próprio |
| `Volvió el SFTP y la 4005 quedó «pendiente»` | O disjuntor não passa a entreaberto: falta `automaticTransitionFromOpenToHalfOpenEnabled: true`, ou `waitDurationInOpenState` é muito maior que 10000 |
| `No arrancaron todas las rutas` | Confira a indentação: `steps` e `onFallback` vão dentro de `circuitBreaker`, no mesmo nível de `resilience4jConfiguration` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

Se o `verifica` for interrompido no meio, o SFTP volta a ligar sozinho na saída. Se mesmo assim ele aparecer desligado:
`docker compose -f ../../infra/compose.yaml start socio-sftp`.

---

## O que isto ensina fora do curso

**Um destino fora do ar não deve derrubar os que estão bem.** É a ideia por trás do disjuntor e dos
«compartimentos» de um navio: se um inunda, os demais continuam secos. Sem isso, a menor falha do sistema
decide a velocidade de tudo.

**Não chamar o que se sabe que está fora do ar é um favor para os dois lados.** Poupa-se a espera, e o destino não recebe uma onda de
chamadas enquanto tenta voltar.

**O disjuntor se recupera sozinho.** A chamada de teste em entreaberto evita que alguém tenha de se lembrar de
«religar o disjuntor». Se o destino continua mal, ele abre de novo e nada aconteceu.

**«Não mandei» é um fato, e é anotado como tal.** Uma tentativa `no_enviado` diz algo diferente de uma `no_se_sabe`. A
primeira pode ser mandada de novo sem medo, porque o destino nunca a viu.

## Exercício: o disjuntor em três tempos

Com a versão própria concluída, rode `./verifica` e depois:

1. Com o log de `orden-router`, monte uma linha do tempo da execução: quando o disjuntor de `edi` abriu,
   quantos pedidos deixou sem mandar e quando passou a chamada de teste. Marque nela os três estados: fechado,
   aberto e entreaberto.
2. Na base, consulte as tentativas do canal do parceiro:
   `SELECT p.orden, i.n, i.desenlace, i.respuesta->>'cuerpo' FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.canal = 'edi' AND p.momento = 'creada' ORDER BY p.orden, i.n;`
   O que diz a resposta de uma tentativa `no_enviado`? E a de uma `no_se_sabe`?
3. Os quatro pedidos do parceiro ficaram `pendiente`. Ninguém vai mandá-los sozinho. O que falta ao router para
   recuperá-los? Escreva a resposta: é a lição 23.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Um disjuntor por canal, no router](../decisiones/26-un-cortacircuito-por-canal.md)

## Revisão

1. Que três estados tem um disjuntor, e o que ele faz em cada um?
2. O que significa cada número da configuração: 4, 2, 50, 10000 e 1?
3. Por que um disjuntor por canal e não um para o router inteiro?
4. Em que se distingue uma tentativa `no_enviado` de uma `no_se_sabe`? Por que a primeira não vai para conciliação?
5. **A pergunta de encerramento:** na lição 10 concluiu-se que «um canal fora do ar não detém os outros dois». Por que deixou de
   ser verdade na 19, e o que torna isso verdade outra vez? Use o que se viu no `verifica` de `inicio/` e no da versão própria.

**A seguir:** na lição 23 alguém tem de mandar de novo o que ficou pendente ou em conciliação. Como
reprocessar um pedido sem duplicá-lo e deixando escrito quem fez isso e por quê?
