# Lección 20 — Aceptado, rechazado o no se sabe

> **Lo que vas a lograr:** que el router clasifique cada respuesta del destino por lo que de verdad dijo: «sí»,
> «no» o nada claro. Que un «no» no se repita, y que lo que se quedó sin respuesta, al agotar los reintentos, quede
> marcado para que una persona lo revise, nunca como un fallo.

**Antes de empezar necesitas:** la lección 19 terminada y el ambiente de la 00 encendido. Esta lección usa la API
1.4.0, que tiene dos modos de prueba: `rechaza` y `lento`. Si tu API es de antes, recréala una vez:

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

## 2. Predice

Contesta en un papel:

1. La API tardó 10 segundos y el canal cortó a los 3. ¿Escribió o no escribió la orden? ¿Cómo lo sabrías?
2. La API contestó 422 «rechazado por negocio». Si el router lo reintenta cinco veces, ¿qué va a contestar la quinta?
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

## 3. Decide por la evidencia

Abre `mio/orden-router/reparto.camel.yaml` y busca la ruta `intentar`. Casi al final, después de llamar al canal,
hay un `choice` que hoy dice: 2xx es `aceptada`; todo lo demás, `rechazada`. Arriba de él están los TODO 1 y 2.

Esta es la tabla que tiene que cumplir:

| Lo que se vio | `${variable.codigo}` | Desenlace | ¿Se reintenta? |
|---|---|---|---|
| El destino dijo que sí | 200 a 299 | `aceptada` | No hace falta |
| El destino dijo que no | 400 a 499 | `rechazada` | No: dirá lo mismo |
| Nada claro: un 5xx, un 504 o sin respuesta | 500 a 599, o `0` | `no_se_sabe` | Sí, con la política de la 19 |

| TODO | Qué escribes | Para qué |
|---|---|---|
| 1 | Un segundo `when` con `${variable.codigo} range '400..499'` que pone `desenlace` en `rechazada` | Que un «no» se anote como «no» |
| 2 | Que el `otherwise` ponga `no_se_sabe` en vez de `rechazada` | Que lo que no es un sí ni un no se anote como «no se sabe» |

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
y manda la 4002. Al salir, la regresa a modo normal. Está terminada cuando ves las seis marcas verdes:

```
✅ Arrancaron el router, los tres canales y el sondeo
✅ Rechazo de negocio: la 4001 quedó rechazada con un solo intento (422): un «no» no se repite
✅ Mientras la llamada va en camino, su intento ya está en la base (con huella y sin desenlace): se escribe antes de enviar
✅ No se sabe: la 4002 se intentó 6 veces, las 6 sin respuesta a tiempo (504, no_se_sabe), y quedó en conciliacion_pendiente
✅ Y la API sí tiene la 4002 (CREADO): por eso no era un fallo ni un rechazo, era «no se sabe»
✅ Los otros canales siguieron: ERP y socio aceptaron las dos órdenes
```

Mira la tercera. La verifica se asoma a la base **mientras la llamada sigue en camino** y encuentra el intento ya
escrito, con su huella y sin desenlace. Esa marca no la escribiste hoy: existe desde la 15. Pero hoy se ve para qué
sirve: si el router se cayera en ese momento, la base diría «esto se intentó y no sé cómo le fue». Si el intento se
escribiera **después** de la llamada, no quedaría nada.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff -r mio/orden-router solucion/orden-router` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `esperaba rechazada, 1 intento y 422` y hay 6 intentos | Un 4xx se está tomando como `no_se_sabe`: revisa el `range '400..499'` |
| `esperaba conciliacion_pendiente con 6 intentos` y dice `rechazada` | El `otherwise` sigue poniendo `rechazada`: es `no_se_sabe` (TODO 2) |
| `esperaba conciliacion_pendiente` y dice `no_se_sabe` | Falta el `UPDATE` del TODO 3, o no lleva `dataSource: "#estado"` |
| `No pude poner la API en modo rechaza` | Tu API es de antes: recréala como dice «Antes de empezar» |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**«No se sabe» es un resultado, no una falla del programa.** En cualquier sistema que habla por red hay llamadas que
se cortan a la mitad. El destino pudo haber escrito o no. Llamarle «fallo» lleva a dos errores: reenviarlo a mano
(y duplicar, si no hay idempotencia) o darlo por perdido (cuando sí llegó). En la verifica lo viste: la API registró
la 4002 diez segundos después de que el canal cortó.

**Un 4xx y un 5xx no se tratan igual.** El 4xx es el destino diciendo «lo que me mandaste está mal, o no lo acepto».
El 5xx es el destino diciendo «algo me pasó a mí». El primero no cambia si lo repites; el segundo, a veces sí. Ojo: no todo 4xx es igual. Un 408 (se acabó el tiempo) o
un 429 (vas demasiado rápido) piden repetir más tarde. Aquí se trata todo el rango como rechazo porque la API de la
práctica solo contesta el 422 de negocio; con un destino real, cada código se clasifica según lo que diga su contrato.

**El intento se escribe antes de enviar.** Es la única manera de que, después de una caída, se pueda encontrar lo que
quedó en el aire. Los bancos hacen lo mismo con una transferencia: primero la anotan como «en proceso», después la
mandan.

**Conciliar es ir a ver al destino.** Cuando el router no puede saber, lo marca para alguien que sí puede: quien
tiene acceso a la API, al ERP o al socio y revisa si la orden quedó.

## Ejercicio: seis respuestas

1. Clasifica estas seis respuestas en `aceptada`, `rechazada` o `no_se_sabe`, y escribe para cada una qué evidencia
   lo decide:

   | # | Lo que regresó el canal |
   |---|---|
   | a | 201, la API creó el pedido |
   | b | 200, la API ya tenía ese pedido con esa misma llave (la 02) |
   | c | 422 `llave-reusada`: la misma llave con otro contenido |
   | d | 503, la API está en mantenimiento |
   | e | 504, el canal cortó a los 3 segundos |
   | f | Ninguna: el contenedor de int-rest se reinició a la mitad (código `0`) |

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
2. ¿Por qué un 4xx no se reintenta y un 504 sí?
3. ¿Qué es `conciliacion_pendiente`, y por qué no se llama «fallida»?
4. ¿Por qué el canal contesta 504 cuando vence su tiempo límite, en vez de un 500 o un 422?
5. **La pregunta de cierre:** el intento se escribe antes de enviar. Usa la 4002 para explicar qué se perdería si se
   escribiera después.

**Siguiente:** en la lección 21 el destino es el socio, y el socio no contesta en la misma llamada. El 850 queda en su
SFTP y el acuse llega después, en otro archivo. Depositar no es confirmar.
