# Lección 00 — Ambiente listo

> **Lo que vas a lograr:** levantar con un solo comando los cuatro sistemas con los que vas a integrar durante toda
> la práctica, y comprobar que cada uno contesta. No programas nada todavía: preparas el taller.

## La historia en una frase

Vas a conectar una **tienda en línea** con los sistemas que necesita para vender: una **API de pedidos**, el **ERP**
de la empresa y un **socio comercial** que recibe archivos. Esos sistemas ya existen y no son tuyos: tu trabajo es
que la información viaje entre ellos sin perderse. En esta lección los enciendes.

## Antes de empezar necesitas

| Qué | Para qué | Cómo compruebas que lo tienes |
|---|---|---|
| Una terminal | Escribir los comandos | En Mac, la app *Terminal*; en Windows, *Ubuntu* dentro de WSL; en Linux, la que traiga |
| **git** | Bajar el repositorio de la práctica | `git --version` responde con un número |
| **Docker** con Compose | Correr cada sistema en su propio contenedor, sin instalarlo en tu máquina | `docker compose version` responde con un número |
| Memoria libre para Docker | Los cuatro sistemas y Camel corren a la vez | `docker compose up --wait` termina sin que tu máquina se trabe |

No instalas Java, ni Maven, ni Camel, ni ninguna base de datos. **Todo corre dentro de Docker.**

Si alguna de estas palabras es nueva para ti (terminal, git, contenedor), lee primero las fichas de
[`conceptos/antes-de-empezar/`](../conceptos/antes-de-empezar/). Son cortas.

⚠️ **Trabaja dentro de tu carpeta personal** (`~`), nunca en `/tmp`. En Mac con colima, Docker solo ve las carpetas
de tu home.

---

## 1. Baja la práctica

```bash
cd ~                                  # ve a tu carpeta personal
git clone https://github.com/HabilMX/ni-una-orden-perdida.git   # baja una copia a tu máquina
cd ni-una-orden-perdida               # entra a la carpeta que se creó
```

## 2. Predice

Abre `infra/compose.yaml` con tu editor. Es la lista de los sistemas que vas a levantar. Sin correr nada, contesta
en un papel:

1. ¿Cuántos servicios hay, y cómo se llama cada uno?
2. ¿Qué número de puerto usa cada uno en tu máquina? *(Pista: busca `ports:`. El número de la izquierda es el de tu
   máquina; el de la derecha, el de adentro del contenedor.)*
3. ¿Cuál crees que va a tardar más en arrancar la primera vez, y por qué?

## 3. Levántalo

Desde la carpeta raíz de la práctica:

```bash
docker compose -f infra/compose.yaml up -d --wait
```

- `-f infra/compose.yaml` le dice a Docker qué lista de servicios usar.
- `up` los arranca.
- `-d` los deja corriendo en segundo plano, así recuperas tu terminal.
- `--wait` espera hasta que todos digan que están sanos.

**La primera vez tarda varios minutos**, sobre todo por la API de pedidos: Docker la construye desde su código antes
de arrancarla. Sabrás que terminó cuando `--wait` te regrese la terminal y `docker compose ps` diga `healthy` en
cada sistema. Las siguientes veces es mucho más rápido.

## 4. Compara con tu predicción

```bash
docker compose -f infra/compose.yaml ps
```

Deberías ver cuatro servicios, todos en `healthy` (sano):

| Servicio | Qué es en la historia | En tu máquina | Qué es, técnicamente |
|---|---|---|---|
| `api-pedidos` | El sistema de pedidos de la empresa, al que se le habla por internet | `localhost:8081` | Una API REST hecha en Quarkus. Guarda en memoria: al reiniciarla, empieza vacía |
| `erp` | El **ERP**: el sistema central donde la empresa registra lo que vende | `localhost:13306` | Una base MariaDB con dos tablas (cabecera y detalle) y un **procedimiento almacenado** (`registrar_pedido`): un programa guardado dentro de la base |
| `socio-sftp` | El buzón del **socio comercial**, donde se le dejan archivos | `localhost:2222` | Un servidor **SFTP**, para copiar archivos de forma segura, con las carpetas `entrada/` y `salida/` |
| `estado` | La libreta donde tu integración anota cómo va cada orden | `localhost:15432` | Una base PostgreSQL con tres tablas. No la tocas hasta la lección 15 |

