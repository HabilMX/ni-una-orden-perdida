# Lição 24 — Não derrubar o destino

> **O que será alcançado:** que o ERP não receba mais registros ao mesmo tempo do que o teto fixado, mesmo que o int-bd
> rode em três réplicas e a loja mande 30 pedidos de uma vez. O que passar do teto espera a vez: não é rejeitado.

**Antes de começar é preciso ter:** as lições 12 e 22 concluídas e o ambiente da 00 ligado.

## A novidade de hoje, em uma frase

Na lição 22 o problema era um destino fora do ar. Hoje é o contrário: o destino está bem, e **quem pode derrubá-lo é a própria integração**. A partir
desta lição o router distribui até 10 pedidos ao mesmo tempo (mais abaixo explicamos por quê), e o int-bd roda em 3 réplicas,
como na lição 12. Com 30 pedidos de uma vez e um ERP que demora um segundo e meio para registrar cada um, o ERP chega a
ter 10 registros em curso. Um ERP real aguenta um certo número de conexões de integração; passado esse número ele fica
lento para todos, inclusive para os usuários que estão digitando à mão. Hoje ele ganha um **teto**: o padrão se chama
*Throttler*.

**A imagem:** a entrada de um elevador com lotação de seis pessoas. Há três portas no saguão, e em cada uma um guarda
que deixa passar duas pessoas por vez. A sétima não vai embora: espera na fila até alguém sair. Se o prédio abre uma
quarta e uma quinta porta, cada uma com o próprio guarda de dois, ao elevador podem chegar dez. **A lotação é por
porta**, e ao abrir portas é preciso refazer a conta.

---

## 1. Prepare a pasta

```bash
cd lecciones/24-no-tumbar-al-destino
cp -R inicio mio
```

Antes de escrever, duas coisas que já estão feitas e que é preciso conhecer.

**A distribuição agora atende 10 pedidos ao mesmo tempo.** Abra `mio/orden-router/reparto.camel.yaml` e leia o comentário
da rota `repartir`: o `from` dela leva `concurrentConsumers: 10`. Até a lição 23 havia um único consumidor, e por isso na
19 dissemos que tentar de novo no lugar conservava a ordem. **A partir de hoje isso muda:**

| | Até a lição 23 | A partir da 24 |
|---|---|---|
| Pedidos distribuídos ao mesmo tempo | 1 | Até 10 |
| As operações de **um** pedido | Em fila | Em fila, como antes |
| Dois pedidos **diferentes** | Em fila | Podem se cruzar |
| Dois eventos do ciclo de vida do mesmo pedido que se cruzam | Não acontecia | A máquina de estados da lição 17 cuida deles: o que chega antes é estacionado |

Por que a mudança? Porque com um único consumidor, um lote de 30 pedidos demora 30 vezes o que demorar o destino mais
lento. O preço é o problema de hoje: o router já pode mandar 10 ao mesmo tempo para um destino.

**O ERP mede o que vê.** Toda vez que o ERP registra um pedido, anota na tabela `muestra_concurrencia` quantos
registros tinha em curso naquele instante. A prova do teto não é o que foi escrito na rota: é o máximo dessa
coluna.

O trabalho vai em `mio/int-bd/int-bd.camel.yaml`, na rota `int-bd`, no começo dos passos dela. Abra também o
`compose.yaml` da lição e procure `replicas: 3`.

## 2. Faça a previsão

Responda em um papel:

1. Lembre a lição 12. Com o int-bd em 3 réplicas e sem teto, quantos registros ao mesmo tempo o ERP pode ver? (Dica: o
   router manda até 10.)
2. O ERP aguenta 6 ao mesmo tempo. Quantos **cada** réplica do int-bd tem de deixar passar?
3. Com o teto posto, os 30 pedidos demoram mais ou menos para ser registrados? Algum se perde?

Veja como está hoje:

```bash
./verifica inicio
```

O ERP registra os 30, e rápido. Mas a marca da concorrência sai em vermelho: o ERP viu **até 10** registros ao
mesmo tempo, quando o máximo com 3 réplicas e um teto de 2 seria 6.

## 3. Ponha o teto

Na rota `int-bd` está o TODO. O que se escreve é um único passo, no começo, antes de todo o resto:

```yaml
- throttle:
    mode: ConcurrentRequests
    expression:
      constant:
        expression: "…"        # cuántos a la vez, POR COPIA
```

O número fica por calcular, com a conta do elevador:

| Dado | Valor |
|---|---|
| O que o ERP aguenta | 6 ao mesmo tempo |
| Réplicas do int-bd (`compose.yaml`) | 3 |
| Teto por réplica | 6 ÷ 3 = **2** |

Repare no `mode`. O Camel tem duas formas de contar, e **a que vem por padrão não é a de hoje**:

| Modo | O que conta | Serve para o ERP? |
|---|---|---|
| `TotalRequests` (por padrão) | Quantos **entram** por período, por exemplo 2 por segundo | Não: se cada registro demora 1,5 s, entram 2 a cada segundo e eles vão se acumulando |
| `ConcurrentRequests` | Quantos **estão em curso**: entraram e ainda não responderam | Sim: é exatamente o que o ERP aguenta |

Por isso o `mode` é escrito mesmo parecendo um detalhe. Sem ele, o teto existe, mas conta outra coisa.

E repare no que o teto **não** faz: não rejeita. O terceiro pedido que chega a uma réplica espera ali a vez até
que um dos dois que estão em curso responda. Para o router, essa chamada só demorou um pouco mais: não é um erro, e
ele não tenta de novo.

