# Lição 21 — Enviado não é confirmado

> **O que será alcançado:** que o canal do parceiro não se dê por concluído quando o 850 fica no SFTP dele, e sim quando
> o parceiro responde com a confirmação de recebimento, o **997**. Essa confirmação chega depois, em outro arquivo e sem o número do pedido: o
> trabalho é saber a que envio ela responde.

**Antes de começar é preciso ter:** as lições 20, 09 (o fio de correlação) e 06 (ler X12) concluídas, e o
ambiente da 00 ligado. Esta lição usa um serviço novo do ambiente, `socio-bot`, que faz o papel de sistema do
parceiro e responde os 997. Se ele não estiver no ar:

```bash
docker compose -f infra/compose.yaml up -d --wait
```

## A novidade de hoje, em uma frase

Com a API e o ERP, a resposta chega na mesma chamada: pergunta-se, vem a resposta, e se sabe. Com o parceiro não. O
router deixa o 850 em uma pasta do SFTP e aí termina a chamada. **O arquivo estar lá diz que foi
depositado, não que o parceiro o leu nem que o entendeu.** O parceiro responde quando o sistema dele processa o arquivo:
segundos depois neste curso, horas na vida real. E responde por outro caminho, deixando um 997 na pasta
`salida/` dele.

**A imagem:** deixa-se um envelope na caixa de correio de uma empresa. A caixa não diz se alguém o abriu. Dias depois
chega pelo correio uma carta que diz «Recebemos o documento com protocolo 100000002 e ele está completo». A carta não
menciona o nome de quem mandou nem do que se tratava: menciona **o protocolo que o remetente pôs no envelope**. Quem não anotou o que
mandou com esse protocolo não sabe o que está sendo confirmado.

---

## 1. Prepare a pasta

```bash
cd lecciones/21-enviado-no-es-confirmado
cp -R inicio mio
```

O router tem um arquivo novo, `orden-router/acuses.camel.yaml`. No alto ele tem a rota `sondeo-acuses`, já feita:
lê cada `997-<algo>.edi` da pasta `salida/` do parceiro, traduz com o mapeamento JSONata e o passa para
`correlacionar-acuse`, que é a parte a escrever.

Antes de escrever, **leia um 997**. Abra
`inicio/orden-router/mappings/edi/997/v1/cases/aceptacion/entrada.json`. É uma única linha com `\n` entre
segmentos; separado fica assim:

```
ISA*00*          *00*          *ZZ*SOCIO01        *ZZ*TIENDANORTE    *261113*0100*U*00401*700000001*0*P*>~
GS*FA*SOCIO01*TIENDANORTE*20261113*0100*700000001*X*004010~
ST*997*0001~
AK1*PO*100000002~
AK2*850*0001~
AK5*A~
AK9*A*1*1*1~
SE*6*0001~
GE*1*700000001~
IEA*1*700000001~
```

| Segmento | O que diz | De quem é o número? |
|---|---|---|
| `ISA … *700000001*` | O envelope do 997, com o número de controle dele | **Do parceiro.** Foi ele que o pôs ao mandar a confirmação |
| `AK1*PO*100000002` | «Confirmo o recebimento do grupo de pedidos de compra (`PO`) número 100000002» | **Nosso.** É o GS06 que foi mandado no 850 |
| `AK2*850*0001` | «Dentro desse grupo, o documento 850 número 0001» | **Nosso.** É o ST02 do 850 |
| `AK5*A` | O documento é aceito (`A`) ou rejeitado (`R`) | — |

Agora abra o de rejeição, `cases/rechazo/entrada.json`. Ele traz mais dois segmentos antes do `AK5*R`: `AK3*PO1*6**8`
diz que o problema está no segmento `PO1`, o sexto do documento; `AK4*7*234*7*SKU-9999` diz que o elemento 7
desse segmento traz `SKU-9999`, um produto que o parceiro não conhece.

E abra os dois `esperado.json`: é o que o mapeamento tira de cada 997 e o que a rota recebe no corpo.

```json
{"socio": {"control": "700000001"}, "grupo": {"tipo": "PO", "control": "100000002"},
 "documento": {"tipo": "850", "control": "0001"}, "acuse": "A", "errores": []}
```

## 2. Faça a previsão

Responda em um papel:

1. O 850 do pedido 4001 foi depositado. O parceiro já o recebeu? Como se saberia?
2. O 997 não traz o número do pedido. Com que número ele seria ligado à operação do 4001? Procure na tabela
   `operacion` de `infra/postgres/01-estado.sql` que coluna guarda esse número, e desde que lição.
3. Chega um 997 que cita um grupo que nunca foi mandado. O que deveria acontecer com ele?

Veja como está hoje:

```bash
./verifica inicio
```

A primeira marca passa: o 850 fica depositado e sem confirmação de recebimento. Depois o parceiro responde, o router lê o 997… e não
faz nada com ele. O canal do parceiro fica em `aceptada` para sempre, embora o parceiro já tenha confirmado, e da rejeição do
4006 ninguém fica sabendo.

## 3. Correlacione a confirmação de recebimento

