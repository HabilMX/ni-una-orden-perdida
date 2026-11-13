# Lección 20 — Aceptado, rechazado o no se sabe

> **Lo que vas a lograr:** que el router clasifique cada respuesta del destino por lo que de verdad dijo: «sí»,
> «no» o nada claro. Que un «no» no se repita, y que lo que se quedó sin respuesta, al agotar los reintentos, quede
> marcado para que una persona lo revise, nunca como un fallo.

**Antes de empezar necesitas:** la lección 19 terminada y el ambiente de la 00 encendido. Esta lección usa la API
1.5.0, que tiene tres modos de prueba: `rechaza`, `lento` y `satura`. Si tu API es de antes, recréala una vez:

```bash
docker compose -f infra/compose.yaml up -d --wait --build --force-recreate api-pedidos
```

## Lo nuevo de hoy, en una frase

En la 19 el router aprendió a reintentar cuando el destino no contesta. Pero «no contestó» es solo una de las
respuestas posibles. Hay destinos que contestan «no» con toda claridad, y hay llamadas que se cortan a la mitad sin
que sepamos si el destino alcanzó a escribir. Hoy el router aprende a distinguirlas **por la evidencia**, no por lo
que esperaba.

**La imagen:** mandaste una carta certificada. Si te regresa el acuse firmado, llegó. Si te la regresan con un sello
de «domicilio no existe», no llegó y no tiene caso mandarla otra vez a la misma dirección. Pero si pasan tres semanas
y no te llega nada, no sabes: pudo haberse perdido o pudo haber llegado y el acuse se perdió de regreso. Lo correcto
no es decir «se perdió»; es decir «no sé» y preguntar en la oficina.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/20-aceptado-rechazado-o-no-se-sabe
cp -R inicio mio
```

Hoy trabajas en dos archivos del router que ya conoces:

| Archivo | Ruta | Qué cambias |
|---|---|---|
| `reparto.camel.yaml` | `intentar`, la que llama al canal y anota cómo le fue | El `choice` que decide el desenlace (TODO 1 y 2) |
| `reintentos.camel.yaml` | El `onException` de la 19 | Qué pasa cuando se agotan los reintentos (TODO 3) |

Y una pieza que ya está hecha: **el canal int-rest le pone un tiempo límite de 3 segundos a la API**. Si la API no
contesta en ese tiempo, int-rest no inventa un «sí» ni un «no»: le contesta **504** al router, que en HTTP quiere
decir «quien estaba detrás no contestó a tiempo».

Y otra, también hecha: **si el destino dice cuánto esperar, se espera eso.** Un 429 suele traer el encabezado
`Retry-After: 3`, «vuelve en 3 segundos». El router lo pasa como la pausa de su siguiente reintento, con un tope de 10
segundos para que un destino no lo detenga una hora. Y el cliente HTTP de Camel, que por su cuenta repetiría un 429 sin
dejar rastro, está apagado (`automaticRetriesDisabled`): el único que reintenta es el router, y cada intento queda en
la base.

## 2. Predice

Contesta en un papel:

1. La API tardó 10 segundos y el canal cortó a los 3. ¿Escribió o no escribió la orden? ¿Cómo lo sabrías?
2. La API contestó 422 «rechazado por negocio». Si el router lo reintenta cinco veces, ¿qué va a contestar la quinta?
   ¿Y si contestó 429 «me mandaste demasiadas, vuelve en 3 segundos»?
3. Se agotaron los cinco reintentos de una llamada que nunca contestó. ¿La orden «falló»? ¿Qué debería hacer alguien
   con ella?

Mira cómo está hoy:

```bash
./verifica inicio
```

La primera prueba pasa: con la API en modo `rechaza`, la 4001 queda `rechazada` con un solo intento. Pero la segunda,
con la API en modo `lento`, sale mal: la 4002 queda **`rechazada`**, y la última marca muestra que la API **sí la
tiene**, en CREADO. El router anotó «el destino dijo que no» cuando el destino nunca dijo nada, y además escribió.
Si alguien confía en esa fila y la manda otra vez a mano, o la da por perdida, se equivoca en los dos casos.

La tercera también sale mal: con la API en modo `satura`, la 4003 recibe un 429 y queda `rechazada` al primer
intento. La API no la tiene, pero la habría aceptado tres segundos después. Esa orden se queda sin mandar.

## 3. Decide por la evidencia

Abre `mio/orden-router/reparto.camel.yaml` y busca la ruta `intentar`. Casi al final, después de llamar al canal,
hay un `choice` que hoy dice: 2xx es `aceptada`; todo lo demás, `rechazada`. Arriba de él están los TODO 1 y 2.

Esta es la tabla que tiene que cumplir:

| Lo que se vio | `${variable.codigo}` | Desenlace | ¿Se reintenta? |
|---|---|---|---|
| El destino dijo que sí | 200 a 299 | `aceptada` | No hace falta |
| Ahora no, prueba después | 408, 425, 429 | `no_se_sabe` | Sí; si trae `Retry-After`, esa espera (tope 10 s) |
| El destino dijo que no | El resto de 400 a 499 | `rechazada` | No: dirá lo mismo |
| Nada claro: un 5xx, un 504 o sin respuesta | 500 a 599, o `0` | `no_se_sabe` | Sí, con la política de la 19 |

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | Dos `when` más, en este orden: uno con `${variable.codigo} in '408,425,429'` que pone `desenlace` en `no_se_sabe`, y después uno con `${variable.codigo} range '400..499'` que lo pone en `rechazada` | Que un «ahora no» se reintente y un «no» se anote como «no» |
| 2 | Que el `otherwise` ponga `no_se_sabe` en vez de `rechazada` | Que lo que no es un sí ni un no se anote como «no se sabe» |

¿Por qué en ese orden? El `choice` se queda con el **primer** `when` que se cumple, y un 429 también está entre 400 y
499. Si el `range` fuera primero, el 429 nunca llegaría a su `when`.

Fíjate en cómo encaja con la 19. Al final de `intentar` hay un `filter`: si el desenlace quedó `no_se_sabe`, lanza
una `IOException`, y esa excepción es la que atrapa tu `onException` para reintentar. **El desenlace que escribes hoy
es el que decide si se reintenta.** Un `rechazada` no lanza nada, así que no se repite.

Ahora abre `mio/orden-router/reintentos.camel.yaml`. Dentro del `onException`, antes del `log`, está el TODO 3:

| TODO | Qué escribes | Para qué |
|---|---|---|
| 3 | Un `to` con `sql:UPDATE operacion SET estado = 'conciliacion_pendiente' WHERE id = :#${variable.operacion}`, con `dataSource: "#estado"` | Que lo agotado quede marcado para que alguien lo revise |

