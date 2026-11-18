# Lição 04 — O pedido chega ao ERP

> **O que será alcançado:** registrar o pedido no ERP da empresa, que não recebe JSON e sim linhas em tabelas:
> um cabeçalho, uma linha de detalhe para cada item, e no fim um procedimento que confere se tudo bate.

**Antes de começar é preciso ter:** a lição 03 concluída e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

Até agora o destino era uma API que recebe um JSON completo de uma só vez. O ERP é diferente: guarda a
informação em **tabelas**, como planilhas, e cada pedido ocupa **várias linhas em duas tabelas**. Hoje a rota
divide o pedido em pedaços e o escreve linha por linha.

**Três palavras que serão usadas:**

| Palavra | O que é | Uma imagem |
|---|---|---|
| **ERP** | O sistema central onde a empresa registra o que vende, compra e fatura | O livro de contabilidade da empresa |
| **SQL** | A linguagem para falar com um banco de dados: `INSERT` acrescenta uma linha, `SELECT` as consulta | As instruções ditadas a quem cuida do livro |
| **Procedimento armazenado** | Um programa que vive **dentro** da base e é chamado com `CALL` | O contador que confere a página antes de assiná-la |

---

## 1. Prepare a pasta

```bash
cd lecciones/04-al-erp
cp -R inicio mio
```

## 2. Conheça as tabelas do ERP

O ERP tem duas tabelas. Assim ficaria o pedido 1001 já registrado:

**`pedido_cabecera`** — uma linha por pedido

| pedido | tienda | fecha | total | estatus |
|---|---|---|---|---|
| 1001 | T-014 | 2026-11-12 10:15:00 | 428.00 | REGISTRADO |

**`pedido_detalle`** — uma linha para cada item do pedido

| pedido | posicion | sku | cantidad | precio |
|---|---|---|---|---|
| 1001 | 1 | SKU-0001 | 2 | 89.50 |
| 1001 | 2 | SKU-0042 | 1 | 249.00 |

A coluna `pedido` do detalhe é o que **une** cada linha ao cabeçalho dela. Por isso o cabeçalho é escrito
primeiro: o ERP não aceita uma linha de detalhe de um pedido que ainda não existe.

E o `estatus` começa em `RECIBIDO`. Só muda para `REGISTRADO` quando o procedimento `registrar_pedido` confere
que a soma do detalhe é igual ao total do cabeçalho. Para ver como ele é feito, está em
`infra/mariadb/01-erp.sql`; não é preciso mudá-lo.

## 3. Leia o mapeamento e a rota

**O mapeamento já vem pronto:** `mio/mappings/erp/pedido/v1/mapeo.jsonata`. Converte o pedido em um JSON com duas
partes, `cabecera` e `detalle`, que têm exatamente os nomes das colunas. Teste-o como na lição 03:

```bash
../../herramientas/probar-mapeo mio/mappings/erp/pedido/v1
```

Repare na data. A loja manda `2026-11-12T10:15:00-06:00` e o mapeamento a deixa como `2026-11-12 10:15:00`. Ele
tira o fuso horário (`-06:00`) porque a coluna do ERP é do tipo `DATETIME`, que guarda data e hora **sem fuso**.
O acordo com o ERP é que essa hora é a da loja; o tipo de dado não sabe disso, quem sabe é quem o lê.

**A rota:** abra `mio/orden-al-erp.camel.yaml`. Acima da rota há um **bean** chamado `erp`: é a conexão com
a base, com endereço, usuário e senha. Os passos `sql:` o usam com `dataSource: "#erp"`.

A rota já faz isto:

```
POST /ordenes  →  traducir  →  guardar cabecera y detalle en variables  →  INSERT de la cabecera  →  (te toca)
```

Veja o `INSERT` do cabeçalho:

```yaml
uri: sql:INSERT INTO pedido_cabecera (pedido, tienda, fecha, total) VALUES (:#pedido, :#tienda, :#fecha, :#total)
```

Cada `:#nombre` é uma lacuna que o Camel preenche com o campo desse nome **que o corpo da mensagem traz**. Por isso,
logo antes, a rota coloca o cabeçalho como corpo com `setBody`.

Mais uma coisa: o driver do MariaDB, a peça que ensina o Camel a falar com essa base, não vem incluído.
Quem o pede é o arquivo `dependencias.txt`, e `herramientas/camel` o baixa ao iniciar.

## 4. Faça a previsão

A rota de `inicio/` só escreve o cabeçalho. Antes de completá-la, responda em um papel:

1. Se o pedido 1001 for enviado com a rota tal como está, que linhas ficam em cada tabela, e com que estatus?
2. Se o `CALL registrar_pedido` fosse acrescentado **sem** escrever o detalhe, o que o procedimento responderia?

A primeira pode ser conferida agora mesmo. A partir de `mio/`, inicie a rota:

```bash
cd mio
../../../herramientas/camel run orden-al-erp.camel.yaml
```

Em outro terminal, a partir da mesma pasta `mio/`, mande o pedido e pergunte ao ERP:

