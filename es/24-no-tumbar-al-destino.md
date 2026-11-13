# Lección 24 — No tumbar al destino

> **Lo que vas a lograr:** que el ERP nunca reciba más registros al mismo tiempo de los que aguanta, aunque int-bd
> corra en tres copias y la tienda mande 30 órdenes de golpe. Lo que pase del tope espera su turno: no se rechaza.

**Antes de empezar necesitas:** las lecciones 12 y 22 terminadas y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 22 el problema era un destino caído. Hoy es al revés: el destino está bien, y **lo puedes tumbar tú**. Desde
esta lección el router reparte hasta 10 órdenes a la vez (abajo te explicamos por qué), e int-bd corre en 3 copias,
como en la 12. Con 30 órdenes de golpe y un ERP que tarda un segundo y medio en registrar cada una, el ERP llega a
tener 10 registros en curso. Un ERP real aguanta un número de conexiones de integración; pasado ese número se pone
lento para todos, incluidos los usuarios que están capturando a mano. Hoy le pones un **tope**: el patrón se llama
*Throttler*.

**La imagen:** la entrada de un elevador con cupo para seis. Hay tres puertas en el lobby, y en cada una un guardia
que deja pasar a dos a la vez. El séptimo no se va: espera en su fila a que alguien salga. Si el edificio abre una
cuarta y una quinta puerta, cada una con su guardia de dos, al elevador le pueden llegar diez. **El cupo es por
puerta**, y al abrir puertas hay que volver a hacer la cuenta.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/24-no-tumbar-al-destino
cp -R inicio mio
```

Antes de escribir, dos cosas que ya están hechas y tienes que conocer.

**El reparto ahora atiende 10 órdenes a la vez.** Abre `mio/orden-router/reparto.camel.yaml` y lee el comentario
de la ruta `repartir`: su `from` lleva `concurrentConsumers: 10`. Hasta la 23 había un solo consumidor, y por eso en
la 19 dijimos que reintentar en el lugar conservaba el orden. **Desde hoy eso cambia:**

| | Hasta la 23 | Desde la 24 |
|---|---|---|
| Órdenes que se reparten a la vez | 1 | Hasta 10 |
| Las operaciones de **una** orden | En fila | En fila, igual que antes |
| Dos órdenes **distintas** | En fila | Se pueden cruzar |
| Dos momentos de la misma orden que se cruzan | No pasaba | Los cuida la máquina de estados de la 17: lo que llega antes se estaciona |

¿Por qué el cambio? Porque con un solo consumidor, un lote de 30 órdenes tarda 30 veces lo que tarde el destino más
lento. El precio es el problema de hoy: el router ya le puede mandar 10 a la vez a un destino.

**El ERP mide lo que ve.** Cada vez que el ERP registra un pedido, anota en la tabla `muestra_concurrencia` cuántos
registros tenía en curso en ese momento. La prueba del tope no es lo que escribiste en la ruta: es el máximo de esa
columna.

Tu trabajo va en `mio/int-bd/int-bd.camel.yaml`, en la ruta `int-bd`, al principio de sus pasos. Abre también el
`compose.yaml` de la lección y busca `replicas: 3`.

## 2. Predice

Contesta en un papel:

1. Recuerda la 12. Con int-bd en 3 copias y sin tope, ¿cuántos registros a la vez puede ver el ERP? (Pista: el
   router manda hasta 10.)
2. El ERP aguanta 6 a la vez. ¿Cuántos tiene que dejar pasar **cada** copia de int-bd?
3. Con el tope puesto, ¿las 30 órdenes tardan más o menos en registrarse? ¿Se pierde alguna?

Mira cómo está hoy:

```bash
./verifica inicio
```

El ERP registra las 30, y rápido. Pero la marca de la concurrencia sale en rojo: el ERP vio **hasta 10** registros a
la vez, cuando el máximo con 3 copias y un tope de 2 sería 6.

## 3. Pon el tope

En la ruta `int-bd` está el TODO. Lo que escribes es un solo paso, al principio, antes de todo lo demás:

```yaml
- throttle:
    mode: ConcurrentRequests
    expression:
      constant:
        expression: "…"        # cuántos a la vez, POR COPIA
