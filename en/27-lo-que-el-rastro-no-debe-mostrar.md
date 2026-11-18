# Lesson 27 — What the audit trail must not show

> **What you will achieve:** making the audit trail of an order (what the database keeps for diagnosis and what the journey
> from lesson 26 displays) not show who the customer is: not her name, not her email, not her phone number, not her
> street. And having it, masked, still be useful for understanding what happened. To the destination, the order keeps
> going out complete.

**Before you start you need:** lesson 26 finished and the environment from lesson 00 turned on.

## What is new today, in one sentence

In lesson 26 you built the journey: anyone who can query it sees each attempt with what went out, in full. That
includes the person who bought. Today the test order is 4008, which carries a customer's name, email, phone number
and address (made up, like everything in this course). Whoever handles support needs to know what happened to the
order; they do **not** need to know where the customer lives. Today you **mask** the personal data before it enters
the audit trail.

**The picture:** your card statement in the bank's app. It shows you `**** **** **** 0428`: enough for you to know
which one it is, not enough for someone to use it. The bank does have the full number, and uses it to charge; what it
masks is **what is displayed**.

---

## 1. Set up your folder

```bash
cd lecciones/27-lo-que-el-rastro-no-debe-mostrar
cp -R inicio mio
```

Open `datos/ordenes/tramo-2/orden-4008.json` and mark with a pencil each piece of data that says **who** the customer
**is**. Then open `mio/orden-router/reparto.camel.yaml` and, in the `intentar` route, find the comment `Desde la 27`
("since lesson 27"). Before being stored in `payload`, the body goes through a new mapping:

```
lo que sale ──→ al canal (completo)
            └─→ mappings/rastro/mascara/v1 ──→ variable rastro ──→ payload (tapado)
```

| Piece | What it stores |
|---|---|
| The body that goes to the channel | The **complete** order: the API, the ERP and the partner need it in order to deliver |
| `payload.cuerpo` | What went out, **masked** |
| `intento.sha256` | The fingerprint of what **really** went out, unmasked: it still serves to recognize repeats (lesson 18) |

The route is already wired. Your job is the mapping: `mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`.
Today it returns everything just as it arrives (`$`). And it has its golden test cases in `cases/`, like the mappings from
lesson 03.

## 2. Predict

Answer on paper:

1. In the journey of order 4008, which of the customer's data would you see today? Who can query that journey?
2. If you mask **everything** about the customer, could you still tell whether her email was spelled correctly or
   whether the postal code was valid?
3. The `orden` table also stores order 4008 in full. Why is that one allowed to have the data?

Look at how it is today:

```bash
./verifica inicio
```

The first check comes out red: the golden test cases expect the data masked and the mapping lets it through. And further
down, the search finds the customer's name, email, phone number and street in the audit trail and in the journey.

## 3. Mask just enough

Before writing, open the three golden test cases: `cases/orden-con-cliente`, `cases/embarque` and `cases/entrega`. Each
one has its `entrada.json` and its `esperado.json`. Compare them and fill in this table:

| Data | Arrives | Is stored | What it still says |
|---|---|---|---|
| `cliente.nombre` | `Ana Ejemplo Prueba` | `A. E. P.` | Her initials |
| `cliente.correo` | `ana.prueba@example.com` | `a***@example.com` | Whether the domain was valid |
| `cliente.telefono` | `+52 55 0000 0428` | `*** **** **28` | The last two digits, to recognize it |
| `cliente.direccion.calle` | `Calle de Ejemplo 28` | `***` | Nothing |
| `recibio` (in a delivery) | `Ana Ejemplo Prueba` | `A. E. P.` | Her initials |
| `tienda`, `renglones`, `cp`, `colonia`… | | The same | Everything that is useful for diagnosis |

The mapping has two TODOs.

### TODO 1: the `$mascara` function

In JSONata you define a function with `function($v, $modo) { … }` and store it in a variable with `:=`. Yours
receives a value and a mode, and returns the masked value. This is what its shape looks like, with each mode as a
`? :` question:

```
(
  $mascara := function($v, $modo) {
    $not($exists($v)) ? $v
    : $modo = 'iniciales' ? …
    : $modo = 'correo'    ? …
    : $modo = 'telefono'  ? …
    : '***'
  };
  …   (aquí va el TODO 2)
)
```

The first line takes care of a case that gets forgotten: **if the value is not there, let it stay not there.** A
shipment does not carry a customer; without that line, a made-up `cliente` would appear.

The JSONata functions you need for each mode:

| Mode | Idea | Functions |
|---|---|---|
| `iniciales` | Split the name into words, take the first letter of each one with a period, and join them with a space | `$split($trim($v), ' ')`, `$map(…, function($p) { $substring($p, 0, 1) & '.' })`, `$join(…, ' ')` |
| `correo` | The first letter, `***@` and what comes after the at sign | `$substring($v, 0, 1)`, `$substringAfter($v, '@')`, and `&` to join texts |
| `telefono` | `*** **** **` and the last two digits | `$replace($v, /[^0-9]/, '')` leaves only the digits; `$substring(…, -2)` takes the last two |

### TODO 2: applying it where it belongs

JSONata has an operator to change **only one part** of a document and leave the rest the same, the transform
operator: `~> | ruta | { cambios } |`. It reads "in whatever is at `ruta`, put these changes".

```
$
  ~> | cliente | {
       "nombre": $mascara(nombre, 'iniciales'),
       "correo": $mascara(correo, 'correo'),
       "telefono": $mascara(telefono, 'telefono')
     } |
  ~> | cliente.direccion | { "calle": $mascara(calle, 'todo') } |
  ~> | $ | { "recibio": $mascara(recibio, 'iniciales') } |
```

