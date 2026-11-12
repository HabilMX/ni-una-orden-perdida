# Lección 07 — Al socio

> **Lo que vas a lograr:** dejar el 850 de la 06 en el buzón del socio comercial con tres cuidados: que no tome un
> archivo a medias, que la misma orden siempre tenga el mismo nombre y que un segundo envío no borre el primero.

**Antes de empezar necesitas:** la lección 06 terminada y el ambiente de la 00 encendido:

```bash
docker compose -f infra/compose.yaml up -d --wait     # desde la carpeta raíz de la práctica
```

## Lo nuevo de hoy, en una frase

El socio no tiene una API: tiene un **buzón SFTP**, una carpeta en su servidor a la que tú subes archivos y de la
que él los lee **cuando quiere**. No te avisa cuándo lee ni te contesta. Por eso lo que dejes ahí tiene que estar
completo y no puede estorbar a lo que ya había.

**SFTP, lo mínimo:** un programa para copiar archivos a otra computadora por la red de forma segura. Pide un usuario
y una contraseña (o una llave), y antes de conectarse comprueba que el servidor sea quien dice ser. Esa comprobación
es el paso 2.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/07-al-socio
cp -R inicio mio
cd mio
```

La ruta usa el mapeo y la plantilla del 850 de la 06, que ya vienen dentro de `mio/`.

## 2. Confía en el socio

La primera vez que hablas con un servidor tienes que saber que es **el de tu socio** y no otro que se hace pasar por
él. Cada servidor tiene una **llave pública**, como una huella digital. Tú la anotas una vez en un archivo llamado
`known_hosts`, y desde entonces la ruta solo se conecta si el servidor presenta esa misma llave.

```bash
../../../herramientas/confiar-en-socio
```

En la vida real, el socio te manda su llave por otro canal (un correo, un portal) y tú la anotas a mano. Aquí el
script la toma directo del contenedor del socio, que hace las veces de su administrador.

⚠️ Si apagas el ambiente con `down` y lo vuelves a levantar, el servidor del socio estrena llave: vuelve a correr el
script. `known_hosts` es tuyo y git no lo sigue.

## 3. Lee la ruta

Abre `orden-al-socio.camel.yaml`. Es la ruta de la 06 con otra estación de salida:

```yaml
uri: sftp://socio@socio-sftp:22/entrada
```

| Parte | Qué es |
|---|---|
| `socio@` | El usuario con el que entras al buzón |
| `socio-sftp:22` | El servidor del socio y su puerto |
| `/entrada` | La carpeta donde el socio recoge lo que le mandan |

Debajo vienen sus parámetros:

| Parámetro | Qué hace |
|---|---|
| `knownHostsFile: known_hosts` y `strictHostKeyChecking: "yes"` | Solo se conecta si la llave del servidor es la que anotaste en el paso 2 |
| `fileName: "850-${variable.orden}.edi"` | El nombre final. La orden 1001 siempre se llama `850-1001.edi` |

Ese nombre se llama **determinístico**: depende solo de la orden. Si se llamara con la hora o con un número al azar,
la misma orden mandada dos veces dejaría dos archivos, y el socio surtiría dos veces.

## 4. Predice

Contesta en un papel:

1. Si mandas la orden 1001 dos veces, ¿cuántos archivos esperas en el buzón del socio?
2. Si la segunda vez la orden trae **otra cantidad** (5 tazas en lugar de 2), ¿qué tiene el archivo al final?
3. Si alguien cambia el servidor del socio por otro con otra llave, ¿qué crees que hace la ruta?

## 5. Córrela dos veces

Pon la orden 1001 en `entrada/` y corre la ruta. `--max-messages=1` la detiene sola después de una orden:

```bash
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
```

Pregúntale al buzón del socio qué tiene:

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp ls /home/socio/entrada
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Ahora manda la **misma orden con otra cantidad**, `orden-1001-cambiada.json`, y vuelve a mirar el buzón:

```bash
cp ../../../datos/ordenes/orden-1001-cambiada.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1
docker compose -f ../../../infra/compose.yaml exec socio-sftp cat /home/socio/entrada/850-1001.edi
```

Busca el primer `PO1`. Dice `PO1*1*5*…`: **el segundo envío borró el primero sin avisar**. Hay un solo archivo, como
predijiste en la pregunta 1, pero ya no es la orden que el socio tenía. Si ya la había leído, ahora tiene dos
versiones distintas de la orden 1001 y no sabe cuál vale.

## 6. Agrega los dos cuidados que faltan

En la ruta hay un comentario **TE TOCA**. Ahí van dos parámetros más de la estación `sftp:`:

**Nombre temporal.** Subir un archivo toma tiempo. Si el socio lee su carpeta justo a la mitad, se lleva un archivo
cortado. La solución es escribirlo con otro nombre y cambiarlo al final, que es instantáneo:

```yaml
tempFileName: "${file:onlyname.noext}.tmp"
```

Mientras se escribe se llama `850-1001.tmp`; cuando termina, se renombra a `850-1001.edi`. Si el socio solo recoge
archivos `.edi`, es muy difícil que tome uno incompleto: el renombre es lo último que pasa. No es una garantía
total (un socio que recoge cualquier archivo, o una carpeta compartida mal configurada, lo rompen), y por eso en la
lección 21 la confirmación de que llegó bien la da el 997, no el archivo.

**No pisar.** Por omisión, si el archivo ya existe, Camel lo reemplaza. Tú quieres lo contrario: que falle y alguien
decida.

```yaml
fileExist: Fail
```

Cuida la sangría: los dos van al mismo nivel que `fileName`.

Vacía el buzón y repite el paso 5:

```bash
docker compose -f ../../../infra/compose.yaml exec socio-sftp rm /home/socio/entrada/850-1001.edi
```

Ahora el segundo envío no pasa. En la terminal de la ruta aparece:

```
File already exist: entrada/850-1001.edi. Cannot write new file.
```

Y la orden cambiada **se quedó en tu carpeta `entrada/`**, sin apartarse a `.camel/` como las que salen bien. No se
perdió: está esperando a que alguien decida qué hacer con ella. Eso lo vas a resolver en la lección 18.

## 7. Cambia la llave

Ahora la pregunta 3. Respalda tu `known_hosts`, pon una llave falsa y manda una orden:

```bash
cp known_hosts known_hosts.bueno
echo "socio-sftp ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHVuYWxsYXZlZGVvdHJvc2Vydmlkb3JxdWVub2Vz" > known_hosts
cp ../../../datos/ordenes/orden-1003.json entrada/
../../../herramientas/camel run orden-al-socio.camel.yaml --max-messages=1 --max-seconds=40
```

Entre los errores vas a encontrar:

```
HostKey has been changed: socio-sftp
```

La ruta **se negó a conectarse**: el servidor presentó una llave distinta a la anotada. Puede ser que el socio la
cambió (y te lo tendría que avisar por otro canal), o que alguien está en medio haciéndose pasar por él. En los dos
casos, lo correcto es no mandarle nada. Regresa tu llave buena:

```bash
mv known_hosts.bueno known_hosts
```

## 8. Comprueba

```bash
cd ..
./verifica
```

⚠️ `verifica` **vacía el buzón del socio** antes de probar.

Está terminada cuando ves las siete marcas verdes:

```
✅ Llegó 850-1001.edi al SFTP del socio
✅ No quedó ningún archivo .tmp
✅ Lo que recibió el socio es el 850 correcto
✅ Un segundo envío de la 1001 no pisó el archivo que ya estaba
✅ La ruta escribe con nombre temporal
✅ La ruta intentó conectar y el servidor no presentó la llave anotada
✅ Con otra llave en known_hosts, la ruta se negó a conectar
```

La quinta es distinta a las demás: el `.tmp` vive unos milisegundos y no hay forma de verlo desde afuera, así que
`verifica` lo revisa en tu ruta. La sexta busca en el log el `HostKey has been changed` del paso 7: demuestra que la
ruta sí intentó conectarse y que la rechazó la llave, no otra falla. Las demás las confirma el buzón del socio.

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/orden-al-socio.camel.yaml solucion/orden-al-socio.camel.yaml` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `PISÓ el archivo del socio` | Falta `fileExist: Fail` |
| `no usa nombre temporal` | Falta `tempFileName` |
| `HostKey has been changed` en todas las corridas | Recreaste el ambiente: vuelve a correr `confiar-en-socio` (paso 2) |
| `No pude hablar con el SFTP del socio` | Enciende el ambiente: `docker compose -f infra/compose.yaml up -d --wait` |

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Cuatro cuidados al dejar un archivo al socio](../decisiones/08-entrega-segura-al-sftp.md)

## Repaso

1. ¿Por qué el archivo se escribe con un nombre temporal y se renombra al final?
2. ¿Qué hace que el nombre `850-1001.edi` sea determinístico, y qué pasaría si llevara la hora?
3. Sin `fileExist: Fail`, ¿qué le pasó al primer envío? ¿Por qué es peor que un error?
4. ¿Para qué sirve `known_hosts`, y qué hace la ruta si el servidor presenta otra llave?
5. La orden que no se pudo mandar se quedó en `entrada/`. ¿Por qué es mejor eso que borrarla?

**Siguiente:** ya tienes los tres destinos: la API, el ERP y el socio. En la lección 08 los juntas: la orden entra
por un **router** que la reparte entre servicios que se llaman por su nombre.
