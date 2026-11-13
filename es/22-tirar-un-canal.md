# Lección 22 — Tirar un canal

> **Lo que vas a lograr:** que cuando el SFTP del socio se cae, la API y el ERP sigan recibiendo sus órdenes al
> momento. El router deja de llamar al destino caído, anota lo que no mandó, y vuelve a probarlo solo cuando pasa un
> rato.

**Antes de empezar necesitas:** la lección 20 terminada y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 10 apagaste el ERP y viste que la API y el socio recibían la orden igual: «un canal caído no detiene a los otros
dos». Desde la 19 eso ya no es cierto. El router reintenta en el lugar, con pausas que suman medio minuto, y **mientras espera al
socio, la cola entera espera con él**. Una orden que solo tenía que llegarle a la API tarda medio minuto por culpa de
un destino que no tiene nada que ver. Hoy pones un **cortacircuito** por canal: cuando un destino falla seguido, el
router deja de llamarlo un rato.

**La imagen:** la pastilla de la luz de tu casa, una por zona. Si la secadora hace corto, se bota la pastilla de la
lavandería y la cocina sigue con luz. No le pides a nadie que deje de usar la secadora: la pastilla corta sola. Y
cuando ya revisaste, la subes y pruebas. Si hubiera una sola pastilla para toda la casa, un corto en la lavandería te
dejaría sin refrigerador.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/22-tirar-un-canal
cp -R inicio mio
```

Todo tu trabajo va en `mio/orden-router/reparto.camel.yaml`, en la ruta `intentar`, en el paso 3: la llamada al
canal. Léelo antes de escribir. El comentario de arriba describe los tres estados de un cortacircuito:

| Estado | Qué hace | Cuándo cambia |
|---|---|---|
| **Cerrado** | Llama al canal, como siempre | Se **abre** si, de las últimas 4 llamadas (con al menos 2), la mitad o más fallaron |
| **Abierto** | **No** llama al canal: contesta al momento que no se intentó | Pasados 10 segundos, queda **medio abierto** |
| **Medio abierto** | Deja pasar **una** llamada de prueba | Si sale bien, se **cierra**; si falla, se vuelve a abrir |

Y hay una ruta ya hecha, `sin-respuesta`, abajo en el mismo archivo. Es la que corre cuando el cortacircuito no deja
pasar la llamada o cuando la llamada falló. Distingue los dos casos, y eso importa:

| Caso | `codigo` | Desenlace del intento | La operación |
|---|---|---|---|
| El cortacircuito estaba abierto y **no se llamó** | `-1` | `no_enviado` | Sigue `pendiente` |
| Se llamó y falló | `0` | `no_se_sabe` (la 20) | Se reintenta |

`no_enviado` no es una duda: el router sabe que no llamó. Por eso no se reintenta en el lugar ni va a conciliación.
Se queda `pendiente`, y en la 23 vas a ver cómo se vuelve a mandar.

## 2. Predice

Contesta en un papel:

1. Tiras el SFTP del socio y llegan cuatro órdenes de franquicia. Sin cortacircuito, ¿cuánto tarda la cuarta en
   llegar a la API? (Pista: suma las pausas de la 19.)
2. ¿Por qué un cortacircuito por canal y no uno para todo el router?
3. El SFTP vuelve. ¿Quién tiene que «subir la pastilla»?

Mira cómo está hoy:

```bash
./verifica inicio
```

La verifica apaga el SFTP, manda cuatro órdenes y cuenta cuántas llegaron a la API y al ERP en unos 25 segundos.
Llega **una de cuatro**. La 4001 pasa a la API y al ERP, y luego la cola se queda esperando los reintentos del socio;
las otras tres no avanzan aunque la API y el ERP estén perfectos.

## 3. Pon una pastilla por canal

En el paso 3 de `intentar` hay un `doTry` que llama a `direct:llamar-canal`. Ese `doTry` es lo que vas a reemplazar.
Tiene tres TODO:

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | Un `choice` por `${variable.canal}` con tres ramas (`rest`, `bd`, `edi`), y en cada una un `circuitBreaker` con su `id`: `cortacircuito-rest`, `cortacircuito-bd`, `cortacircuito-edi` | Una pastilla por zona |
| 2 | En cada `circuitBreaker`, su `resilience4jConfiguration` | Cuándo se abre y cuánto tarda en probar |
| 3 | En cada uno, un `onFallback` que llame a `direct:sin-respuesta` | Que se anote si no se llamó o si se llamó y falló |

Así se ve **una** rama. Las otras dos son iguales, con su canal y su `id`:

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

Cada línea de la configuración es una fila de la tabla de estados:

| Parámetro | Valor | Qué dice |
|---|---|---|
| `slidingWindowSize` | 4 | Mira las últimas 4 llamadas |
| `minimumNumberOfCalls` | 2 | No decide con menos de 2 |
| `failureRateThreshold` | 50 | Se abre con 50 % de fallas o más |
| `waitDurationInOpenState` | `"10000"` | Abierto 10 segundos (va en milisegundos y entre comillas) |
| `permittedNumberOfCallsInHalfOpenState` | 1 | En medio abierto, una sola llamada de prueba |
| `automaticTransitionFromOpenToHalfOpenEnabled` | `true` | Pasa a medio abierto solo, sin esperar a que alguien llame |

Escribes la configuración tres veces, igual. Se podría escribir una vez y reusarla; aquí va completa en cada canal
para que cada pastilla se lea sola.

Una nota para más adelante: si corres esta ruta en modo nativo (el apéndice), el cortacircuito lo da otra librería,
pero el `circuitBreaker` del YAML es el mismo y no cambias nada.

## 4. Rompe uno a propósito

Con tus tres cortacircuitos terminados, cambia el `id` de los tres a uno solo, `cortacircuito`, como si hubiera una
sola pastilla para todo el router. Corre `./verifica`.

Antes, predice: la API y el ERP están bien. ¿Siguen al día?

No. El cortacircuito cuenta las llamadas de los tres canales juntas: las del socio fallan, las de la API y el ERP no,
y con la mitad fallando se abre **para todos**. La API y el ERP dejan de recibir órdenes por un destino que no es
suyo, y en el log aparece abierto el cortacircuito de `rest` y el de `bd`. Salen en rojo la marca de la API y el ERP
y la del log. Regresa los tres `id`.

## 5. Comprueba

```bash
./verifica
```

`verifica` apaga el SFTP, manda cuatro órdenes, revisa la API, el ERP y el estado; luego vuelve a prender el SFTP,
espera a que pasen los 10 segundos y manda una quinta. Al salir, deja el SFTP prendido. Está terminada cuando ves las
cinco marcas verdes:

```
✅ Arrancaron el router (con un cortacircuito por canal), los tres canales y el sondeo
✅ Con el SFTP caído, la API y el ERP siguieron al día: las 4 órdenes en los dos, en (…) s
✅ El canal del socio: las 4 quedaron pendientes, su último intento «no_enviado», y de la 4002 en adelante con un solo intento (no esperaron)
✅ Log: se abrió el cortacircuito del socio, y solo ese
✅ Volvió el SFTP: la 4005 pasó por el cortacircuito ya cerrado y su 850 está en el socio
```

Lee la tercera con calma. La 4001 sí intentó varias veces: el cortacircuito todavía estaba cerrado, y las fallas de
esos intentos son las que lo abrieron. De la 4002 en adelante hay un solo intento, `no_enviado`: el router ni
siquiera llamó.

Y mira el log del router mientras pasa:

```bash
DONDE=mio docker compose -f compose.yaml logs orden-router | grep -i cortacircuito
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-router/reparto.camel.yaml solucion/orden-router/reparto.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `a los (…) s la API tiene 1 de 4` | No hay cortacircuito, o el del socio nunca se abre: revisa que la rama `edi` exista y que `failureRateThreshold` sea 50 |
| `El canal del socio quedó` con 0 no enviadas | Falta el `onFallback` a `direct:sin-respuesta`: sin él, nada anota el `no_enviado` |
| `esperaba el cortacircuito de edi abierto y los de rest y bd cerrados` | Dos ramas comparten `id`: cada canal lleva el suyo |
| `Volvió el SFTP y la 4005 quedó «pendiente»` | El cortacircuito no pasa a medio abierto: falta `automaticTransitionFromOpenToHalfOpenEnabled: true`, o `waitDurationInOpenState` es mucho mayor que 10000 |
| `No arrancaron todas las rutas` | Revisa la sangría: `steps` y `onFallback` van dentro de `circuitBreaker`, a la misma altura que `resilience4jConfiguration` |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

