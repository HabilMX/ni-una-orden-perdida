# JVM contra nativo se mide en tu máquina, con un método fijo y sin umbrales; la capacidad se mide igual

> **Dónde aparece:** lección 32 y el recuadro de capacidad del apéndice (lecciones/31-…/CAPACIDAD.md).

## El problema

Las cifras de arranque y memoria que se publican vienen de otras máquinas, otras versiones y otros servicios. Si la
lección diera «el nativo arranca en tanto», el alumno compararía su máquina con la de alguien más y sacaría
conclusiones que no son suyas. Pero medir «a ojo» tampoco sirve: el arranque depende de qué se cuenta (¿desde que se
pide el contenedor?, ¿hasta que el proceso lo dice?), y la memoria de cuándo se mira. Y para el recuadro de
capacidad hay que decir cuánta RAM, CPU y disco pide el apéndice, con cifras que se puedan repetir.

## Qué decidimos

- **Seis cifras por modo, siempre las mismas y medidas igual** (`medir` de la 32, que escribe `cifras.md`):

  | Cifra | Cómo se mide |
  |---|---|
  | Construir (s) | `docker compose build --no-cache`, con el caché de Maven ya lleno: es compilar, no bajar |
  | Quarkus dice «started in» (s) | La línea de la bitácora: lo que el proceso dice de sí mismo |
  | Hasta sano (ms) | Desde que se pide el contenedor hasta que `/q/health/ready` contesta 200, preguntando cada 50 ms |
  | Memoria en reposo (MiB) | `docker stats`, 10 s después de estar sano, sin carga |
  | Memoria con carga (MiB) | La más alta de `docker stats`, leída cada segundo durante 20 s de carga (100 órdenes por segundo) |
  | Imagen (MB) | El tamaño de la imagen, según `docker image inspect` |

- **Mismas condiciones para los dos:** cada contenedor con 1 CPU y 512 MiB, uno a la vez (el otro apagado), la misma
  carga y la misma API.
- **El `verifica` no compara contra ningún umbral.** Exige que las dos imágenes arranquen y entreguen su orden a la API
  (contado en la bitácora de la API), que la nativa no traiga JVM (control) y que `cifras.md` se haya escrito en
  esa corrida con las seis cifras de cada modo, y que cada una sea un número.
- **La capacidad del apéndice se mide con herramientas que dicen lo que miden**, y el recuadro dice cuál se usó:
  `docker stats` del contenedor que es el nodo de kind (CPU y memoria del clúster completo), la memoria usada de la
  máquina virtual de Docker (`free -m`, cada 2 s) para el pico de la compilación, lo que el propio compilador nativo
  reporta (`Peak RSS` y `CPU load`), y `docker system df` y el tamaño de cada imagen para el disco.

## Por qué

- **Dos medidas de arranque, porque dicen cosas distintas.** «started in» no cuenta lo que tarda el contenedor en
  nacer; «hasta sano» sí, y es lo que espera un HPA antes de mandarle órdenes a una copia nueva. Si solo diéramos una,
  la diferencia entre JVM y nativo se vería más grande o más chica de lo que es.
- **La memoria con carga se lee DURANTE la carga**: al terminar, la memoria puede bajar, y una sola lectura al final
  mediría otra cosa.
- **Un umbral en el `verifica` sería una cifra inventada**: en una laptop con otras cosas abiertas el nativo podría
  «fallar» sin que nada esté mal. Lo que sí se puede exigir es que la medición exista y venga de esta corrida.
- **Los números de otro no son los tuyos** (ficha nativo, `conceptos/antes-de-empezar/07-nativo.md`). Con un método
  fijo, el alumno puede repetirlo, cambiar una cosa y ver qué movió.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Dar en la lección cifras de referencia | Son de otra máquina; se volverían la meta en vez de la medición |
| Medir el arranque solo con «started in» | Deja fuera lo que tarda el contenedor, que es parte de lo que espera quien escala |
| Medir con un generador de carga largo (minutos) | La lección es de arranque y memoria; la carga corta basta para ver la memoria trabajando, y el rendimiento sostenido es otra medición |
| Medir la memoria de la compilación con `docker stats` | La compilación corre dentro de BuildKit, que no aparece como contenedor; por eso se usa la VM y el reporte del compilador |
| Exigir que el nativo arranque más rápido que la JVM | Es lo esperado, pero convertirlo en condición sería un umbral; el `verifica` deja la cifra y el alumno la lee |

## Qué nos haría reabrirla

- Que se agregue la medición de rendimiento sostenido (órdenes por segundo por copia, durante minutos): es la otra
  mitad de la comparación (ficha nativo), y pediría su propia lección.
- Que Docker cambie lo que reporta `docker stats` como memoria (hoy, la del grupo de control del contenedor, sin el
  caché de archivos).

## Fuentes

- Quarkus, construir un ejecutable nativo (qué se gana y qué cuesta): https://quarkus.io/guides/building-native-image
- Quarkus, salud con SmallRye Health (`/q/health/ready`): https://quarkus.io/guides/smallrye-health
- Docker, `docker stats`: https://docs.docker.com/reference/cli/docker/container/stats/
- Docker, `docker system df`: https://docs.docker.com/reference/cli/docker/system/df/
- Docker Compose, límites de CPU y memoria de un servicio: https://docs.docker.com/reference/compose-file/services/