```

El número lo calculas tú, con la cuenta del elevador:

| Dato | Valor |
|---|---|
| Lo que aguanta el ERP | 6 a la vez |
| Copias de int-bd (`compose.yaml`) | 3 |
| Tope por copia | 6 ÷ 3 = **2** |

Fíjate en el `mode`. Camel tiene dos formas de contar, y **la que trae por omisión no es la de hoy**:

| Modo | Qué cuenta | ¿Sirve para el ERP? |
|---|---|---|
| `TotalRequests` (por omisión) | Cuántos **entran** por periodo, por ejemplo 2 por segundo | No: si cada registro tarda 1.5 s, entran 2 cada segundo y se van juntando |
| `ConcurrentRequests` | Cuántos **van en curso**: entraron y todavía no contestan | Sí: es justo lo que aguanta el ERP |

Por eso el `mode` se escribe aunque parezca un detalle. Sin él, el tope existe, pero cuenta otra cosa.

Y fíjate en lo que **no** hace el tope: no rechaza. El tercer pedido que llega a una copia espera ahí su turno hasta
que uno de los dos que van en curso conteste. Para el router, esa llamada solo tardó un poco más: no es un error, y
no la reintenta.

## 4. Rompe uno a propósito

Con tu tope terminado, borra la línea `mode: ConcurrentRequests` y corre `./verifica`.

Antes, predice: el tope sigue diciendo «2». ¿El ERP ve a lo más 6?

No. Sin `mode`, Camel usa `TotalRequests`: cada copia deja **entrar** 2 pedidos por segundo, sin importar cuántos
siguen en curso. Como cada registro tarda un segundo y medio, se van juntando, y el ERP vuelve a ver más de 6. Sale en
rojo la marca de la concurrencia. El número era el correcto; lo que estaba mal era **qué contaba**. Regresa la línea.

## 5. Comprueba

```bash
./verifica
```

`verifica` levanta int-bd en 3 copias, manda 30 órdenes de golpe y luego le pregunta al ERP cuántos registros vio a
la vez. Está terminada cuando ves las cinco marcas verdes:

```
✅ Arrancaron el router, los tres canales (int-bd en 3 copias) y el sondeo
✅ El ERP registró las 30 órdenes en (…) s
✅ El ERP vio a lo más (…) registros a la vez: no pasó de 3 copias × tope 2 = 6
✅ El tope hizo esperar, no rechazó: las 30 operaciones del ERP aceptadas al primer intento
✅ La API recibió las 30: el tope del ERP no frenó a los otros canales
   Registros por copia de int-bd: (…)
```

El último renglón no es una marca: te dice cuántos registros hizo cada copia. Las tres trabajaron.

Compara la segunda marca con la de `inicio/`. Con el tope, las 30 tardan más: en nuestras corridas, unos 16 segundos
contra unos 6. **Eso es lo que cuesta cuidar al ERP**, y es un precio razonable: el ERP sigue sirviendo a los demás.

Mira la cuenta del ERP tú mismo:

```bash
docker compose -f ../../infra/compose.yaml exec erp mariadb -uintegracion -ppractica erp \
  -e "SELECT MAX(en_curso) AS maximo, COUNT(*) AS registros FROM muestra_concurrencia"
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/int-bd/int-bd.camel.yaml solucion/int-bd/int-bd.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `El ERP vio hasta 10 registros a la vez` | No hay `throttle`, o quedó después de la llamada al ERP: va al principio de los pasos |
| `El ERP vio hasta` más de 6, pero menos de 10 | Falta `mode: ConcurrentRequests`, o el tope no es 2 |
| `int-bd corre en 1 copia(s) y se esperaban 3` | Cambiaste el `replicas` del `compose.yaml`: regrésalo a 3 |
| `El puerto 8080 está ocupado` | Quedaron arriba los servicios de otra corrida: `DONDE=mio docker compose -f compose.yaml down` |

---

## Lo que esto enseña fuera de la práctica

**El destino tiene un límite aunque no lo diga.** Un ERP, una base de datos o una API de un tercero aguantan cierto
número de conexiones. Una integración que crece en copias y en concurrencia sin tope es la forma más común de tumbar
a un sistema que estaba bien.

**El tope va donde se conoce al destino.** int-bd es el único que habla con el ERP, así que él es quien sabe cuánto
aguanta. Poner el tope en el router mezclaría lo que sabe de todos los destinos en un solo lugar.

**El tope es por copia, y escalar obliga a recalcular.** Si mañana int-bd corre en 5 copias, el mismo tope de 2 deja
pasar 10. La cuenta es: lo que aguanta el destino entre las copias que le hablan.

**Hacer esperar no es rechazar.** Un tope que rechaza obliga a quien llama a reintentar, y con eso se gana poco. Un
tope que hace esperar solo vuelve la llamada más lenta, y para el router sigue siendo un «sí».

## Ejercicio: el elevador con más puertas

1. En papel: el ERP aguanta 6 y int-bd corre en 3 copias con tope 2. Si el ERP de producción aguantara 20 y quisieras
   correr 4 copias, ¿qué tope pondrías? ¿Y con 6 copias? Escribe la cuenta.
2. Cambia en el `compose.yaml` las réplicas de int-bd a `5`, sin tocar el tope, y corre `./verifica`. La primera
   marca va a salir en rojo, porque la verifica espera 3. Lee la tercera: ¿cuántos registros a la vez vio el ERP? ¿Pasó
   de 6? Escribe qué cambiarías en `int-bd.camel.yaml` para que no pasara, y regresa las réplicas a 3.
3. Desde hoy el reparto atiende 10 órdenes a la vez. Explica en dos frases por qué eso no desordena los momentos de
   **una** misma orden. (Pista: la 17.)

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Un tope por copia hacia el ERP, contando lo que va en curso](../decisiones/28-un-tope-por-copia.md)

## Repaso

1. ¿Qué es un Throttler, y qué problema resuelve aquí?
2. ¿Por qué el tope va en int-bd y no en el router?
3. ¿Qué diferencia hay entre `TotalRequests` y `ConcurrentRequests`? ¿Por qué hoy sirve el segundo?
4. ¿Qué le pasa a un pedido que llega cuando su copia ya tiene 2 en curso?
5. **La pregunta de cierre:** el ERP aguanta 6 y hoy corre con 3 copias. ¿Cuántos registros a la vez puede ver con
   5 copias y el mismo tope, y qué tendrías que cambiar al escalar?

**Siguiente:** en la lección 25 la pregunta es otra: la API y el ERP ya aceptaron y el socio no ha mandado su 997.
¿Cuándo está **completa** una orden?
