# Lição 03 — Traduzir o pedido

> **O que será alcançado:** escrever à mão o tradutor que converte o pedido da loja (`orden`) no pedido da API (`pedido`),
> conferi-lo com casos de teste antes de rodar a rota, e descobrir por que um computador não soma centavos como
> uma pessoa.

**Antes de começar é preciso ter:** a lição 02 concluída e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

Na lição 02 o tradutor já vinha pronto. Hoje ele é escrito à mão, e a API agora pede mais: em cada item, o **valor**
(quantidade × preço), e no pedido, o **total**. Se o total não bate **até o centavo** com os itens, a API o
rejeita.

**O tradutor se chama mapeamento** e está escrito em **JSONata**, uma linguagem pequena para transformar um JSON em outro.
Primeiro se lê, depois se escreve: quase tudo se entende de primeira.

---

## 1. Prepare a pasta

```bash
cd lecciones/03-traducir-la-orden
cp -R inicio mio
```

Desta vez **fica-se na pasta da lição**, sem entrar em `mio/`: a ferramenta de testes roda a partir daqui.

## 2. Leia o mapeamento já conhecido

Abra `mio/mappings/api/pedido/v1/mapeo.jsonata`. É o da lição 02:

```
{
  "pedido": orden,
  "tienda": tienda,
  "fecha": creada,
  "partidas": [renglones.{
    "posicion": renglon,
    "sku": sku,
    "cantidad": cantidad,
    "precio": precio
  }]
}
```

Lê-se assim: **à esquerda, o nome que a API quer; à direita, de onde ele sai no pedido da loja.**
`"pedido": orden` quer dizer «o campo `pedido` vale o que o campo `orden` trouxer».

A linha estranha é `renglones.{ … }`. O ponto quer dizer **«para cada um»**: para cada linha do pedido, monta
um item com estes campos. Os colchetes de fora garantem que sempre saia uma lista, mesmo que o pedido traga
uma única linha.

Por que o arquivo vive em uma pasta `v1`? Porque hoje a `v2` será escrita **ao lado**, sem mexer na v1. Se
algo der errado, a rota pode voltar à versão anterior trocando um único número.

## 3. Leia os casos de teste de referência

Abra a pasta `mio/mappings/api/pedido/v2/cases/`. Tem três casos, e cada um é um par de arquivos:

| Arquivo | O que é |
|---|---|
| `entrada.json` | Um pedido da loja |
| `esperado.json` | Exatamente o que o mapeamento tem de produzir com esse pedido |

A isto se chama **casos de teste de referência**: o resultado correto, escrito antes de programar. São o controle do erro
desta lição: não é preciso perguntar a ninguém se o mapeamento ficou certo, os casos dizem.

Abra `cases/dos-renglones/esperado.json` e compare com o que a v1 produz. Que campos são novos?

## 4. Faça a previsão

Abra `cases/centavos/entrada.json`. É o pedido 1003, de uma papelaria:

| Linha | Quantidade | Preço |
|---|---|---|
| Clipe | 3 | 0.10 |
| Elástico | 7 | 0.70 |
| Caderno | 1 | 19.99 |

Antes de seguir, escreva em um papel:

1. Quanto é o valor do clipe (3 × 0.10)?
2. Qual é o total do pedido?

Guarde as respostas: elas serão necessárias no passo 6.

## 5. Escreva a v2

Abra `mio/mappings/api/pedido/v2/mapeo.jsonata`. Só tem algumas instruções e umas chaves vazias `{ }`.

1. **Copie para lá o conteúdo da v1**, sem o comentário de cima.
2. **Acrescente o valor** em cada item. Em JSONata a multiplicação é `*`, e dentro de `renglones.{ … }` já
   se está em uma linha do pedido, então `cantidad` e `precio` são os dessa linha.
3. **Acrescente o total** do pedido, depois de `"partidas"`. O JSONata tem uma função `$sum( … )` que soma uma
   lista de números. Dica: `renglones.(cantidad * precio)` produz a lista de valores.

Com isso pronto, teste contra os casos de teste de referência, a partir da pasta da lição:

```bash
../../herramientas/probar-mapeo mio/mappings/api/pedido/v2
```

A ferramenta roda o mapeamento com cada `entrada.json` e compara o resultado com o `esperado.json` correspondente. No fim diz
`Casos: 3 · pasan … · fallan …`. Se um caso falha, ela mostra o que obteve; abra-o ao lado do `esperado.json` e procure a diferença.

