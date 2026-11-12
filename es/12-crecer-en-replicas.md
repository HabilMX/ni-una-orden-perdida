# Lección 12 — Crecer en réplicas

> **Lo que vas a lograr:** correr **tres copias** de int-bd sin tocar los demás servicios, medir cómo se reparten
> las órdenes entre ellas, y descubrir por qué al principio no se reparten.

**Antes de empezar necesitas:** la lección 11 terminada y el ambiente de la 00 encendido (con el ERP recreado en la
10).

## Lo nuevo de hoy, en una frase

Llega el Buen Fin y la tienda manda el triple de órdenes. El cuello de botella es int-bd, que escribe en el ERP.
Hoy **escalas un solo servicio**: en vez de una copia de int-bd corres tres, y el router las sigue llamando por el
mismo nombre, `int-bd`.

**La imagen:** una caja de banco que abre dos ventanillas más. Los clientes siguen formándose en la misma fila,
y el banco pone a alguien que los mande a una ventanilla libre. Hoy vas a ver qué pasa cuando ese alguien no hace
bien su trabajo.

Una **réplica** es una copia idéntica del mismo servicio: la misma imagen, la misma carpeta, las mismas rutas.
int-bd se puede copiar sin cuidado porque **no guarda nada en memoria**: todo lo que importa queda en el ERP.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/12-crecer-en-replicas
cp -R inicio mio
```

Hoy **no tocas ninguna ruta**. Todo el trabajo va en un solo archivo, `mio/replicas.yaml`. Compose lo junta con el
`compose.yaml` de la lección, que no se toca: lo que dice el segundo archivo se agrega a lo del primero o lo
reemplaza.

```bash
DONDE=mio docker compose -f compose.yaml -f mio/replicas.yaml up -d
```

Es la misma idea del `franquicias.yaml` de la 11: cambiar cómo corre un servicio sin cambiar el servicio.

## 2. Predice

Hoy usas un lote nuevo: `datos/ordenes/lote-30/`, el lote de 30 órdenes, todas de tiendas propias (el socio no
recibe ninguna). Contesta en un papel:

1. Si corres tres copias de int-bd y mandas las 30 órdenes, ¿cuántas registra cada copia?
2. ¿Quién decide a qué copia va cada orden: el router, Camel, Docker o el ERP?

Comprueba cómo está hoy:

```bash
./verifica inicio
```

Salen dos marcas rojas: `int-bd corre en 1 copia(s) y se esperaban 3`, y `Con una sola copia no hay nada que
repartir`. El verifica imprime el reparto, y hoy es `int-bd-1=30`: una sola copia hace todo.

## 3. Pide tres copias

En `mio/replicas.yaml`, el TODO 1. Pide tres copias de int-bd con `deploy` → `replicas`:

```yaml
services:
  int-bd:
    deploy:
      replicas: 3
```

Corre el verifica:

```bash
./verifica
```

Mira la línea `Reparto:`. Lo que vimos al probarla:

```
   Reparto: practica-retail-l12-int-bd-1=0 practica-retail-l12-int-bd-2=0 practica-retail-l12-int-bd-3=30
❌ 2 de 3 copias no registraron nada: el router sigue usando la misma conexión.
```

Las tres copias están arriba, pero **una hace todo y dos no hacen nada**. ¿Acertaste el 10/10/10? Casi nadie lo
acierta, porque parece obvio. Antes de seguir, **escribe tu explicación**: ¿por qué no se repartieron?

## 4. Por qué no se reparten

Hay dos razones, y van juntas.

**La conexión se reusa.** Abrir una conexión HTTP cuesta tiempo, así que el router abre **una** hacia `int-bd` y la
deja abierta (*keep-alive*). Todas las órdenes que siguen viajan por esa misma conexión, y una conexión va a una sola
copia. Docker no reparte peticiones: reparte **conexiones**.

**La JVM recuerda la dirección.** Cuando el router pregunta «¿dónde está `int-bd`?», el DNS de Docker contesta las
tres direcciones, en distinto orden cada vez. Pero la JVM del router guarda la primera respuesta y no vuelve a
preguntar en un buen rato.

| | Lo que hace hoy | Lo que necesitas |
|---|---|---|
| La conexión | Vive para siempre | Que venza pronto, para abrir otra |
| La dirección de `int-bd` | La JVM la recuerda | Que la olvide pronto, para volver a preguntar |

Con las dos cortas, cada conexión nueva vuelve a preguntar la dirección y puede caer en otra copia.

## 5. Haz que el router reparta

El TODO 2, en el mismo `mio/replicas.yaml`, dentro de `orden-router`. Son dos cosas:

**a) Que la conexión viva a lo más un segundo.** Es la opción `connectionTimeToLive` del componente `http`, en
milisegundos. Se le pasa al router al arrancar:

```yaml
    command: ["run", ".", "--prop=camel.component.http.connectionTimeToLive=1000"]
```

**b) Que la JVM recuerde la dirección un segundo.** Va en una variable de entorno que la JVM lee al arrancar:

```yaml
    environment:
      JDK_JAVA_OPTIONS: "-Dsun.net.inetaddr.ttl=1"
