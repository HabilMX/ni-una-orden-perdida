# Lição 15 — O estado vive fora do processo

> **O que será alcançado:** que o router **guarde** cada pedido na própria base antes de responder à loja,
> com uma linha por canal e um registro de cada tentativa, e que com isso um pedido não se perca mesmo que um destino
> esteja fora do ar.

**Antes de começar é preciso ter:** a lição 14 concluída, o ambiente da 00 ligado (a base de estado,
`estado`, é a que foi ligada desde o primeiro dia e até hoje não era usada) e a resposta à pergunta de
encerramento da 12.

## A novidade de hoje, em uma frase

Até a 14, o router recebia o pedido, chamava os três canais, esperava e respondia à loja o que
cada um disse. Se o router caía no meio, ninguém sabia que destinos já tinham o pedido. A partir de hoje o router
**primeiro anota e depois distribui**: guarda o pedido no PostgreSQL, responde à loja **202 `recibida`** («recebido») e,
já com a loja tranquila, sai para distribuir.

**A imagem:** o guichê de uma transportadora. O pacote é recebido, registrado no sistema, e o cliente leva o
comprovante. Ninguém fica no balcão até o pacote chegar ao destino. O comprovante vale porque **o pacote já
ficou registrado**, não porque o funcionário se lembre do cliente. Se o turno muda, quem chega lê o registro.

Volte à resposta da 12: «se o router tivesse réplicas, onde viveria o que diz respeito a cada pedido?». Hoje ela é
respondida: **fora do processo, em uma base**. Assim qualquer réplica do router pode lê-lo, e se uma cair, o
que foi anotado continua lá.

---

## 1. Prepare a pasta

```bash
cd lecciones/15-el-estado-vive-fuera-del-proceso
cp -R inicio mio
```

O router agora tem três arquivos. Abra-os nesta ordem:

| Arquivo | O que faz | É para escrever? |
|---|---|---|
| `orden-router.camel.yaml` | Recebe o pedido ou o embarque, manda guardá-lo, responde 202 e enfileira a distribuição | Não, só leia |
| `estado.camel.yaml` | Guarda na base. Aqui está `guardar-orden`, o trabalho de hoje | **Sim** |
| `reparto.camel.yaml` | Lê da base o que está pendente, anota a tentativa, chama o canal e anota como foi | Não, só leia |

E abra o esquema da base, `infra/postgres/01-estado.sql`. Hoje importam três tabelas:

| Tabela | Uma linha por… | O importante |
|---|---|---|
| `orden` | pedido | Em que evento do ciclo de vida ele está (`creada`, `embarcada`, `entregada`) e os `datos` dele, o pedido tal como chegou |
| `operacion` | pedido, evento e canal | O que é preciso fazer em cada destino e como ficou (`estado`: `pendiente`, `aceptada`, `rechazada`, `no_se_sabe`) |
| `intento` | vez em que se tentou uma operação | A impressão digital do que saiu, a versão do mapeamento e o desfecho |

Um pedido de franquia, ao ser criado, deixa **uma** linha em `orden` e **três** em `operacion`: `rest`, `bd` e `edi`.

## 2. Faça a previsão

Responda em um papel:

1. Hoje o router responde 202 embora `guardar-orden` não guarde nada. O que deve acontecer com o pedido depois do
   202? (Dica: a distribuição lê da base o que está pendente.)
2. Se o ERP está desligado quando o pedido chega, o que o router deveria responder à loja? E o que deveria
   ficar na base?

Veja como está hoje:

```bash
./verifica inicio
```

A loja recebe o 202, e depois não acontece nada: a base não tem o pedido e nenhum destino o recebe. **O router
prometeu algo que não cumpriu.** É o pior erro deste curso, porque a loja acredita que tudo deu certo.

## 3. Guarde antes de responder

Abra `mio/orden-router/estado.camel.yaml` e procure `guardar-orden`. Tem três TODO. O modelo está logo abaixo:
`guardar-momento` faz o mesmo para o embarque. Leia-o inteiro antes de escrever.

| TODO | O que escrever | Para quê |
|---|---|---|
| 1 | `transacted` com `ref: todoONada` | Que o pedido e as operações dele fiquem **todos ou nenhum** (a mesma ideia da 05, agora no estado) |
| 2 | Um `to` com `sql:INSERT INTO orden (orden, momento, datos) VALUES (…)` | A linha do pedido: o número, o evento `'creada'` e o pedido completo como `jsonb` |
| 3 | Um `split` sobre `${variable.canales.split(',')}` com um `INSERT INTO operacion …` dentro | Uma operação por canal, em `'pendiente'`, com o fio dela. A do parceiro, com o número de controle |

Três dicas:

- As variáveis necessárias já existem quando esta rota é chamada: `orden`, `canales` e `correlacion`. O corpo é
  o pedido como a loja o mandou.
- Para guardar o corpo como `jsonb`: `CAST(:#${body} AS jsonb)`. `:#${…}` é como o componente `sql` passa um
  valor como parâmetro, sem colá-lo no texto do SQL.
- O `INSERT INTO operacion` de `guardar-momento` serve quase igual: o que muda é o evento.

Repare na linha do número de controle:
`CASE WHEN :#${body} = 'edi' THEN CAST(nextval('numero_control_seq') AS text) END`. Só a operação do parceiro
pede um número à sequência. **A partir de hoje o número de controle do X12 é atribuído pelo router**, não sai do número do
pedido como na 06 e na 13.

## 4. Quebre um de propósito

Com o `guardar-orden` concluído, desligue a base de estado e mande um pedido ao router:

```bash
DONDE=mio docker compose -f compose.yaml up -d
docker compose -f ../../infra/compose.yaml stop estado
curl -i -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../datos/ordenes/tramo-2/orden-4003.json
```

Antes de mandá-lo, faça a previsão: o router responde 202?

Não responde: devolve um erro. Sem base ele não pode guardar, e **sem guardar não promete**. A loja vê o erro e sabe
que tem de tentar de novo. É exatamente o contrário de `inicio/`, que respondia 202 sem guardar. Ligue
tudo de novo:

```bash
docker compose -f ../../infra/compose.yaml start estado
DONDE=mio docker compose -f compose.yaml down
```

## 5. Confira

```bash
./verifica
```

O `verifica` manda o 4001 e o embarque dele, confere a base e os destinos, e no final desliga o ERP e manda o 4002. Está
concluída quando aparecem as doze marcas verdes:

```
✅ Arrancaron el router (con su base de estado), los tres canales y el sondeo
✅ La tienda mandó la 4001 y el router contestó 202 «recibida»
✅ Estado: una fila de la orden 4001, con sus datos
✅ Estado: tres operaciones de la 4001, una por canal, y las tres aceptadas
✅ Estado: cada operación tiene su intento, con huella sha256, versión del mapeo y desenlace
✅ Destinos: la API tiene la 4001 CREADO y el ERP REGISTRADO
✅ Socio: el 850 de la 4001 lleva en el ISA el número de control de la base (…)
✅ Estado: el embarque de la 4001 tiene sus tres operaciones aceptadas y la orden va en «embarcada»
✅ Socio: el 856 lleva su propio número de control (…), distinto del 850 (…)
✅ Destinos: la API tiene la 4001 EMBARCADO
✅ Con el ERP caído, la tienda recibió su 202 por la 4002
✅ Estado: la operación del ERP quedó sin aceptar y está guardada (…); las otras dos siguieron
```

Os números de controle mudam a cada execução: quem os dá é a sequência.

Veja as duas últimas. Com o ERP fora do ar, a loja recebeu, **sim**, o 202, porque o router guardou, e o que não saiu
ficou anotado: a operação `bd` do 4002 está em `no_se_sabe` ou `rechazada`, enquanto a API e o parceiro já o
têm. Na 14 isso se perdia em um log. Hoje está em uma linha que pode ser consultada.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/estado.camel.yaml solucion/orden-router/estado.camel.yaml` diz em que elas são diferentes.

| O que aparece | O que fazer |
|---|---|
| `no está la fila de la orden 4001` | Falta o INSERT de `orden`, ou ele não leva `dataSource: "#estado"` |
| `esperaba «bd=aceptada edi=aceptada rest=aceptada» y hay «nada»` | Falta o `split` das operações, ou os INSERT ficaram fora dele |
| `column "datos" is of type jsonb but expression is of type…` | Faltou o `CAST(:#${body} AS jsonb)` |
| `el 850 … no lleva el número de control de la base` | A operação `edi` foi guardada sem `numero_control`: confira o `CASE WHEN` |
| `El puerto 8080 está ocupado` | Os serviços do passo 4 ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Responder «recebido» é uma promessa, e ela só se cumpre se já ficou escrito.** Por isso o router guarda primeiro. É
o mesmo que um banco faz com uma transferência: dá o protocolo quando ela já está no livro dele, não quando já chegou.

**O estado fora do processo é o que deixa o router crescer.** Um processo que lembra na própria memória não pode ser
replicado: cada réplica lembraria coisas diferentes, e ao reiniciar esqueceria. Com a base, qualquer réplica lê o
mesmo.

**Separar «recebi» de «entreguei» muda a conversa.** A loja já não espera os três destinos responderem.
Em troca, alguém tem de olhar o que ficou pendente: é isso que a parte 3 constrói.

## Exercício: o pedido em três tabelas

Com a versão própria concluída, rode `./verifica` e entre na base:

```bash
docker compose -f ../../infra/compose.yaml exec estado psql -U router -d estado
```

1. No papel, **desenhe** o pedido 4001 nas três tabelas: uma caixa para a linha dele em `orden`, uma para cada `operacion`
   (criação e embarque) e uma para cada `intento`, com setas de qual aponta para qual.
2. Escreva a consulta que diz, para o 4002, em que estado está cada canal. Comece com
   `SELECT canal, estado FROM operacion WHERE orden = '4002'` e acrescente o evento.
3. Que número de controle tem o 850 do 4001, e qual ele tinha na 06? De onde sai cada um, e por que um
   número dado pela base não se repete mesmo que dois pedidos cheguem ao mesmo tempo?

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [Guardar antes de responder](../decisiones/18-guardar-antes-de-contestar.md)
- [O número de controle do X12 é atribuído pelo router](../decisiones/19-el-numero-de-control-lo-asigna-el-router.md)

## Revisão

1. O que cada uma das três tabelas guarda, `orden`, `operacion` e `intento`?
2. Por que o router guarda o pedido **antes** de responder 202, e não depois?
3. Para que serve a transação em `guardar-orden`? O que aconteceria sem ela se o segundo INSERT falhasse?
4. Com o ERP fora do ar, o que a loja recebeu e o que ficou na base? O que acontecia com esse mesmo pedido na 14?
5. **A pergunta de encerramento:** a partir de hoje o número de controle do X12 é atribuído pelo router. De onde ele saía na 06,
   e o que mudou? Compare com a resposta da 12 sobre onde vive o que diz respeito a cada pedido.

**A seguir:** na lição 16 chega o terceiro evento: a transportadora avisa que entregou. Ninguém pergunta a ela: ela chama
sozinha, por um webhook. O que tem de acontecer no estado e no SFTP?
