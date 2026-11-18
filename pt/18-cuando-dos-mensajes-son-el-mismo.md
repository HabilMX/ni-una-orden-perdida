# Lição 18 — Quando duas mensagens são a mesma?

> **O que será alcançado:** que o router reconheça um aviso que já recebeu, mesmo que chegue escrito de outro jeito, e não
> o distribua outra vez. E que distinga esse repetido de um aviso que traz a mesma chave com outro conteúdo: isso não
> é um repetido, é um conflito, e gera um alerta.

**Antes de começar é preciso ter:** as lições 15 e 16 concluídas e o ambiente da 00 ligado. Esta lição
usa uma tabela nova da base de estado, `conflicto`. Se a base for de antes, recrie-a uma vez:

```bash
docker compose -f infra/compose.yaml up -d --wait --force-recreate -V estado
```

## A novidade de hoje, em uma frase

Na lição 16 viu-se que quem manda um webhook **tenta de novo** se não recebe o 2xx a tempo. Isso tem uma
consequência: cedo ou tarde o mesmo aviso chega duas vezes. Hoje o router aprende a reconhecê-lo. A isso se dá o
nome de *Idempotent Consumer*: receber duas vezes a mesma coisa deixa o mesmo resultado que recebê-la uma vez.

**A imagem:** a guarita da entrada de um prédio com o livro de visitas. Chega a senhora do 302 às
10:00 e é anotada. Às 10:05 ela volta porque esqueceu o crachá: não é anotada outra vez, já está no livro. Mas
se chega alguém dizendo que é a senhora do 302 e não se parece com a da foto, o guarda não a deixa passar nem
a anota por cima: liga para quem mora lá. O primeiro caso é um **repetido**; o segundo, um **conflito**.

---

## 1. Prepare a pasta

```bash
cd lecciones/18-cuando-dos-mensajes-son-el-mismo
cp -R inicio mio
```

O router tem um arquivo novo, `orden-router/reconocer.camel.yaml`. Abra-o e leia o comentário do alto. Ele diz
com que se reconhece uma mensagem:

| Conceito | O que é | No curso |
|---|---|---|
| **Chave** | O que diz **de que** é a mensagem | `orden-<orden>-<momento>`, por exemplo `orden-4001-entregada`. Cada canal a leva uma vez: é a `UNIQUE (orden, momento, canal)` de `operacion` |
| **Impressão digital** | O que diz **o que traz** a mensagem | O sha256 do corpo, já normalizado. Dois corpos com o mesmo conteúdo têm a mesma impressão digital |

Com essas duas peças saem três casos:

| Caso | Chave | Impressão digital | O que o router responde | O que faz |
|---|---|---|---|---|
| **novo** | Não há operações com essa chave | — | 202 `recibida` («recebido») | Guarda e distribui, como até a lição 17 |
| **repetido** | Já existe | A mesma | 202 `repetido` | Nada: já o tinha |
| **conflito** | Já existe | Outra | 409 `conflicto` («conflito») | Anota-o na tabela `conflicto`, alerta no log e não sobrescreve o que está guardado |

Embaixo no arquivo está a rota `recibir-momento`, que já usa essa decisão: lê `${variable.visto}` e responde ou
guarda conforme o caso. O trabalho é a rota que preenche essa variável, `reconocer`.

## 2. Faça a previsão

Responda em um papel:

1. A transportadora avisa duas vezes a entrega do pedido 4001, com o mesmo conteúdo. Quantos 214 se esperam no SFTP do
   parceiro? Quantos eventos na API?
2. O segundo aviso traz o mesmo, mas em uma única linha e com os campos em outra ordem. É a mesma mensagem?
3. Chega um terceiro aviso do 4001 com a mesma chave e **outro código de rastreio**. O que deveria acontecer com o código que
   a API e o ERP já têm?

Veja como está hoje:

```bash
./verifica inicio
```

O primeiro aviso passa. O segundo **responde com um erro**: a rota de `inicio/` vê tudo como novo, tenta guardar as
operações outra vez e a `UNIQUE` de `operacion` a detém. Nada foi duplicado, mas a transportadora recebe um erro
por algo que já tínhamos, e como se viu na lição 16, um emissor que recebe erro **manda de novo**. E o aviso
com outro código de rastreio também não se distingue: é mais um erro.

## 3. Reconheça o que chega

