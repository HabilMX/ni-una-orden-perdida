# Los contenedores de Camel escriben como el dueño de tu carpeta, sin que tengas que hacer nada

> **Dónde aparece:** de la lección 01 a la 30: en `herramientas/camel` (01 a 07, más `leer-x12` y `probar-mapeo`),
> en el `verifica` de la 01 y en el bloque común `x-camel` de cada `compose.yaml` (08 a 30). La pieza que lo hace es
> `herramientas/correr-como-el-dueno`.

## El problema

La imagen oficial de Camel, `apache/camel-jbang`, corre con un usuario fijo: `ubuntu`, con el número (uid) 1000. Las
lecciones montan carpetas de tu máquina en el contenedor (`entrada/`, `salida/`, `buzon/`, y las `.camel/` y
`.error/` que crea Camel al mover lo que leyó), y en Linux los permisos de esas carpetas se respetan tal cual.
Eso trae dos fallas que en una Mac casi nunca se ven, porque ahí Docker traduce los permisos de la carpeta compartida:

1. **Tu usuario tiene otro número.** Si eres el 1001 (lo que suele usar un servidor de pruebas) o el 1002 (el
   segundo usuario de una laptop), tus carpetas son tuyas con permisos `755`, y el 1000 no puede escribir ahí. La
   ruta arranca, lee la orden y no la puede dejar: «La orden no llegó a salida/ · sigue en entrada/».
2. **La carpeta no existía.** Si levantas los servicios antes de que exista `buzon/`, Docker en Linux la crea a
   nombre de root (`0:0`, `755`). Ahí no escribe ni el 1000: le pasa a cualquier alumno de Linux, aunque su número
   coincida con el de la imagen.

Lo medimos en una máquina Linux con Docker nativo (la de colima), con la lección 01 sin cambios y la carpeta del
usuario 501 en `755`: la ruta arrancó, Camel dijo `Failed to create working directory: /work/.camel-jbang` y
`Cannot store file: salida/orden-1001.json`, y el `verifica` dio 2 ✅ y 3 ❌. Y un `docker run -v` hacia un `buzon/`
que no existía lo dejó como `drwxr-xr-x 0 0`.

## Qué decidimos

- **El contenedor arranca como root solo para acomodar permisos, y luego corre Camel como el dueño de la carpeta.**
  Lo hace `herramientas/correr-como-el-dueno`, que se monta en el contenedor y queda como su punto de arranque:
  1. Lee de quién es `/work`. Si se ve a nombre de root, lo toma como una carpeta compartida de Mac o de Docker
     Desktop, que ya traduce permisos, y se queda con `ubuntu` como hasta ahora.
  2. Si Docker creó a nombre de root alguna carpeta montada dentro de `/work` (como `buzon/`), se la pasa al dueño de
     `/work`, como si la hubieras creado tú.
  3. Si el dueño no es el 1000, le cambia el número a `ubuntu` dentro del contenedor. Su casa, `/home/ubuntu`, con lo
     que Camel ya trae descargado, le sigue sirviendo.
  4. Si el caché de descargas (el volumen `practica-retail-m2`) quedó a nombre de otro número, lo pasa al de ahora.
  5. Suelta root (`setpriv`) y arranca Camel con lo mismo que se le pidió.
- **El alumno no teclea nada nuevo.** Ni en la 00 ni en ninguna otra: lo que escribe sigue siendo
  `../../herramientas/camel run …` y `docker compose … up`. Los archivos que deja Camel salen a tu nombre, y el
  `rm -rf entrada salida` del `verifica` los puede borrar.
- **Una sola pieza para todos.** Los `compose.yaml` la cargan con `configs:` desde el bloque común `x-camel`, así
  que cada servicio la hereda sin repetirla; `herramientas/camel`, `leer-x12`, `probar-mapeo` y el `verifica` de la
  01 la montan con `-v`.

## Por qué

- **Ataca la causa, no el síntoma.** El problema es que el que escribe no es el dueño. Abrir los permisos de la
  carpeta (`chmod 777`) haría que cualquiera pudiera escribir en ella y dejaría archivos a nombre de otro número que
  luego no podrías borrar sin `sudo`.
- **No depende del número que tengas.** Medido después del cambio en Linux, con el usuario 501 y con uno temporal de
  número 1001 (como el servidor de pruebas), y en la Mac. En WSL no lo medimos: con el repo dentro de la
  distribución, la carpeta es de tu usuario de Linux, y ese caso es el mismo que medimos en Linux.
- **No le pide un paso al alumno.** Un paso que se olvida es un paso que falla a la mitad del curso, en la lección
  13, lejos de donde se dio.
- **Es lo que hacen otras imágenes oficiales.** Arrancar como root, acomodar lo que haga falta y bajar a un usuario
  sin privilegios es el patrón del arranque de imágenes como la de PostgreSQL.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| `chmod 777` (o `a+w`) en las carpetas, en el texto o escondido en los `verifica` | Tapa el síntoma: cualquiera escribe ahí y los archivos quedan a nombre de otro |
| Guardar `entrada/`, `salida/` y `buzon/` en el repo con un `.gitkeep` | Arregla solo que la carpeta no exista (caso 2); con otro número (caso 1) sigue sin poder escribir |
| `user: "${UID}:${GID}"` en compose y `--user` en `docker run` | Medido: con `--user 1001:1001`, Camel ni arranca (`Could not read artifact descriptor`): ese número no tiene casa (`HOME=/`), `/home/ubuntu` le niega la entrada y el caché es del 1000. Además, bash y zsh no exportan `UID`, y compose lee el `.env` de la carpeta de cada lección: serían 23 `.env` o un paso por lección |
| Una imagen propia de Camel con el número del alumno | Otra pieza que construir y mantener en cada máquina, y cambia de imagen a mitad del curso |
| Reasignar números en Docker (`userns-remap`) o usar Docker sin root | Es configuración del Docker de cada máquina, no de la práctica |

## Qué nos haría reabrirla

- Que la imagen oficial de Camel deje de traer `setpriv`, `usermod` o `groupmod`, o cambie de usuario o de casa.
- Que alguien la use con Docker sin root o con Podman: ahí root dentro del contenedor ya es tu usuario, `/work` se ve
  a nombre de root y la pieza se queda con `ubuntu`, como antes. No lo hemos medido.
- Que un sistema muestre la carpeta compartida a nombre de root y aun así respete los permisos: la pieza no podría
  distinguirlo de una Mac.

## Fuentes

- Docker, montar carpetas de tu máquina (y qué pasa si no existen): https://docs.docker.com/engine/storage/bind-mounts/
- Docker Compose, `configs` en un servicio: https://docs.docker.com/reference/compose-file/configs/
- Docker Compose, `user` y `entrypoint` de un servicio: https://docs.docker.com/reference/compose-file/services/
- La imagen de Camel JBang: https://hub.docker.com/r/apache/camel-jbang
- `setpriv`, correr un programa con otro usuario sin pasar por una sesión: https://man7.org/linux/man-pages/man1/setpriv.1.html
- `usermod`, cambiar el número de un usuario: https://man7.org/linux/man-pages/man8/usermod.8.html
- El arranque de la imagen oficial de PostgreSQL, que baja de root a su usuario: https://github.com/docker-library/postgres/blob/master/docker-entrypoint.sh