El último intento se queda en `no_se_sabe`, porque eso es lo que pasó en ese intento. Lo que cambia es la
**operación**: ya no está esperando un reintento, está esperando a una persona.

## 4. Rompe uno a propósito

Con todo terminado, cambia tu TODO 1 para que un 4xx también sea `no_se_sabe`, como si todo lo que no es 2xx valiera
la pena repetirlo. Corre `./verifica`.

Antes, predice: la API está en modo `rechaza` y contesta 422 cada vez. ¿Cuántos intentos va a hacer el router?

Seis: el primero y cinco reintentos, y los seis contestan 422. Sale en rojo la marca del rechazo de negocio. Repetir
un «no» no lo vuelve «sí»: solo le pega seis veces a la API, detiene la cola unos treinta segundos (la 19) y al final
deja la operación en `conciliacion_pendiente`, mandando a una persona a revisar algo que el destino ya había contestado
con claridad. Regresa el `rechazada`.

## 5. Comprueba

```bash
./verifica
```

`verifica` pone la API en modo `rechaza` y manda la 4001; luego la pone en modo `lento` (contesta a los 10 segundos)
y manda la 4002; al final, en modo `satura` (dos 429 con `Retry-After: 3` y luego atiende), manda la 4003. Al salir,
la regresa a modo normal. Está terminada cuando ves las nueve marcas verdes:

```
✅ Arrancaron el router, los tres canales y el sondeo
✅ Rechazo de negocio: la 4001 quedó rechazada con un solo intento (422): un «no» no se repite
✅ Mientras la llamada va en camino, su intento ya está en la base (con huella y sin desenlace): se escribe antes de enviar
✅ No se sabe: la 4002 se intentó 6 veces, las 6 sin respuesta a tiempo (504, no_se_sabe), y quedó en conciliacion_pendiente
✅ Y la API sí tiene la 4002 (CREADO): por eso no era un fallo ni un rechazo, era «no se sabe»
✅ Ahora no: la 4003 recibió dos 429 (no_se_sabe), se reintentó y quedó aceptada al tercer intento (201): un 429 no es un «no»
✅ Y respetó el Retry-After: entre una llamada y la siguiente pasaron 3.05 3.04 s, ninguna antes de los 3 s que pidió la API
✅ La API tiene la 4003 (CREADO): los 429 no la registraron, el reintento sí
✅ Los otros canales siguieron: ERP y socio aceptaron las dos órdenes
```

Mira la tercera. La verifica se asoma a la base **mientras la llamada sigue en camino** y encuentra el intento ya
escrito, con su huella y sin desenlace. Esa marca no la escribiste hoy: existe desde la 15. Pero hoy se ve para qué
sirve: si el router se cayera en ese momento, la base diría «esto se intentó y no sé cómo le fue». Si el intento se
escribiera **después** de la llamada, no quedaría nada.