Na primeira vez demora mais, porque o Camel baixa o que precisa. Espere até aparecer a linha dos casos.

## 6. Compare com a previsão

Se o valor foi escrito como `cantidad * precio`, o mais provável é que **o caso `centavos` tenha falhado**. Veja
o que ele obteve:

```
"importe": 0.30000000000000004      ← el clip
"importe": 4.8999999999999995       ← la liga
"total": 25.189999999999998
```

A previsão era 0.30, e estava certa. O computador, não. **Ele não guarda os decimais como são escritos:** guarda-os
em binário, e 0.10 em binário não tem um final exato, assim como 1/3 em decimal é 0.3333… sem fim. Ao multiplicar,
aparece esse pedacinho que sobra.

Para uma pessoa, 0.30000000000000004 e 0.30 são a mesma coisa. **Para a API, não:** ela compara o total com a soma
dos itens até o centavo, e se não batem rejeita o pedido com um **422**: `El total 25.189999999999998 no cuadra
con las partidas, que suman 25.19` («o total 25.189999999999998 não bate com os itens, que somam 25.19»). Em um sistema de vendas real, um centavo
de diferença é uma fatura que não fecha.

A solução é **arredondar para duas casas decimais** com `$round(numero, 2)`. Aplique-a no valor e no total, e teste
de novo até aparecer:

```
Casos: 3 · pasan 3 · fallan 0
```

Dá para ver por que os casos de teste de referência se escrevem antes de programar? Em uma comparação «a olho», 0.30000000000000004
parece 0.30 e o erro teria ido até a API.

## 7. Mude a rota para a v2

O mapeamento novo existe, mas a rota ainda usa o velho. Abra `mio/orden-a-api.camel.yaml` e procure a linha:

```yaml
uri: jsonata:mappings/api/pedido/v1/mapeo.jsonata
```

Mude-a para que aponte para a v2. É a única mudança da rota.

Para vê-la rodar, faça o mesmo que na lição 02: a partir de `mio/`, inicie a rota com
`../../../herramientas/camel run orden-a-api.camel.yaml` e em outro terminal mande o pedido 1003
(`-d @../../../datos/ordenes/orden-1003.json`). Depois pergunte à API: `curl localhost:8081/pedidos/1003`.

## 8. Confira

Pare a rota com `Ctrl+C` se ela ficou rodando e, a partir da pasta da lição:

```bash
./verifica
```

Está concluída quando aparecem as quatro marcas verdes:

```
✅ El mapeo v2 pasa todos sus casos dorados
✅ La ruta usa la versión 2 del mapeo
✅ La API aceptó la orden 1003 (201)
✅ El pedido quedó con total 25.19
```

As duas primeiras conferem o trabalho feito; as duas últimas quem confirma é **a API**. Se o mapeamento passasse nos casos mas a
rota continuasse na v1, a API receberia um pedido sem total.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/mappings/api/pedido/v2/mapeo.jsonata solucion/mappings/api/pedido/v2/mapeo.jsonata` mostra em que são
diferentes.

| O que aparece | O que fazer |
|---|---|
| `Casos: 3 · pasan 2 · fallan 1` em `centavos` | Falta arredondar: volte ao passo 6 |
| `No hay casos en …` | A pasta foi escrita errado: deve terminar em `pedido/v2` |
| `La API no aceptó la orden 1003: llegó 422` | O total não bate até o centavo: confira os `$round` do passo 6 |
| `El pedido quedó con total ninguno` | A rota continua na v1, que não manda total: volte ao passo 7 |
| `El puerto 8080 está ocupado` | Ficou uma rota rodando: pare-a com `Ctrl+C` |

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [JSONata para traduzir entre formatos](../decisiones/03-jsonata-para-traducir.md)
- [Sem casos de teste de referência não há mapeamento](../decisiones/04-casos-dorados.md)

## Revisão

1. Em `"pedido": orden`, que lado é a API e que lado é a loja?
2. O que faz o ponto em `renglones.{ … }`?
3. O que é um caso de teste de referência, e por que se escreve antes do mapeamento?
4. Por que 3 × 0.10 não dá exatamente 0.30 em um computador?
5. Para que serve a v2 viver ao lado da v1 e não por cima?

**A seguir:** na lição 04 o pedido chega a um sistema que não fala JSON: o ERP, que guarda tudo em tabelas.