They are three chained transforms: one on `cliente`, one on `cliente.direccion` and one on the root (`$`), for the
`recibio` of a delivery. What you do not name stays the same: that is why the postal code and the order lines pass through
untouched.

Test your mapping before bringing anything up:

```bash
../../herramientas/probar-mapeo mio/orden-router/mappings/rastro/mascara/v1
```

## 4. Break one on purpose

With your mapping finished, add the postal code to what gets masked, in the transform on `cliente.direccion`:
`"cp": $mascara(cp, 'todo')`. Run `./verifica`.

First, predict: the audit trail now masks more. Is that better?

The first check comes out red (the golden test case expected the postal code) and so does the last: the audit trail no longer
says the postal code. If a delivery fails because the postal code does not exist, the audit trail no longer lets you see
it. **Masking too much is also a mistake**: the audit trail exists for diagnosis, and one that says nothing is useless. A
postal code alone, without street or name, hardly points to the customer. Put it back the way it was.

## 5. Check

```bash
./verifica
```

`verifica` tests your golden test cases, sends order 4008, waits for its three channels to answer and then **searches**
for the customer's four pieces of data in the database audit trail, in the journey and in the logs of all the services. It
is done when you see the seven green checks:

```
✅ El mapeo rastro/mascara/v1 pasa sus casos dorados
✅ Arrancaron el router (que tapa los datos personales del rastro), los tres canales y el sondeo
✅ Control: la orden guardada sí tiene los 4 datos personales (la búsqueda encuentra lo que hay)
✅ En el rastro de la base (payload, intentos, acuses, avisos, conflictos): 0 datos personales
✅ GET /ordenes/4008/recorrido: la orden sí sale, y sin sus datos personales
✅ En los logs: la 4008 aparece (…) veces y sus datos personales 0
✅ Lo tapado sirve para diagnosticar: (…)
```

Look at the third one. Before counting zeros, `verifica` searches for the same four pieces of data where they **do**
have to be, in the `orden` table, and finds them. If it did not find them there, the "0" in the following checks
would prove nothing: it could be that the search was written wrong. That is called a **positive control**: before
believing a zero, check that the search knows how to find. And read the zero with its scope: `0 datos personales`
("0 personal data") means 0 occurrences of the four pieces of data that `verifica` searches for, in the places where
it searches for them.

**Not working?** `./verifica solucion` tests ours, and
`diff mio/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata solucion/orden-router/mappings/rastro/mascara/v1/mapeo.jsonata`
tells you where they differ.

| What you see | What to do |
|---|---|
| `no pasa sus casos dorados` | Run `probar-mapeo` as in step 3: it tells you which case and which field does not match |
| The `embarque` case fails with a `cliente` that should not be there | The first line of `$mascara` is missing: if the value is not there, let it stay not there |
| `En el rastro de la base hay … apariciones` | Some transform is not being applied: check the path of each `~> | … |` |
| `GET /ordenes/4008/recorrido: … de los 4 datos personales a la vista` | The same: the journey reads from `payload` |
| `Lo que guarda el rastro de la 4008` without the postal code or without the domain | You masked too much: the postal code and the email domain stay |
| `El puerto 8080 está ocupado` | The services from another run were left up: `DONDE=mio docker compose -f compose.yaml down` |

---

## What this teaches beyond the course

**You mask what is displayed, not what is used.** The destinations need the complete data in order to deliver: the
carrier needs the address. The audit trail is for diagnosis, and it is read by more people than should know the customer.
That is why the masking happens there and not in the send.

**Masking is a design, not a deletion.** The initials, the email domain, the last two digits and the postal code say
enough to recognize and diagnose, and much less than the full data. Deciding what stays is the hard part. And masking
fields is not anonymizing: together, or with other sources, some data can point back to the person (exercise 2
explores it). A real audit trail also needs a decision on who can read it and how long it is kept.

**The fingerprint is still that of the real thing.** The audit trail stores what is masked, but the fingerprint from
lesson 18 is taken from what really went out. That way a repeat is still recognized without storing the data.

**Before believing a zero, search where it is.** A `0 datos personales` can be a clean audit trail or a search that does
not know how to search. The positive control is what tells them apart, and it works for any measurement that gives
you zero.

## Exercise: what is seen and what is not

1. Ask for the journey of order 4008 (`curl -s localhost:8080/ordenes/4008/recorrido`) and fill in two columns: what
   you can know about the order and what you can**not** know about the customer.
2. In `orden-4008.json`, find another piece of data that, together with the ones left unmasked, could help identify
   the customer. Would you mask it? What would you lose for diagnosis if you mask it?
3. Someone in support needs to call the customer because her delivery failed. Where should they get the phone number
   from: the audit trail or somewhere else? Who should be able to see it?

---

## Why it was done this way

What we decided in this lesson, along with what we ruled out and what would make us change our mind:

- [The audit trail is stored masked](../decisiones/31-el-rastro-se-guarda-tapado.md)

## Review

1. Which data in order 4008 is personal, and which is not?
2. Why is the masking done in the audit trail and not in what goes to the destination?
3. What does JSONata's transform operator, `~> | ruta | { … } |`, do?
4. Why are the postal code and the email domain left in view?
5. **The closing question:** `verifica` searches for the data in the `orden` table before counting zeros in the
   audit trail. What is that for, and what would happen to the test if it did not?

**Next:** in lesson 28 the integration stops talking only over HTTP and files: the lifecycle events of the order travel
through a topic, and each integration reads them with its own group.