Si la verifica se corta a la mitad, el SFTP se vuelve a prender solo al salir. Si aun así lo ves apagado:
`docker compose -f ../../infra/compose.yaml start socio-sftp`.

---

## Lo que esto enseña fuera de la práctica

**Un destino caído no debe tumbar a los que están bien.** Es la idea detrás del cortacircuito y de los
«compartimentos» de un barco: si se inunda uno, los demás siguen secos. Sin eso, la falla más chica del sistema
decide la velocidad de todo.

**No llamar a lo que sabes caído es un favor para los dos.** Te ahorras la espera, y el destino no recibe una ola de
llamadas mientras intenta volver.

**El cortacircuito se recupera solo.** La llamada de prueba en medio abierto evita que alguien tenga que acordarse de
«subir la pastilla». Si el destino sigue mal, se vuelve a abrir y no pasó nada.

**«No lo mandé» es un hecho, y se anota como tal.** Un intento `no_enviado` dice algo distinto de uno `no_se_sabe`. El
primero se puede volver a mandar sin miedo, porque el destino nunca lo vio.

## Ejercicio: la pastilla en tres tiempos

Con tu versión terminada, corre `./verifica` y luego:

1. Con el log de `orden-router`, arma una línea de tiempo de la corrida: cuándo se abrió el cortacircuito de `edi`,
   cuántas órdenes dejó sin mandar y cuándo pasó la llamada de prueba. Marca en ella los tres estados: cerrado,
   abierto y medio abierto.
