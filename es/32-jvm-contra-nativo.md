# Lección 32 — JVM contra nativo, medido

> **Apéndice opcional.** Se puede **leer** sin correrla.
>
> **Lo que vas a lograr:** construir el mismo int-rest de dos formas, para la JVM y como ejecutable nativo, y medir en
> tu máquina qué cambia: cuánto tarda cada uno en construirse, en estar listo para atender y cuánta memoria usa. Y
> medirlo **bien**: que cada cifra diga lo que dice que mide.

**Antes de empezar, revisa tu equipo.** Compilar a nativo es lo más pesado de toda la práctica. En la máquina donde lo
medimos (Mac con Apple M5, Docker con 6 CPU y 16 GiB), el compilador llegó a usar 4.6 GB de memoria y 4.6 núcleos, y
la construcción nativa tardó 202 segundos desde cero. El detalle está en
[`lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md`](../lecciones/31-de-laboratorio-a-kubernetes/CAPACIDAD.md).

**Necesitas además:** la lección 31 leída (esta usa su `Dockerfile`) y, si no conoces el modo nativo,
[`conceptos/antes-de-empezar/07-nativo.md`](../conceptos/antes-de-empezar/07-nativo.md). No necesitas kind ni el
ambiente de la 00: la lección trae su propio `compose.yaml` con la API, los dos int-rest y un cliente de carga.

## Lo nuevo de hoy, en una frase

Hasta hoy, cada servicio de la práctica corría sobre la JVM. Camel Quarkus también puede compilarse **a nativo**: un
ejecutable que arranca sin JVM. Se dice mucho de cuánto más rápido arranca y cuánta memoria ahorra. Hoy no lo crees:
**lo mides**, con un método fijo, en tu máquina. La dificultad nueva no es el nativo: es **medir bien**.

**La imagen:** comparar dos coches. Si uno lo mides con el motor ya caliente y el otro en frío, o a uno le cuentas
desde que arranca el motor y al otro desde que se sube el conductor, el resultado no dice nada de los coches. Para
comparar, los dos van por la misma pista, con la misma carga y el mismo cronómetro.

| Lo que se mide | Con qué | Por qué así |
|---|---|---|
| Construir | El reloj, con `--no-cache` | Que ninguno reuse lo de la vez anterior |
| Lo que dice Quarkus | La línea `started in …` de su bitácora | Para compararla con la siguiente |
| Hasta sano | Desde que se **pide** el contenedor hasta que `/q/health/ready` contesta | Es lo que espera quien necesita que la copia atienda |
| Memoria en reposo | `docker stats`, ya arrancado, sin trabajo | Lo que ocupa esperando |
| Memoria con carga | `docker stats` cada segundo **durante** una carga de 100 órdenes por segundo, 20 s; la más alta | Lo que ocupa trabajando |
| Imagen | `docker image inspect` | Lo que pesa bajarla |