¿Acertaste cuál tardaba más?

## 5. Comprueba

```bash
cd lecciones/00-ambiente
./verifica
```

Está terminada cuando ves las nueve marcas verdes:

```
✅ estado está arriba
✅ erp está arriba
✅ socio-sftp está arriba
✅ api-pedidos está arriba
✅ La base de estado tiene sus tres tablas
✅ El ERP tiene su procedimiento registrar_pedido
✅ El ERP rechaza lo que no cuadra
✅ El SFTP del socio tiene entrada/ y salida/
✅ La API de pedidos contesta y exige su llave (400 sin ella)
```

Fíjate en la séptima. `verifica` le pide al ERP que registre un pedido que no existe, y **espera que el ERP se niegue**. La novena hace lo mismo con la API: le manda un pedido sin
su llave y espera que lo rechace.
Los sistemas de esta práctica no dicen que sí a todo, como los de verdad: validan, rechazan y a veces contestan tarde.
Si un destino aceptara cualquier cosa, tu integración parecería funcionar aunque estuviera mal.

## 6. Apágalo y vuelve a encenderlo

```bash
docker compose -f infra/compose.yaml down        # apaga y quita los contenedores
docker compose -f infra/compose.yaml up -d --wait
```

Al apagar con `down` se borra lo que había adentro: el ambiente vuelve limpio cada vez. Al inicio de cada lección
lo vas a encender así.

## Si algo falla

| Lo que ves | Qué pasa | Qué haces |
|---|---|---|
| `port is already allocated` | Ya tienes algo usando ese puerto. Por eso la práctica usa 15432 y 13306 en lugar de los de siempre (5432 y 3306), pero otro programa puede estar en 8081 o 2222 | Detén ese programa, o pregunta en el canal cómo cambiar el puerto |
| `Cannot connect to the Docker daemon` | Docker no está corriendo | Abre Docker Desktop, o en Mac `colima start` |
| Un servicio se queda en `starting` | Le falta memoria o sigue construyendo | Espera un minuto más; si no cambia, `docker compose -f infra/compose.yaml logs <servicio>` |
| `Source file does not exist` | Clonaste el repo fuera de tu home (Mac con colima) | Clónalo dentro de `~` |

## Una nota sobre las contraseñas

En `infra/compose.yaml` las contraseñas están escritas a la vista (`practica`). Aquí se puede porque es un ambiente
de práctica en tu máquina. **En un sistema real esto no se hace nunca:** las contraseñas llegan por variables de
entorno o por un gestor de secretos, y no se guardan en el repositorio.

---

## Por qué se hizo así

Cada decisión de la práctica tiene su ficha: qué decidimos, por qué, qué descartamos y qué nos haría
cambiar de idea. Están en [`decisiones/`](../decisiones/), y cada lección te liga la suya. Hoy todavía no hay
ninguna: el ambiente es el piso, no una decisión de diseño.

## Repaso

1. ¿Qué hace cada una de las cuatro piezas en la historia de la tienda?
2. ¿Qué significan `up`, `-d` y `--wait`?
3. ¿Por qué es bueno que el ERP rechace un pedido que no existe?
4. ¿Por qué la base de estado usa el puerto 15432 en tu máquina y no el 5432 de siempre? ¿Qué pasaría si usara el 5432
   y tú ya tuvieras un PostgreSQL instalado?
5. ¿Qué pasa con los datos del ERP cuando apagas el ambiente con `down` y lo vuelves a encender? ¿Por qué
   conviene eso en una práctica?

**Siguiente:** en la lección 01 corres tu primera ruta de Camel. Todavía no usa estos sistemas: mueve un archivo de
una carpeta a otra.