Abra `mio/orden-router/reconocer.camel.yaml` e procure a rota `reconocer`. Hoje ela só põe `visto` em `nuevo`. Tem
quatro TODO:

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | Um `to` com `sql:` que devolva duas colunas: `nueva`, a impressão digital do que chegou, e `guardada`, a impressão digital da primeira operação com esse pedido e esse evento do ciclo de vida | Comparar o que chegou com o que já se tem, em uma única consulta |
| 2 | Um `setVariable` `huella` com a impressão digital nova | `guardar-momento` a escreve em cada operação, para comparar da próxima vez |
| 3 | Um `choice` de três ramos que põe `visto` em `nuevo`, `repetido` ou `conflicto` | A decisão que `recibir-momento` lê |
| 4 | No ramo de conflito: um `INSERT INTO conflicto` e um `log` em `WARN` que comece com `CONFLICTO orden-<orden>-<momento>` | Que o conflito fique escrito e alguém o veja |

Quatro dicas:

- A consulta do TODO 1 está quase completa no comentário. Leva `dataSource: "#estado"`, `outputType: SelectOne`
  e `variableReceive: huellas`. Com isso, `${variable.huellas[nueva]}` e `${variable.huellas[guardada]}` são as duas
  impressões digitais.
- Para `guardada`, uma subconsulta: `(SELECT huella FROM operacion WHERE orden = … AND momento = … ORDER BY id
  LIMIT 1)`. Se não há operações, devolve `NULL`.
- O `choice` compara assim: `${variable.huellas[guardada]} == null` é novo; `${variable.huellas[guardada]} ==
  ${variable.huella}` é repetido; o resto (`otherwise`) é conflito.
- A tabela `conflicto` está em `infra/postgres/01-estado.sql`. As colunas são `llave`, `orden`, `momento`,
  `huella_guardada`, `huella_nueva`, `cuerpo` (como `jsonb`, igual à lição 15) e `correlacion`.

Repare na linha da impressão digital:

```sql
encode(sha256(convert_to(CAST(CAST(:#${body} AS jsonb) AS text), 'UTF8')), 'hex')
```

Lê-se de dentro para fora: o corpo é convertido em `jsonb`, o `jsonb` vira texto, o texto vira
bytes, dos bytes se tira o sha256 e o resultado é escrito em hexadecimal. O passo que importa é o
primeiro. **O PostgreSQL guarda o `jsonb` sem os espaços a mais e com as chaves em uma ordem própria**, de modo que dois
corpos com o mesmo conteúdo, escritos de jeitos diferentes, saem iguais ao virar texto. Sem esse `CAST`, a impressão digital seria
a do texto tal como chegou.

## 4. Quebre um de propósito

Com o `reconocer` concluído, tire o `CAST(… AS jsonb)` de dentro: deixe `convert_to(:#${body}, 'UTF8')`. Rode
`./verifica`.

Antes, faça a previsão: o segundo aviso do `verifica` traz o mesmo conteúdo em uma única linha e com os campos invertidos.
Como ele será reconhecido?

Sai em vermelho a marca dos dois avisos: o router respondeu 409 ao segundo. A impressão digital foi tirada do texto, e o texto mudou embora o
conteúdo não. **Um reenvio foi tomado por uma mudança**, e alguém vai ter de conferir um conflito que não existe.
Devolva o `CAST`.

## 5. Confira

```bash
./verifica
```

O `verifica` cria e embarca o pedido 4001, faz o papel da transportadora com o aviso de entrega, manda-o outra vez escrito de outro jeito e
no fim manda um com outro código de rastreio. Está concluída quando aparecem as nove marcas verdes:

```
✅ Arrancaron el router (con su reconocedor de repetidos), los tres canales y el sondeo
✅ Antes de los avisos: la 4001 está EMBARCADO
✅ Los dos avisos contestaron 202, y el segundo se reconoció como repetido aunque viniera escrito distinto
✅ Estado: la entrega tiene 3 operaciones con su huella sha256 y 3 intentos, uno por canal
✅ Destinos: un solo 201 de la entrega en la API, un solo 214 en el socio y el ERP en ENTREGADO
✅ Un aviso con la misma llave y otra guía contestó 409 «conflicto»
✅ Estado: el conflicto quedó en su tabla, con la llave, lo que llegó y las dos huellas distintas
✅ Log: el router avisó el conflicto con un WARN
✅ Nada se pisó: la orden guarda la guía original, sigue habiendo 3 intentos y la API tiene la guía con la que salió
```