Y mira la de las esperas: los segundos salen de la bitácora de la API, la hora a la que le llegó cada llamada. Los
tuyos van a ser un poco distintos; lo que se pide es que ninguno baje de 3.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff -r mio/orden-router solucion/orden-router` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `esperaba rechazada, 1 intento y 422` y hay 6 intentos | Un 4xx se está tomando como `no_se_sabe`: revisa el `range '400..499'` |
| `esperaba conciliacion_pendiente con 6 intentos` y dice `rechazada` | El `otherwise` sigue poniendo `rechazada`: es `no_se_sabe` (TODO 2) |
| `Ahora no: esperaba aceptada con 3 intentos…` y hay `rechazada` con un intento | El 429 se tomó como «no»: el `when` de `408,425,429` va **antes** del `range '400..499'` (TODO 1) |
| `Retry-After: esperaba dos esperas de al menos 3 s…` | Algo reintenta sin esperar: revisa que no hayas tocado el `CamelRedeliveryDelay` de `intentar` ni el `onException` |
| `esperaba conciliacion_pendiente` y dice `no_se_sabe` | Falta el `UPDATE` del TODO 3, o no lleva `dataSource: "#estado"` |
| `No pude poner la API en modo rechaza` o `en modo satura` | Tu API es de antes de la 1.5.0: recréala como dice «Antes de empezar» |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**«No se sabe» es un resultado, no una falla del programa.** En cualquier sistema que habla por red hay llamadas que
se cortan a la mitad. El destino pudo haber escrito o no. Llamarle «fallo» lleva a dos errores: reenviarlo a mano
(y duplicar, si no hay idempotencia) o darlo por perdido (cuando sí llegó). En la verifica lo viste: la API registró
la 4002 diez segundos después de que el canal cortó.

**Un 4xx y un 5xx no se tratan igual.** El 4xx es el destino diciendo «lo que me mandaste está mal, o no lo acepto».
El 5xx es el destino diciendo «algo me pasó a mí». El primero no cambia si lo repites; el segundo, a veces sí. Pero
no todo 4xx es un «no»: un 408 (tardaste en mandarla), un 425 (es muy pronto) o un 429 (vas demasiado rápido) dicen
«ahora no, prueba después». Por eso aquí se reintentan, y si traen `Retry-After`, se espera lo que piden. Con un
destino real, cada código se clasifica según lo que diga su contrato; esta tabla es la de la API de la práctica.

**El intento se escribe antes de enviar.** Es la única manera de que, después de una caída, se pueda encontrar lo que
quedó en el aire. Los bancos hacen lo mismo con una transferencia: primero la anotan como «en proceso», después la
mandan.

**Conciliar es ir a ver al destino.** Cuando el router no puede saber, lo marca para alguien que sí puede: quien
tiene acceso a la API, al ERP o al socio y revisa si la orden quedó.

## Ejercicio: siete respuestas

1. Clasifica estas siete respuestas en `aceptada`, `rechazada` o `no_se_sabe`, y escribe para cada una qué evidencia
   lo decide:

   | # | Lo que regresó el canal |
   |---|---|
   | a | 201, la API creó el pedido |
   | b | 200, la API ya tenía ese pedido con esa misma llave (la 02) |
   | c | 422 `llave-reusada`: la misma llave con otro contenido |
   | d | 503, la API está en mantenimiento |
   | e | 504, el canal cortó a los 3 segundos |
   | f | Ninguna: el contenedor de int-rest se reinició a la mitad (código `0`) |
   | g | 429 con `Retry-After: 3`: la API recibió demasiadas en poco tiempo |

2. En la base, después de correr `./verifica`, mira los intentos de la 4002:
   `SELECT n, desenlace, respuesta->>'codigo' AS codigo FROM intento i JOIN operacion p ON p.id = i.operacion
   WHERE p.orden = '4002' AND p.canal = 'rest' ORDER BY n;`
   Y luego pregúntale a la API: `curl -s localhost:8081/pedidos/4002`. Escribe en una frase qué le dirías a la persona
   que tiene que conciliar esa operación.
3. Si el intento se escribiera **después** de llamar al canal y el router se cayera con la llamada en camino, ¿qué
   quedaría en la base? ¿Qué pasaría con esa orden?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Tres desenlaces, decididos por la evidencia: aceptado, rechazado o no se sabe](../decisiones/24-tres-desenlaces.md)

## Repaso

1. ¿Qué tres desenlaces puede tener un intento, y qué evidencia decide cada uno?
2. ¿Por qué un 422 no se reintenta y un 429 o un 504 sí? ¿Por qué el `when` del 429 va antes que el del rango?
3. ¿Qué es `conciliacion_pendiente`, y por qué no se llama «fallida»?
4. ¿Por qué el canal contesta 504 cuando vence su tiempo límite, en vez de un 500 o un 422?
5. **La pregunta de cierre:** el intento se escribe antes de enviar. Usa la 4002 para explicar qué se perdería si se
   escribiera después.

**Siguiente:** en la lección 21 el destino es el socio, y el socio no contesta en la misma llamada. El 850 queda en su
SFTP y el acuse llega después, en otro archivo. Depositar no es confirmar.