## 4. Quebre um de propósito

Com o teto concluído, apague a linha `mode: ConcurrentRequests` e rode `./verifica`.

Antes, faça a previsão: o teto continua dizendo «2». O ERP vê no máximo 6?

Não. Sem `mode`, o Camel usa `TotalRequests`: cada réplica deixa **entrar** 2 pedidos por segundo, sem importar quantos
continuam em curso. Como cada registro demora um segundo e meio, eles vão se acumulando, e o ERP volta a ver mais de 6. Sai em
vermelho a marca da concorrência. O número era o correto; o que estava errado era **o que se contava**. Devolva a linha.

## 5. Confira

```bash
./verifica
```

O `verifica` sobe o int-bd em 3 réplicas, manda 30 pedidos de uma vez e depois pergunta ao ERP quantos registros ele viu ao
mesmo tempo. Está concluída quando aparecem as cinco marcas verdes:

```
✅ Arrancaron el router, los tres canales (int-bd en 3 copias) y el sondeo
✅ El ERP registró las 30 órdenes en (…) s
✅ El ERP vio a lo más (…) registros a la vez: no pasó de 3 copias × tope 2 = 6
✅ El tope hizo esperar, no rechazó: las 30 operaciones del ERP aceptadas al primer intento
✅ La API recibió las 30: el tope del ERP no frenó a los otros canales
   Registros por copia de int-bd: (…)
```

A última linha não é uma marca: diz quantos registros cada réplica fez. As três trabalharam.

Compare a segunda marca com a de `inicio/`. Com o teto, os 30 demoram mais: nas nossas execuções, uns 16 segundos
contra uns 6; o `verifica` imprime os da própria execução. **É isso que custa cuidar do ERP**, e é um preço razoável: o ERP continua servindo os demais.

Veja a conta do ERP diretamente:

```bash
docker compose -f ../../infra/compose.yaml exec erp mariadb -uintegracion -ppractica erp \
  -e "SELECT MAX(en_curso) AS maximo, COUNT(*) AS registros FROM muestra_concurrencia"
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/int-bd/int-bd.camel.yaml solucion/int-bd/int-bd.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `El ERP vio hasta 10 registros a la vez` | Não há `throttle`, ou ele ficou depois da chamada ao ERP: vai no começo dos passos |
| `El ERP vio hasta` mais de 6, mas menos de 10 | Falta `mode: ConcurrentRequests`, ou o teto não é 2 |
| `int-bd corre en 1 copia(s) y se esperaban 3` | O `replicas` do `compose.yaml` foi mudado: devolva-o para 3 |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**O destino tem um limite mesmo que não o diga.** Um ERP, um banco de dados ou uma API de terceiros aguentam certo
número de conexões. Uma integração que cresce em réplicas e em concorrência sem teto é a forma mais comum de derrubar
um sistema que estava bem.

**O teto vai onde se conhece o destino.** O int-bd é o único que fala com o ERP, e por isso é ele quem sabe quanto
o ERP aguenta. Pôr o teto no router misturaria em um só lugar o que se sabe de todos os destinos.

**O teto é por réplica, e escalar obriga a recalcular.** Se amanhã o int-bd rodar em 5 réplicas, o mesmo teto de 2 deixa
passar 10. A conta é: o que o destino aguenta dividido pelas réplicas que falam com ele.

**Fazer esperar não é rejeitar.** Um teto que rejeita obriga quem chama a tentar de novo, e com isso se ganha pouco. Um
teto que faz esperar só deixa a chamada mais lenta, e para o router ela continua sendo um «sim».

## Exercício: o elevador com mais portas

1. Em papel: o ERP aguenta 6 e o int-bd roda em 3 réplicas com teto 2. Se o ERP de produção aguentasse 20 e a ideia fosse
   rodar 4 réplicas, que teto caberia? E com 6 réplicas? Escreva a conta.
2. Mude no `compose.yaml` as réplicas do int-bd para `5`, sem mexer no teto, e rode `./verifica`. A primeira
   marca vai sair em vermelho, porque o `verifica` espera 3. Leia a terceira: quantos registros ao mesmo tempo o ERP viu? Passou
   de 6? Escreva o que seria preciso mudar em `int-bd.camel.yaml` para que não passasse, e devolva as réplicas para 3.
3. A partir de hoje a distribuição atende 10 pedidos ao mesmo tempo. Explique em duas frases por que isso não tira de ordem os eventos de
   **um** mesmo pedido. (Dica: a lição 17.)

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Um teto por réplica na direção do ERP, contando o que está em curso](../decisiones/28-un-tope-por-copia.md)

## Revisão

1. O que é um Throttler, e que problema ele resolve aqui?
2. Por que o teto vai no int-bd e não no router?
3. Que diferença há entre `TotalRequests` e `ConcurrentRequests`? Por que hoje serve o segundo?
4. O que acontece com um pedido que chega quando a réplica dele já tem 2 em curso?
5. **A pergunta de encerramento:** o ERP aguenta 6 e hoje roda com 3 réplicas. Quantos registros ao mesmo tempo ele pode ver com
   5 réplicas e o mesmo teto, e o que seria preciso mudar ao escalar?

**A seguir:** na lição 25 a pergunta é outra: a API e o ERP já aceitaram e o parceiro ainda não mandou o 997.
Quando um pedido está **completo**?