2. En la base, consulta los intentos del canal del socio:
   `SELECT p.orden, i.n, i.desenlace, i.respuesta->>'cuerpo' FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.canal = 'edi' AND p.momento = 'creada' ORDER BY p.orden, i.n;`
   ¿Qué dice la respuesta de un intento `no_enviado`? ¿Y la de uno `no_se_sabe`?
3. Las cuatro órdenes del socio se quedaron `pendiente`. Nadie las va a mandar solas. ¿Qué le falta al router para
   recuperarlas? Escribe tu respuesta: es la lección 23.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Un cortacircuito por canal, en el router](../decisiones/26-un-cortacircuito-por-canal.md)

## Repaso

1. ¿Qué tres estados tiene un cortacircuito, y qué hace en cada uno?
2. ¿Qué significa cada número de la configuración: 4, 2, 50, 10000 y 1?
3. ¿Por qué un cortacircuito por canal y no uno para todo el router?
4. ¿En qué se distingue un intento `no_enviado` de uno `no_se_sabe`? ¿Por qué el primero no va a conciliación?
5. **La pregunta de cierre:** en la 10 concluiste que «un canal caído no detiene a los otros dos». ¿Por qué dejó de
   ser cierto en la 19, y qué lo hace cierto otra vez? Usa lo que viste en la verifica de `inicio/` y en la tuya.

**Siguiente:** en la lección 23 alguien tiene que volver a mandar lo que se quedó pendiente o en conciliación. ¿Cómo
reprocesas una orden sin duplicarla y dejando escrito quién lo hizo y por qué?