Los dos contenedores tienen el mismo techo (1 CPU y 512 MiB) y se miden **uno a la vez**, para que uno no le quite CPU
al otro.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/32-jvm-contra-nativo
cp -R inicio mio
```

Tu trabajo es un script: `mio/medir`. Ya construye las dos imágenes, levanta cada modo, lanza la carga y escribe la
tabla en `mio/cifras.md`. Le faltan las tres funciones que **miden**. Ábrelo y lee de arriba abajo antes de escribir.

Abre también `compose.yaml`: los dos int-rest salen del mismo `Dockerfile` de la 31, con dos `target` distintos
(`jvm` y `nativo`), y escuchan en `8091` y `8092`.

## 2. Predice

Escribe en un papel, **antes** de medir:

1. ¿Cuántas veces más rápido crees que arranca el nativo? ¿Y cuánta memoria menos usa en reposo?
2. ¿Cuál de los dos crees que tarda más en **construirse**?
3. Quarkus dice en su bitácora `started in 0.05s`. ¿Es eso lo que tarda la copia en poder atender una orden?

Mira cómo está hoy (tarda unos 7 minutos: construye las dos imágenes desde cero):

```bash
./verifica inicio
```

Las imágenes se construyen, arrancan y entregan su orden a la API. Pero en `cifras.md` faltan «hasta sano»,
«memoria en reposo» y «memoria con carga»: las funciones que miden todavía contestan `—`.

## 3. Las tres medidas

### TODO 1: la memoria de un contenedor

`docker stats` la da así: `145.8MiB / 512MiB`. Pide una sola lectura, solo esa columna, y quédate con el primer
número, en MiB:

```bash
memoria() {
  docker stats --no-stream --format '{{.MemUsage}}' "$1" 2>/dev/null | awk '{
    v=$1; n=v+0
    if (v ~ /GiB/) n=n*1024; else if (v ~ /KiB/) n=n/1024
    printf "%.1f\n", n }'
}
```

`n=v+0` convierte `145.8MiB` en `145.8`: awk toma los dígitos del principio. Lo demás es cambiar de unidad.

### TODO 2: hasta sano

Toma la hora **antes** de pedir el contenedor y pregunta cada 50 ms por `/q/health/ready`, a lo más 60 segundos:

```bash
hasta_sano() {
  local modo=$1 t0 p
  p=$(puerto "$modo"); t0=$(ms)
  C up -d --force-recreate "int-rest-$modo" >/dev/null 2>&1
  for _ in $(seq 1 1200); do
    curl -sf -o /dev/null --max-time 1 "localhost:$p/q/health/ready" && { echo $(( $(ms) - t0 )); return 0; }
    sleep 0.05
  done
  echo "—"
}
```

¿Por qué no basta con el `started in` de la bitácora? Porque eso lo dice Quarkus de sí mismo: no cuenta lo que tarda
el contenedor en nacer. Quien espera que la copia atienda, espera las dos cosas.

### TODO 3: la memoria con carga

La carga corre en segundo plano. **Mientras** sigue viva, toma la memoria cada segundo y quédate con la más alta:

```bash
while kill -0 "$carga" 2>/dev/null; do
  m=$(memoria "$cont"); awk -v a="$m" -v b="$tope" 'BEGIN{exit !(a+0 > b+0)}' && tope=$m
  sleep 1