```bash
curl -XPOST localhost:8080/ordenes -H 'Content-Type: application/json' -d @../../../datos/ordenes/orden-1001.json

docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle;"
```

Compare as duas respostas. **A rota** respondeu à loja `{"pedido":"1001","erp":"REGISTRADO"}`. **O ERP**
diz outra coisa: o cabeçalho está em `RECIBIDO` e o detalhe está vazio. A rota não mente de propósito: o último
passo dela escreve essa resposta sem perguntar nada ao ERP. Por isso neste curso o que conta é o que o
destino guardou, nunca o que a rota diz.

Um pedido com cabeçalho e sem detalhe é um pedido pela metade. Para o ERP ele existe, mas não pode ser atendido nem faturado.

## 5. Escreva o detalhe e o fechamento

Pare a rota com `Ctrl+C`. Em `mio/orden-al-erp.camel.yaml` há dois comentários que dizem **TE TOCA** («a parte de quem faz a lição»).

**O detalhe.** A lista de linhas está em `${variable.detalle}`, e é preciso um `INSERT` para cada uma. Para isso
existe o passo **`split`**: pega uma lista, divide-a nos elementos dela e roda os próprios passos uma vez para cada elemento,
com esse elemento como corpo.

```yaml
- split:
    expression:
      simple:
        expression: "${variable.detalle}"
    steps:
      - to:
          uri: sql:INSERT INTO pedido_detalle (…) VALUES (…)
          parameters:
            dataSource: "#erp"
```

Complete as colunas e as lacunas. Use o `INSERT` do cabeçalho como modelo; as colunas do detalhe estão
na tabela do passo 2.

**O fechamento.** Depois do `split`, chame o procedimento:

```yaml
uri: sql:CALL registrar_pedido(:#pedido)
```

Atenção ao corpo: depois do `split`, o que ele traz? A lacuna `:#pedido` precisa de um corpo que tenha o campo
`pedido`. Que passo da rota já fez isso antes do `INSERT` do cabeçalho?

Este padrão tem nome nos EIP: **Splitter**. Um pedido com vários itens é dividido em mensagens menores,
uma por item, porque é assim que o destino as recebe.

## 6. Compare com a previsão

Rode a rota outra vez e mande o pedido 1001. Antes, esvazie o ERP para começar limpo:

```bash
docker compose -f ../../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "DELETE FROM pedido_detalle; DELETE FROM pedido_cabecera;"
```

Consulte as duas tabelas como no passo 4. Agora o cabeçalho deve dizer `REGISTRADO` e o detalhe ter duas linhas.

E a pergunta 2? Se o procedimento é chamado sem detalhe, responde `El total no cuadra con el detalle` («o total não bate com o detalhe»): a soma de
zero linhas é 0, e o total é 428. **O ERP não confia no que recebe: confere.** Assim como a API na lição
03, um destino que valida é o que faz um erro aparecer.

## 7. Confira

Pare a rota com `Ctrl+C` e, a partir da pasta da lição:

```bash
cd ..
./verifica
```

⚠️ O `verifica` **esvazia as tabelas do ERP** antes de testar: é um ERP de prática e assim começa do zero a cada vez.

Está concluída quando aparecem as seis marcas verdes:

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ El ERP tiene las 2 cabeceras
✅ El pedido 1001 tiene sus 2 renglones de detalle
✅ El pedido 1003 tiene sus 3 renglones de detalle
✅ El procedimiento marcó los 2 pedidos REGISTRADO
```

O `verifica` manda dois pedidos, o 1001 e o dos centavos (1003), e pergunta ao ERP o que ele guardou. Como sempre, quem
confirma é o destino, não a rota.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `Al pedido 1001 le faltan renglones de detalle` | O `split` não roda ou falta uma lacuna: confira se ele percorre `${variable.detalle}` |
| `Hay pedidos sin REGISTRADO` | Falta o `CALL`, ou o corpo não traz `pedido`: confira o passo 5 |
| `No pude hablar con el ERP: ¿corriste la 00?` | Ligue o ambiente: `docker compose -f infra/compose.yaml up -d --wait` |
| `El puerto 8080 está ocupado` | Ficou uma rota rodando: pare-a com `Ctrl+C` |

---

## Por que foi feito assim

Hoje o pedido foi escrito em três passos soltos. A ficha que explica por que isso não basta, e o que se
decidiu, é a da lição seguinte: [No ERP, tudo ou nada](../decisiones/06-todo-o-nada-en-el-erp.md).

## Revisão

1. Por que um pedido ocupa linhas em duas tabelas, e que coluna as une?
2. Por que o cabeçalho é escrito antes do detalhe?
3. O que faz um `split`, e o que o corpo traz dentro de cada volta?
4. De onde o Camel tira o valor de `:#sku`?
5. O que `registrar_pedido` confere antes de marcar um pedido como `REGISTRADO`?

**A seguir:** o que acontece se uma linha do meio está errada e o ERP a rejeita? O cabeçalho já ficou escrito e o
detalhe pela metade. Na lição 05 o ERP passará a guardar **tudo ou nada**.
