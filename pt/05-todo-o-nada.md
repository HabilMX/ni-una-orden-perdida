# Lição 05 — Tudo ou nada

> **O que será alcançado:** que, quando uma linha do pedido estiver errada, o ERP não fique com um pedido pela metade:
> ou se guarda tudo, ou não se guarda nada. Consegue-se acrescentando **um único passo** à rota da lição 04.

**Antes de começar é preciso ter:** a lição 04 concluída e o ambiente da 00 ligado:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## A novidade de hoje, em uma frase

Na lição 04 a rota escreve no ERP em vários passos: o cabeçalho, depois cada linha e no fim o procedimento.
Se um passo do meio falha, os anteriores **já ficaram escritos**. Hoje todos esses passos são envolvidos em uma
**transação**: um pacote que o ERP confirma inteiro ou desfaz inteiro.

**A imagem:** uma transferência bancária. Subtrai-se de uma conta e soma-se à outra. Se o banco fizesse a
subtração e depois a soma falhasse, o dinheiro desapareceria. Por isso as duas operações vão juntas: ou acontecem as duas, ou
não acontece nenhuma.

| Palavra | O que significa |
|---|---|
| **Transação** | Um grupo de escritas que a base trata como uma só |
| **Confirmar** (*commit*) | Ao terminar bem, a base guarda o grupo inteiro de uma vez |
| **Desfazer** (*rollback*) | Se algo falha, a base apaga tudo o que o grupo havia escrito, como se nunca tivesse acontecido |

---

## 1. Prepare a pasta

```bash
cd lecciones/05-todo-o-nada
cp -R inicio mio
```

`inicio/` é a rota da lição 04 já concluída, com split e `CALL` incluídos, mais três peças novas que vêm prontas.

## 2. Conheça o pedido que vai falhar

Abra `datos/ordenes/orden-1004-renglon-invalido.json`, na pasta raiz do curso. Tem duas linhas:

| Linha | Produto | Quantidade | Preço |
|---|---|---|---|
| 1 | Xícara branca | 1 | 89.50 |
| 2 | Toalha de mesa azul | **0** | 249.00 |

O ERP não aceita quantidade 0: a tabela `pedido_detalle` tem uma regra (`CHECK (cantidad > 0)`) que rejeita essa
linha. É como um sistema real, que não deixa registrar «zero toalhas de mesa».

## 3. Faça a previsão

A rota escreve primeiro o cabeçalho, depois a linha 1 e depois a linha 2. Responda em um papel:

1. Quando chegar o pedido 1004, em que passo a rota falha?
2. No fim, o que fica em `pedido_cabecera` e o que fica em `pedido_detalle` para o pedido 1004?
3. Com que estatus fica o cabeçalho?

## 4. Confira na prática

Rode o `verifica` com a rota tal como vem, sem mudar nada:

```bash
./verifica
```

Vão sair **duas marcas vermelhas**:

```
❌ Quedó la cabecera de la 1004 sin su detalle completo: falta la transacción
❌ Quedaron renglones sueltos de la 1004
```

Pergunte ao ERP o que ficou:

```bash
docker compose -f ../../infra/compose.yaml exec erp \
  mariadb -uintegracion -ppractica erp -e "SELECT * FROM pedido_cabecera; SELECT * FROM pedido_detalle WHERE pedido='1004';"
```

O cabeçalho do 1004 está lá, em `RECIBIDO`, com um total de 89.50, e tem **uma** linha: a xícara. A toalha de mesa
nunca entrou e o procedimento nunca rodou. É um pedido que o ERP acredita que existe, que ninguém vai atender e que
alguém terá de encontrar e apagar à mão.

A previsão acertou? Repare que a loja ficou sabendo do erro (a rota respondeu um 500), mas isso não limpa o que
já havia sido escrito.

## 5. Leia as peças novas

Abra `mio/orden-al-erp.camel.yaml`. Acima, junto à conexão `erp` já conhecida, há dois beans novos:

| Bean | O que faz | Na imagem do banco |
|---|---|---|
| `transacciones` (um `DataSourceTransactionManager`) | Abre, confirma ou desfaz a transação sobre a conexão `erp` | O caixa que conduz a operação |
| `todoONada` (uma `SpringTransactionPolicy`) | A regra que será usada na rota: «se já há uma transação aberta, entre nela; se não, abra uma» (`PROPAGATION_REQUIRED`) | A política do banco: as duas operações vão juntas |

E em `mio/dependencias.txt` há mais duas peças que o Camel baixa ao iniciar: `camel-spring` e `spring-jdbc`. São
as que sabem lidar com transações. Não se escreve Java: tudo é declarado no YAML.

## 6. Acrescente o passo

Procure na rota o comentário que diz **TE TOCA** («a parte de quem faz a lição»). Ali vai um único passo:

```yaml
- transacted:
    ref: todoONada
```

`transacted` quer dizer: **daqui até o fim da rota, tudo acontece dentro de uma transação**, com a
regra `todoONada`. Se qualquer passo de baixo falhar, desfaz-se tudo o que foi escrito a partir deste ponto.

Repare em **onde** ele vai: antes de escrever o cabeçalho. O que aconteceria se fosse colocado depois do `INSERT` do
cabeçalho?

Este padrão tem nome nos EIP: **Transactional Client**. A rota não só manda mensagens: pede ao destino
que trate várias escritas como uma só.

## 7. Confira

```bash
./verifica
```

⚠️ Assim como na lição 04, o `verifica` **esvazia as tabelas do ERP** antes de testar.

Está concluída quando aparecem as seis marcas verdes:

```
✅ El mapeo al ERP pasa sus casos dorados
✅ La ruta arrancó
✅ La orden 1004 (con un renglón de cantidad 0) fue rechazada (500)
✅ No quedó la cabecera de la 1004: se deshizo
✅ No quedó ningún renglón de la 1004
✅ La orden buena 1001 sí quedó completa y REGISTRADO
```

A última importa tanto quanto as outras: a transação não deve atrapalhar os pedidos bons.

Consulte de novo o ERP como no passo 4. Agora do 1004 não fica nada: nem cabeçalho, nem xícara.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-al-erp.camel.yaml solucion/orden-al-erp.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| As mesmas duas marcas vermelhas do passo 4 | O passo `transacted` não está lá, ou está depois do cabeçalho |
| `La ruta no arrancó` | Confira a indentação do passo: `transacted` vai no mesmo nível dos demais passos |
| `El puerto 8080 está ocupado` | Ficou uma rota rodando: pare-a com `Ctrl+C` |

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [No ERP, tudo ou nada](../decisiones/06-todo-o-nada-en-el-erp.md)

## Revisão

1. O que é uma transação, e o que significam confirmar e desfazer?
2. Sem transação, o que ficou no ERP com o pedido 1004, e por que é um problema?
3. Por que o passo `transacted` vai antes do `INSERT` do cabeçalho?
4. Para que serve cada um dos dois beans novos?
5. A loja recebeu um 500. O que ela deveria fazer com esse pedido? *(Ainda não há resposta: na terceira parte do
   curso, «Que não falhe», será decidido o que se tenta de novo e o que se separa.)*

**A seguir:** na lição 06 o pedido viaja para o parceiro comercial, que não recebe JSON nem tabelas: recebe um arquivo
EDI, um formato de texto que as empresas usam há décadas para falar entre si.