done
```

`kill -0` no mata nada: solo pregunta si el proceso sigue vivo. Va antes del `wait`.

## 4. Rompe uno a propósito

Con tu `medir` terminado, cambia el TODO 3: borra el `while` y, después del `wait`, toma **una** sola lectura
(`tope=$(memoria "$cont")`). Antes de correrlo, copia tu `mio/cifras.md` a otro lado. Corre `./verifica`.

Antes, predice: ¿la verifica lo detecta?

No: sale verde. «Memoria con carga» sigue siendo un número, y la verifica solo pide que lo sea. Compara con la copia
que guardaste: la cifra nueva es la memoria **después** de la carga, y puede salir más baja que la verdadera. **Una medición mal
hecha no falla: devuelve un número y suena a hecho.** Por eso la verifica no juzga tus cifras contra ningún umbral
(ficha 39), y por eso el método se escribe junto a la tabla. Regresa el `while`.

## 5. Comprueba

```bash
./verifica
```

`verifica` corre tu `mio/medir`, que construye las dos imágenes desde cero y mide cada una. Luego le manda una orden a
cada imagen y revisa en la bitácora de la API que llegó, comprueba que la nativa no trae JVM y revisa que `cifras.md`
se escribió **en esta corrida** con las seis cifras de cada modo. Está terminada cuando ves las marcas verdes:

```
✅ La imagen jvm arrancó, contestó y su orden (…) llegó a la API
✅ La imagen nativo arrancó, contestó y su orden (…) llegó a la API
✅ Control: la imagen nativa no trae JVM; corre un ejecutable de (…) MB
✅ Cifras de jvm medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
✅ Cifras de nativo medidas: construir (…)s · Quarkus (…)s · sano (…) ms · reposo (…) MiB · carga (…) MiB · imagen (…) MB
```

Las cifras son **tuyas**. En la máquina donde lo probamos, la JVM estuvo sana en unos 1,700 ms con unos 108 MiB en
reposo, y el nativo en unos 240 ms con unos 16 MiB. A cambio, el nativo tardó más en construirse. En tu máquina serán
otras, y eso está bien: compara contra las tuyas, no contra las nuestras.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y `diff mio/medir solucion/medir` te dice en qué son
distintas.

| Lo que ves | Qué haces |
|---|---|
| `El puerto … está ocupado` | Apaga lo que dejaste corriendo y vuelve a intentar |
| `Tu mio/medir terminó con error` | Corre `./mio/medir` solo: te dice en qué paso se detuvo |
| `No existe la imagen ni-una-orden-perdida/int-rest:nativo: no se construyó` | Lee `mio/build-nativo.log`. Si se quedó sin memoria, revisa `CAPACIDAD.md`: el `Dockerfile` permite bajar `MEMORIA_NATIVO`, y tarda más |
| `La imagen … no entregó su orden … a la API` | Las líneas que siguen son la bitácora de int-rest: dicen por qué |
| `Tu medir no escribió mio/cifras.md` o `no se escribió en esta corrida` | `medir` se detuvo antes de escribir la tabla: córrelo solo |
| `A las cifras de … les falta (o no es un número): hasta sano` | Falta el TODO 2, o `/q/health/ready` nunca contestó en 60 s |
| `… les falta …: memoria en reposo, memoria con carga` | Falta el TODO 1 (y con él, el 3 tampoco mide) |

---

## Lo que esto enseña fuera de la práctica

**Una cifra sin su método no se compara.** «Arranca en 50 ms» puede ser lo que dice el programa de sí mismo o lo que
espera quien lo usa. Las dos son ciertas y no son la misma cifra.

**El nativo cambia un costo por otro.** Arranca rápido y usa poca memoria, y tarda más en construirse y pide una
máquina grande para compilar. Conviene donde se arrancan muchas copias seguido (como los picos de la 31); en un
servicio que corre semanas sin reiniciar, la diferencia de arranque casi no se nota.

**Las cifras publicadas son de otra máquina.** Las de un artículo, las nuestras y las tuyas son de equipos distintos.
Sirven para saber qué esperar; para decidir, se mide en el equipo donde va a correr.

**Que la prueba salga verde no prueba que mediste bien.** La verifica puede comprobar que hay un número, no que el
número sea el correcto. El método es lo que lo sostiene.

## Ejercicio: la misma pista

1. Escribe en `mio/cifras.md`, debajo de la tabla, tus predicciones del paso 2 y en qué te equivocaste.
2. Corre `./mio/medir` dos veces seguidas. ¿Cuánto cambian «hasta sano» y «memoria con carga» entre una corrida y otra?
   ¿Qué te dice eso de comparar una sola corrida?
3. En papel: tu equipo tiene un servicio que se escala de 1 a 10 copias en los picos de la mañana, y otro que corre
   sin reiniciar todo el mes. Con **tus** cifras, ¿cuál compilarías a nativo y por qué?

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [El nativo se compila con Mandrel dentro de un contenedor](../decisiones/38-el-nativo-se-compila-en-un-contenedor.md)
- [JVM contra nativo se mide en tu máquina, con un método fijo y sin umbrales](../decisiones/39-se-mide-en-tu-maquina-sin-umbrales.md)

## Repaso

1. ¿Qué es un ejecutable nativo, y qué le falta que la JVM sí tiene?
2. ¿Por qué «hasta sano» y «started in» son cifras distintas?
3. ¿Por qué la memoria con carga se lee cada segundo durante la carga, y no una vez al final?
4. ¿Por qué los dos contenedores se miden uno a la vez y con el mismo techo?
5. **La pregunta de cierre:** alguien te dice «el nativo arranca 30 veces más rápido». ¿Qué le preguntas antes de
   creerle?

**Aquí termina la práctica.** Si llegaste hasta aquí, una orden ya cruza la API, el ERP y el socio sin perderse, y
sabes dónde mirar cuando algo falla.
