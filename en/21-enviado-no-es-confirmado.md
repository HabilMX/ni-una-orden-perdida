# Lesson 21 — Sent is not confirmed

> **What you will achieve:** the partner's channel is not considered finished when the 850 lands in its SFTP, but when
> the partner answers with its acknowledgment, the **997**. That acknowledgment arrives later, in another file and
> without the order number: your job is to know which send it is answering.

**Before you start you need:** lessons 20, 09 (the correlation thread) and 06 (reading X12) finished, and the
environment from lesson 00 turned on. This lesson uses a new service in the environment, `socio-bot`, which plays the
partner's system and answers with the 997s. If you do not have it up:

```bash
docker compose -f infra/compose.yaml up -d --wait
```

## What is new today, in one sentence

With the API and the ERP, the response arrives in the same call: you ask, they answer, and you know. Not with the
partner. The router leaves the 850 in a folder on the SFTP and the call ends there. **The file being there says that
you dropped it, not that the partner read it or understood it.** The partner answers when its system processes the
file: seconds later in this course, hours in real life. And it answers by another path, leaving a 997 in its
`salida/` folder.

**The picture:** you leave an envelope in a company's mailbox. The mailbox does not tell you whether anyone opened it.
Days later a letter arrives in the mail saying "We received your document with reference number 100000002 and it is
complete". The letter does not mention your name or what it was about: it mentions **the reference number you put on
the envelope**. If you did not write down what you sent under that reference number, you do not know what they are
confirming.

---

## 1. Set up your folder

```bash
cd lecciones/21-enviado-no-es-confirmado
cp -R inicio mio
```

The router has a new file, `orden-router/acuses.camel.yaml`. At the top it has the `sondeo-acuses` route, already
done: it reads each `997-<algo>.edi` from the partner's `salida/` folder, translates it with its JSONata mapping and
passes it to `correlacionar-acuse`, which is your part.

Before writing, **read a 997**. Open
`inicio/orden-router/mappings/edi/997/v1/cases/aceptacion/entrada.json`. It is a single line with `\n` between
segments; separated, it looks like this:

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

| Segment | What it says | Whose number is it? |
|---|---|---|
| `ISA … *700000001*` | The envelope of the 997, with its control number | **The partner's.** The partner put it there when sending its acknowledgment |
| `AK1*PO*100000002` | "I acknowledge the purchase order (`PO`) group number 100000002" | **Yours.** It is the GS06 you sent in the 850 |
| `AK2*850*0001` | "Within that group, the 850 document number 0001" | **Yours.** It is the ST02 of the 850 |
| `AK5*A` | The document is accepted (`A`) or rejected (`R`) | — |

Now open the rejection one, `cases/rechazo/entrada.json`. It has two more segments before the `AK5*R`: `AK3*PO1*6**8`
says the problem is in the `PO1` segment, the sixth of the document; `AK4*7*234*7*SKU-9999` says that element 7
of that segment carries `SKU-9999`, a product the partner does not know.

And open the two `esperado.json` files: that is what the mapping gets out of each 997 and what your route receives in
the body.

```json
{"socio": {"control": "700000001"}, "grupo": {"tipo": "PO", "control": "100000002"},
 "documento": {"tipo": "850", "control": "0001"}, "acuse": "A", "errores": []}
```

## 2. Predict

Answer on paper:

1. You dropped the 850 for order 4001. Has the partner received it yet? How would you know?
2. The 997 does not carry the order number. What number would you use to match it with the operation for order 4001?
   Look in the `operacion` table of `infra/postgres/01-estado.sql` for the column that stores that number, and since
   which lesson.
3. A 997 arrives that cites a group you never sent. What should happen to it?

Look at how it is today:

```bash
./verifica inicio
```

The first check passes: the 850 is dropped and has no acknowledgment. Then the partner answers, the router reads the
997… and does nothing with it. The partner's channel stays in `aceptada` forever, even though the partner already
acknowledged, and nobody finds out about the rejection of order 4006.