Veja a tabela do conflito:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado \
  -c "SELECT llave, cuerpo->>'guia' AS guia, huella_guardada <> huella_nueva AS distintas FROM conflicto"
```

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/reconocer.camel.yaml solucion/orden-router/reconocer.camel.yaml` mostra em que são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `Esperaba 202 y 202 «repetido»` e o segundo respondeu 500 | O `choice` não chegou a pôr `repetido`: confira que a consulta leve `variableReceive: huellas` e que `guardada` busque por pedido **e** evento |
| `Esperaba 202 y 202 «repetido»` e o segundo respondeu 409 | A impressão digital é tirada do texto: falta o `CAST(:#${body} AS jsonb)` de dentro |
| `esperaba 3 operaciones con huella` e há 0 impressões digitais | Falta o TODO 2: a variável `huella` não existe quando `guardar-momento` a escreve |
| `Esperaba 409 para el aviso con otra guía` | O ramo de conflito não põe `visto` em `conflicto`, ou o `choice` o toma por novo |
| `esperaba una fila en conflicto` | Falta o `INSERT INTO conflicto`, ou ele não leva `dataSource: "#estado"` |
| `no hay un WARN «CONFLICTO orden-4001-entregada»` | O `log` não leva `loggingLevel: WARN`, ou a mensagem não começa com `CONFLICTO orden-${variable.orden}-${variable.momento}` |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Projete como se uma mensagem pudesse chegar mais de uma vez.** Em muitos fluxos com novas tentativas, como este, garante-se
que chegue «pelo menos uma vez», não «exatamente uma vez»: as novas tentativas protegem contra a perda, e em troca trazem
repetidos. Quem recebe se encarrega de reconhecê-los. Por isso quase toda API séria de
pagamentos ou de envios pede uma chave de idempotência.

**A chave sai do negócio, não do acaso.** Um número aleatório novo a cada envio faria cada reenvio parecer
outra mensagem. `orden-4001-entregada` é a mesma toda vez que a transportadora reenvia, e por isso serve.

**Chave e impressão digital respondem a perguntas diferentes.** A chave diz se já se viu algo **disto**; a impressão digital, se é **a
mesma coisa**. Só com a chave, uma mudança passa por repetido e se perde. Só com a impressão digital, o mesmo aviso com outra
data de envio passaria por novo.

**Um conflito não é resolvido pelo programa.** Ficar com o primeiro ou com o último em silêncio seria decidir por
alguém. Anota-se, alerta-se e uma pessoa resolve.

## Exercício: o livro da guarita

Com a versão própria concluída:

1. Escreva em papel a chave destas três mensagens: o embarque do 4002, a entrega do 4002 e a entrega do
   4003. Por que não serve como chave um número aleatório que o router gere ao receber?
2. Tire à mão, com `psql`, a impressão digital de `{"orden":"4001","guia":"X"}` e de `{ "guia": "X", "orden": "4001" }`, com e
   sem o `CAST` para `jsonb`. Use `SELECT encode(sha256(convert_to(CAST('…' AS jsonb)::text, 'UTF8')), 'hex')`. Quantas
   impressões digitais diferentes saem em cada caso?
3. No aviso da transportadora, que campo poderia mudar entre um reenvio e outro sem que mude o que aconteceu? (Dica:
   pense em quando o aviso foi **mandado**, não em quando a entrega foi feita.) O que aconteceria com a impressão digital? Escreva a
   resposta: esta lição não a resolve.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Uma mensagem se reconhece pela chave, e o conteúdo dela pela impressão digital](../decisiones/22-la-llave-y-la-huella.md)

## Revisão

1. O que é um Idempotent Consumer? Explique com o exemplo da guarita.
2. O que é a chave de um evento neste curso, e de onde sai?
3. O que é a impressão digital, e por que é tirada depois de converter o corpo em `jsonb`?
4. Por que o repetido é respondido com 202 e não com um erro? (Volte à lição 16.)
5. **A pergunta de encerramento:** um repetido e um conflito têm a mesma chave. O que os distingue, o que o
   router responde a cada um e por que o conflito não sobrescreve o que está guardado?

**A seguir:** na lição 19 o problema é o contrário. Não chega duas vezes: não chega. A API não responde, e o
router tem de tentar de novo sem tirar de ordem os eventos do pedido.