Abra `mio/orden-router/acuses.camel.yaml` e procure a rota `correlacionar-acuse`. Hoje ela só escreve no log. Tem
quatro TODO, e todos menos o último cabem em **uma única instrução SQL**:

| TODO | O que faz | Para quê |
|---|---|---|
| 1 | Anota a confirmação na tabela `acuse`, **corresponda ou não** a algo | Que nenhuma confirmação se perca |
| 2 | Procura a operação do parceiro que esperava esta confirmação | A correlação: a única dificuldade de hoje |
| 3 | Passa-a para `confirmada` (AK5 = A) ou `rechazada` (AK5 = R), e guarda na linha da confirmação a que operação ela correspondeu | Que o canal só termine com a confirmação de recebimento |
| 4 | Se não correspondeu a nenhuma, alerta com um `WARN` que diga `<archivo> no corresponde` | Que alguém veja a confirmação sem dono |

A forma da instrução já vem dada. Primeiro um `UPDATE … RETURNING` dentro de um `WITH`, que muda a
operação e devolve qual mudou; depois o `INSERT` da confirmação, que usa o que foi devolvido:

```yaml
- to:
    uri: >-
      sql:WITH op AS (
        UPDATE operacion SET estado = CASE WHEN CAST(:#${body[acuse]} AS text) = 'R' THEN 'rechazada' ELSE 'confirmada' END
        WHERE canal = 'edi' AND estado = 'aceptada'
          AND …                               -- TODO 2: tus condiciones
        RETURNING id, orden, momento, estado)
      INSERT INTO acuse (archivo, control_socio, grupo_tipo, grupo_control, documento_tipo, documento_control,
                         codigo, errores, operacion)
      SELECT :#${variable.archivo}, :#${body[socio][control]}, :#${body[grupo][tipo]}, :#${body[grupo][control]},
             :#${body[documento][tipo]}, :#${body[documento][control]}, :#${body[acuse]},
             CAST(:#${variable.leido} AS jsonb) -> 'errores', (SELECT id FROM op)
      RETURNING COALESCE((SELECT orden || ' ' || momento || ' → ' || estado FROM op), '') AS resultado
    variableReceive: correlacion997
    parameters:
      dataSource: "#estado"
      outputType: SelectOne
```

Se o `UPDATE` não encontra nada, `op` fica vazio: a confirmação é anotada do mesmo jeito, com `operacion` em `NULL`, e `resultado`
sai como texto vazio. É isso que o TODO 4 usa.

**O trabalho são as condições do TODO 2.** A operação que espera esta confirmação é a que cumpre as quatro:

| Condição | Em SQL | Por quê |
|---|---|---|
| O número de controle dela é o do grupo que o AK1 cita | `numero_control = CAST(:#${body[grupo][control]} AS text)` | É o número que **o router** põe no GS06 desde a lição 15 |
| É do evento do ciclo de vida que corresponde ao documento | `momento = CASE CAST(:#${body[documento][tipo]} AS text) WHEN '850' THEN 'creada' WHEN '856' THEN 'embarcada' WHEN '214' THEN 'entregada' END` | Um 850 confirma a criação; um 856, o embarque |
| O tipo do grupo bate com o documento | `PO` vai com `850`, `SH` com `856` e `QM` com `214` | Uma confirmação mal montada não deve confirmar nada |
| É o primeiro documento do grupo | `CAST(:#${body[documento][control]} AS text) = '0001'` | Cada grupo nosso leva um único documento, com ST02 `0001` |

A terceira fica por fazer, com um `CASE` como o da segunda.

Para o TODO 4, um `choice`: se `${variable.correlacion997} == ''`, um `log` em `WARN` com a mensagem
`El 997 ${variable.archivo} no corresponde a ninguna operación que esperara su acuse`; se não, um `log` normal que
diga que pedido foi confirmado.

Repare no que **não** se usa: o `ISA13` do 997, `${body[socio][control]}`. Ele é guardado em `control_socio`, mas não
serve para correlacionar: é o número que o parceiro pôs **no envelope dele**, não o nosso.

## 4. Quebre um de propósito

Com tudo concluído, mude a primeira condição para que compare com o número do parceiro:
`numero_control = CAST(:#${body[socio][control]} AS text)`. Rode `./verifica`.

Antes, faça a previsão: o que acontece com o 4001 quando chegar o 997 dele?

Fica em `aceptada`. O 997 é anotado na tabela `acuse` sem operação, e no log aparece o `WARN` de «no
corresponde». Sai em vermelho a marca da confirmação. O ISA do parceiro (`7000000…`) nunca vai coincidir com o
número nosso (`1000000…`): são duas numerações de dois sistemas diferentes. Se ele não tivesse sido anotado como confirmação sem dono,
teria se perdido em silêncio. Devolva o `grupo`.

## 5. Confira

```bash
./verifica
```

O `verifica` manda o 4001 e espera a confirmação dele; depois o 4006, que pede um produto que o parceiro não conhece; e no fim
deixa no SFTP um 997 que cita um grupo que nunca mandamos. Está concluída quando aparecem as sete marcas verdes:

```
✅ Arrancaron el router (con el sondeo de los acuses), los tres canales y el sondeo de la bodega
✅ El 850 de la 4001 quedó depositado («aceptada») y todavía no hay acuse: enviado no es confirmado
✅ Llegó el 997 de aceptación: el canal pasó a «confirmada», correlacionado por el GS06 (…) y el ST02, no por el ISA del socio
✅ El 997 que leyó el router pasa el lector X12, y su AK1 cita el grupo (…)
✅ La API y el ERP no esperan un acuse: se quedan en «aceptada» (bd=aceptada rest=aceptada)
✅ La 4006: el 997 de rechazo dejó el canal en «rechazada», con el error que señaló el socio (PO1, SKU-9999)
✅ Un 997 que cita un grupo que no mandamos quedó anotado sin operación, con su WARN, y no confirmó nada más
```

Abra o 997 que confirmou o 4001: o `verifica` o copia para `mio/salida/997-<número>.edi`. Procure o número nosso no `AK1` e
o do parceiro no `ISA`.

**Não roda?** `./verifica solucion` testa a nossa, e
`diff mio/orden-router/acuses.camel.yaml solucion/orden-router/acuses.camel.yaml` mostra em que são diferentes.

| O que aparece | O que fazer |
|---|---|
| `Esperaba la 4001 confirmada` e a confirmação é «nada» | Falta o `INSERT INTO acuse`, ou a instrução não leva `dataSource: "#estado"` |
| `Esperaba la 4001 confirmada` e há confirmação | O `UPDATE` não encontrou a operação: confira que a comparação é com `body[grupo][control]` e que o `CASE` do evento está completo |
| `La 4006: esperaba rechazada` e continua `aceptada` | O `SET estado` não lê `body[acuse]`, ou a condição do tipo de grupo não deixa passar `PO` com `850` |
| `El 997 sin dueño` e há 0 confirmações sem operação | A confirmação sem dono não foi anotada: o `INSERT` tem de rodar mesmo que o `UPDATE` não mude nada |
| `El 997 sin dueño` e falta o WARN | O `choice` do TODO 4 não compara com `''`, ou o `log` não leva `loggingLevel: WARN` |
| `No está arriba socio-bot` | Suba o ambiente como diz «Antes de começar» |
| `El puerto 8080 está ocupado` | Os serviços de outra execução ficaram no ar: `DONDE=mio docker compose -f compose.yaml down` |

---

## O que isto ensina fora do curso

**Depositar não é entregar, e entregar não é ser entendido.** Um arquivo em um SFTP pode ficar lá sem que
ninguém o processe. Em EDI, o 997 é a maneira padrão de o outro lado dizer «recebi e consegui ler», ou «recebi e
está errado em tal segmento».

**Uma resposta assíncrona se correlaciona pelo que foi mandado.** O 997 não sabe de pedidos: sabe do número de
controle que o arquivo enviado trazia. Por isso, desde a lição 15, o router atribui um número único por documento e o guarda. Sem
esse número guardado, a confirmação não teria para quem apontar.

**Cada sistema numera o que é dele.** O ISA do 997 é do parceiro; o GS06 que ele cita é nosso. Confundi-los é o erro mais
comum ao correlacionar, e não falha com erro: simplesmente não encontra nada.

**O que não corresponde é anotado e gera um alerta.** Uma confirmação sem dono pode ser um erro do parceiro, um envio antigo ou um
teste. Jogá-la fora a esconde; anotá-la permite que alguém a investigue.

## Exercício: duas confirmações, dois números

1. Em papel, com os dois `entrada.json` dos casos de teste de referência, responda para cada 997: que número de controle o
   AK1 cita? Qual é o do ISA? Aceita ou rejeita? Se rejeita, que segmento e que valor aponta?
2. Depois de rodar `./verifica`, consulte a tabela de confirmações:
   `SELECT archivo, control_socio, grupo_control, codigo, operacion FROM acuse ORDER BY id;`
   Qual das três linhas não tem operação, e por quê?
3. Dos três canais do curso, qual termina em `aceptada` e qual em `confirmada`? Escreva uma frase para
   cada um que explique por que esse é o final dele.

---

## Por que foi feito assim

O que decidimos nesta lição, com o que descartamos e o que nos faria mudar de ideia:

- [O 997 se correlaciona pelo grupo e pelo documento, e só ele confirma](../decisiones/25-el-997-se-correlaciona-por-grupo-y-documento.md)

## Revisão

1. O que é um 997, e quem o manda?
2. O que o AK1 cita e o que o AK2 cita?
3. Por que o ISA13 do 997 não serve para correlacionar?
4. O que acontece com um 997 que não corresponde a nenhuma operação? Por que não é jogado fora?
5. **A pergunta de encerramento:** «depositado» e «confirmado» não são a mesma coisa. Que canal do curso espera qual, e
   por que a API e o ERP não esperam uma confirmação de recebimento?

**A seguir:** na lição 22 o SFTP do parceiro cai, e com as novas tentativas da lição 19 isso detém também a API
e o ERP. Como deixar de chamar um destino fora do ar sem parar os demais?