```

Antes de poner la b, haz la prueba: **pon solo la a** y corre `./verifica`. Al probar la lección, solo con la a el
reparto siguió en `0/0/30`. La conexión ya vencía, pero la conexión nueva iba a la misma dirección que la JVM tenía
guardada. Ahora agrega la b y corre otra vez.

## 6. Comprueba

```bash
./verifica
```

`verifica` reinicia la API, vacía el ERP, levanta los servicios con tu `replicas.yaml` y manda las 30 órdenes, una
cada segundo y medio, como llegarían de una tienda. Está terminada cuando ves las seis marcas verdes:

```
✅ int-bd corre en 3 copias
✅ Arrancaron el router, int-rest, int-edi y las copias de int-bd
✅ ERP: tiene las 30 órdenes REGISTRADO, cada una una sola vez
✅ API: tiene las 30 órdenes (crecer int-bd no tocó a los demás)
   Reparto: practica-retail-l12-int-bd-1=12 practica-retail-l12-int-bd-2=12 practica-retail-l12-int-bd-3=6
✅ Entre todas las copias registraron 30 órdenes: ni una de más, ni una de menos
✅ Cada copia de int-bd registró al menos una orden: el trabajo se repartió
```

**Tu reparto va a ser distinto**, y cambia en cada corrida: al probarla vimos 12/12/6 y 6/11/13. Quien elige la copia
es la red, y la red no reparte exacto. Por eso el verifica no pide 10/10/10: pide que **ninguna copia se quede sin
trabajo** y que **ninguna orden se pierda ni se repita**. Cada copia cuenta lo suyo en su propio log, así que el
reparto lo dicen las copias, no el router.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y `diff mio/replicas.yaml solucion/replicas.yaml` te dice
en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `int-bd corre en 1 copia(s)` | Falta el `deploy` → `replicas: 3`, o quedó debajo de otro servicio. Revisa la sangría |
| `2 de 3 copias no registraron nada` | Te falta la a, la b o las dos (paso 5) |
| `No pude levantar los servicios. Revisa que mio/replicas.yaml sea YAML válido` | Un error de sangría o de comillas; el verifica te enseña las últimas líneas del error |
| Pusiste `CAMEL_COMPONENT_HTTP_CONNECTION_TIME_TO_LIVE` en `environment` y no reparte | En esta versión esa variable no surte efecto (lo medimos: 0/29/0). Usa el `--prop` del paso 5 |
| `Tu ERP no tiene la columna correlacion` | Recrea el ERP como dice la 10 |

---

## Lo que esto enseña fuera de la práctica

**Se reparte por conexión, y una conexión eterna no se reparte.** En Docker reparte el DNS. En Kubernetes, un
Service también elige una copia por cada conexión nueva, así que el mismo problema aparece igual. Cambia quién
reparte; la regla no.

**El segundo es para que lo veas aquí**, no una cifra para un sistema real. Cuánto debe vivir una conexión depende
de cuánto tráfico hay y de qué tan seguido cambian las copias. Cerrarla después de cada orden también reparte, pero
pagas el costo de abrir una conexión en cada llamada.

## Ejercicio: crecer sin tocar a nadie

Con tu versión terminada, contesta solo con lo que ves en pantalla:

1. Cambia `replicas: 3` por `replicas: 5` y corre `./verifica`. ¿Qué marca se pone roja, y por qué? (Pista: el
   verifica espera 3.) ¿Cuántas órdenes registró cada una de las cinco?
2. Regresa a 3. ¿Qué archivos tocaste para pasar de una copia a tres? ¿Tocaste alguna ruta, algún mapeo o el
   `compose.yaml` de la lección?
3. ¿Por qué int-bd se puede copiar tres veces sin que una orden se registre dos veces? ¿Qué pasaría si cada copia
   guardara en su memoria las órdenes que ya vio?

---

## Lo que ya funciona

Con esta lección cierras la entrega 1. Una orden que la tienda crea ya hace todo esto:

- llega al router por HTTP y el router le contesta qué dijo cada destino;
- llega a la API una sola vez, aunque se reenvíe, gracias a su llave;
- entra al ERP completa o no entra, y el ERP puede crecer a varias copias;
- se vuelve un 850 que llega al socio sin pisarse ni quedar a medias, solo si la tienda es suya;
- lleva un hilo que la sigue por todos los servicios;
- y si un destino se cae, los demás la reciben igual.

Lo que todavía no hace: recordar en qué va cada orden. Eso empieza en la entrega 2.

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Tres copias no reparten solas: el reparto es por conexión](../decisiones/15-repartir-por-conexion.md)
- [Una sola imagen; cada servicio es una carpeta](../decisiones/09-una-imagen-un-servicio-por-carpeta.md)

## Repaso

1. ¿Qué es una réplica, y por qué int-bd se puede copiar sin cuidado?
2. Con `replicas: 3` y nada más, ¿cómo se repartieron las órdenes? ¿Por qué?
3. ¿Qué hace `connectionTimeToLive`, y qué hace `sun.net.inetaddr.ttl`? ¿Por qué con una sola no basta?
4. ¿Por qué el verifica no pide 10/10/10, y qué pide en su lugar?
5. **La pregunta de cierre:** si el router tuviera réplicas, ¿dónde viviría lo de cada orden? Escribe tu respuesta:
   la vas a comparar con la lección 15.

**Siguiente:** en la lección 13 empieza la entrega 2: llega el embarque. La bodega deja un archivo, y ahora que
sabes correr copias, la pregunta es otra: si varias copias leen la misma carpeta, ¿quién toma el archivo?