## 3. Correlate the acknowledgment

Open `mio/orden-router/acuses.camel.yaml` and find the `correlacionar-acuse` route. Today it only writes to the log.
It has four TODOs, and all but the last one fit in **a single SQL statement**:

| TODO | What it does | What for |
|---|---|---|
| 1 | Records the acknowledgment in the `acuse` table, **whether or not it corresponds** to something | So no acknowledgment is lost |
| 2 | Finds the partner operation that was waiting for this acknowledgment | The correlation: the only difficulty today |
| 3 | Moves it to `confirmada` (AK5 = A) or `rechazada` (AK5 = R), and stores in the acknowledgment's row which operation it corresponded to | So the channel finishes only with the acknowledgment |
| 4 | If it did not correspond to any, flags it with a `WARN` that says `<archivo> no corresponde` | So someone sees the acknowledgment with no owner |

We give you the shape of the statement. First an `UPDATE … RETURNING` inside a `WITH`, which changes the
operation and returns which one changed; then the `INSERT` of the acknowledgment, which uses what was returned:

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

If the `UPDATE` finds nothing, `op` is left empty: the acknowledgment is recorded anyway, with `operacion` as `NULL`,
and `resultado` comes out as empty text. That is what TODO 4 uses.

**Your job is the conditions of TODO 2.** The operation waiting for this acknowledgment is the one that meets all
four:

| Condition | In SQL | Why |
|---|---|---|
| Its control number is that of the group the AK1 cites | `numero_control = CAST(:#${body[grupo][control]} AS text)` | It is the number **you** put in the GS06 since lesson 15 |
| It belongs to the lifecycle event that corresponds to the document | `momento = CASE CAST(:#${body[documento][tipo]} AS text) WHEN '850' THEN 'creada' WHEN '856' THEN 'embarcada' WHEN '214' THEN 'entregada' END` | An 850 acknowledges the creation; an 856, the shipment |
| The group type matches the document | `PO` goes with `850`, `SH` with `856` and `QM` with `214` | A badly built acknowledgment must not confirm anything |
| It is the first document of the group | `CAST(:#${body[documento][control]} AS text) = '0001'` | Each of our groups carries a single document, with ST02 `0001` |

Write the third one yourself, with a `CASE` like the one in the second.

For TODO 4, a `choice`: if `${variable.correlacion997} == ''`, a `log` at `WARN` with the message
`El 997 ${variable.archivo} no corresponde a ninguna operación que esperara su acuse`; otherwise, a normal `log` that
says which order it confirmed.

Notice what you do **not** use: the `ISA13` of the 997, `${body[socio][control]}`. It is stored in `control_socio`,
but it is no use for correlating: it is the number the partner put **on its envelope**, not yours.

## 4. Break one on purpose

With everything finished, change the first condition so that it compares with the partner's number:
`numero_control = CAST(:#${body[socio][control]} AS text)`. Run `./verifica`.

First, predict: what happens to order 4001 when its 997 arrives?

It stays in `aceptada`. The 997 is recorded in the `acuse` table without an operation, and your `no corresponde`
(does not correspond) `WARN` appears in the log. The confirmation check comes out red. The partner's ISA (`7000000…`)
is never going to match your number (`1000000…`): they are two numbering schemes from two different systems. If you
had not recorded it as an acknowledgment with no owner, it would have been lost in silence. Put `grupo` back.

## 5. Check

```bash
./verifica
```

`verifica` sends order 4001 and waits for its acknowledgment; then order 4006, which asks for a product the partner
does not know; and finally it leaves in the SFTP a 997 that cites a group we never sent. It is done when you see the
seven green checks:

```
✅ Arrancaron el router (con el sondeo de los acuses), los tres canales y el sondeo de la bodega
✅ El 850 de la 4001 quedó depositado («aceptada») y todavía no hay acuse: enviado no es confirmado
✅ Llegó el 997 de aceptación: el canal pasó a «confirmada», correlacionado por el GS06 (…) y el ST02, no por el ISA del socio
✅ El 997 que leyó el router pasa el lector X12, y su AK1 cita el grupo (…)
✅ La API y el ERP no esperan un acuse: se quedan en «aceptada» (bd=aceptada rest=aceptada)
✅ La 4006: el 997 de rechazo dejó el canal en «rechazada», con el error que señaló el socio (PO1, SKU-9999)
✅ Un 997 que cita un grupo que no mandamos quedó anotado sin operación, con su WARN, y no confirmó nada más
```

Open the 997 that confirmed order 4001: `verifica` copies it to `mio/salida/997-<número>.edi`. Find your number in the
`AK1` and the partner's in the `ISA`.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/acuses.camel.yaml solucion/orden-router/acuses.camel.yaml` tells you where they differ.

| What you see | What to do |
|---|---|
| `Esperaba la 4001 confirmada` and the acknowledgment is `nada` (nothing) | The `INSERT INTO acuse` is missing, or the statement does not have `dataSource: "#estado"` |
| `Esperaba la 4001 confirmada` and there is an acknowledgment | The `UPDATE` did not find the operation: check that you compare with `body[grupo][control]` and that the `CASE` for the lifecycle event is complete |
| `La 4006: esperaba rechazada` and it is still `aceptada` | The `SET estado` does not read `body[acuse]`, or your group type condition does not let `PO` through with `850` |
| `El 997 sin dueño` and there are 0 acknowledgments without an operation | The acknowledgment with no owner was not recorded: the `INSERT` has to run even if the `UPDATE` changes nothing |
| `El 997 sin dueño` and the WARN is missing | The `choice` in TODO 4 does not compare with `''`, or the `log` does not have `loggingLevel: WARN` |
| `No está arriba socio-bot` | Bring up the environment as "Before you start" says |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**Dropping is not delivering, and delivering is not being understood.** A file on an SFTP can sit there without
anyone processing it. In EDI, the 997 is the standard way for the other side to say "I received it and I could read
it", or "I received it and it is wrong in such-and-such segment".

**An asynchronous response is correlated by what you sent.** The 997 knows nothing about orders: it knows about the
control number your file carried. That is why, since lesson 15, the router assigns a unique number per document and
stores it. Without that stored number, the acknowledgment would have nothing to point to.

**Each system numbers its own.** The ISA of the 997 is the partner's; the GS06 it cites is yours. Mixing them up is
the most common error when correlating, and it does not fail: it simply finds nothing.

**What does not correspond is recorded and flagged.** An acknowledgment with no owner can be an error by the partner,
an old send or a test. Throwing it away hides it; recording it lets someone investigate.

## Exercise: two acknowledgments, two numbers

1. On paper, with the two `entrada.json` files from the golden test cases, answer for each 997: what control number does
   the AK1 cite? Which one is the ISA's? Does it accept or reject? If it rejects, what segment and what value does it
   point to?
2. After running `./verifica`, query the acknowledgments table:
   `SELECT archivo, control_socio, grupo_control, codigo, operacion FROM acuse ORDER BY id;`
   Which of the three rows has no operation, and why?
3. Of the three channels in the course, which one ends in `aceptada` and which in `confirmada`? Write one sentence for
   each explaining why that is where it ends.

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The 997 is correlated by the group and the document, and only it confirms](../decisiones/25-el-997-se-correlaciona-por-grupo-y-documento.md)

## Review

1. What is a 997, and who sends it?
2. What does the AK1 cite and what does the AK2 cite?
3. Why is the ISA13 of the 997 no use for correlating?
4. What happens to a 997 that does not correspond to any operation? Why is it not thrown away?
5. **The closing question:** "dropped" and "confirmed" are not the same. Which channel in the course waits for which,
   and why do the API and the ERP not wait for an acknowledgment?

**Next:** in lesson 22 the partner's SFTP goes down, and with the retries from lesson 19 that also holds up the API
and the ERP. How do you stop calling a destination that is down without stopping the others?
