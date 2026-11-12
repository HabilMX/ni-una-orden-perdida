# Lección 01 — Mi primera ruta corre

> **Lo que vas a lograr:** correr una ruta de Camel, predecir lo que hace antes de verlo y poner nombre a sus tres
> piezas. Es la única lección en la que no escribes nada desde cero: la ruta ya viene hecha.

**Antes de empezar necesitas:** la lección 00 terminada y Docker funcionando. Nada más: no instalas Java ni Camel.

⚠️ **Trabaja dentro de tu carpeta personal** (`~`, por ejemplo `~/practicas/`), nunca en `/tmp`. En Mac con colima,
Docker solo puede ver las carpetas que están dentro de tu home. Si clonaste el repo en otro lado, la ruta falla con
«Source file does not exist».

---

## 1. Prepara tu carpeta

Todo lo tuyo va en `mio/`. Es una copia de `inicio/` y git no la sigue, así que ahí puedes romper lo que quieras.

```bash
cd lecciones/01-mi-primera-ruta
cp -R inicio mio
cd mio
```

## 2. Lee la ruta y predice

Abre `primera-ruta.camel.yaml`. Sin los comentarios, es esto:

```yaml
- route:
    id: primera-ruta
    from:
      uri: file:entrada
      parameters:
        include: ".*\\.json"
      steps:
        - log:
            message: "Llegó la orden del archivo ${header.CamelFileName}: ${body}"
        - to:
            uri: file:salida
```

**Antes de correrla, escribe en un papel tus respuestas** (no hay respuestas malas; lo que importa es compararlas
después):

1. Si pones un archivo `orden-1001.json` en la carpeta `entrada/`, ¿qué crees que va a aparecer en la pantalla?
2. ¿Dónde va a terminar el archivo?
3. ¿Qué va a quedar en `entrada/` cuando la ruta termine con él?

## 3. Córrela

Pon una orden en `entrada/` y arranca la ruta:

```bash
mkdir -p entrada
cp ../../../datos/ordenes/orden-1001.json entrada/
../../../herramientas/camel run primera-ruta.camel.yaml
```

La primera vez tarda un poco, porque Docker descarga la imagen de Camel. Cuando veas una línea con
`Started primera-ruta`, la ruta ya está escuchando. Unos segundos después aparece el anuncio de la orden. Para
detenerla, oprime `Ctrl+C`.

## 4. Compara con tu predicción

Mira tus tres respuestas y revisa las carpetas:

```bash
ls salida/
ls -a entrada/
```

- **La pantalla:** el log dice `Llegó la orden del archivo orden-1001.json:` y después la orden completa. `${header.CamelFileName}`
  se cambió por el nombre del archivo y `${body}` por su contenido.
- **El archivo:** está en `salida/`, idéntico a como entró.
- **`entrada/`:** parece vacía, pero `ls -a` muestra una carpeta escondida, `.camel/`, y adentro está tu orden.
  **Camel no borra lo que lee:** lo aparta para no volver a procesarlo. ¿Lo habías predicho?

Si alguna respuesta no coincidió, anota por qué crees que pasó. Esa nota vale más que haber acertado.

<!-- PENDIENTE (mdw-lab): sección «5. Míralo por dentro con camel trace», cuando el trace de la 01 esté armado. -->

## 5. Ponle nombre a lo que viste

La ruta tiene tres piezas. Nómbralas con una imagen y luego con su nombre formal:

| Lo que ves en el YAML | La imagen | Su nombre en los patrones de integración (EIP) |
|---|---|---|
| Toda la ruta: de `from` a `to`, pasando por `log` | **La tubería** | *Pipes and Filters* |
| `file:entrada` y `file:salida`, por donde entra y sale | **Las estaciones** | *Message Endpoint* |
| La orden que viaja, con su nombre de archivo y su contenido | **La carta** | *Message* |

Los EIP (*Enterprise Integration Patterns*) son nombres que cualquier equipo de integración del mundo reconoce. No
hace falta memorizarlos hoy: en cada lección aparece uno nuevo, justo cuando lo usas.

**Ejercicio:** en tu `mio/primera-ruta.camel.yaml`, escribe arriba de cada parte un comentario con su nombre
(`# estación`, `# tubería`, `# carta`). En la carta hay un detalle: no aparece escrita en el YAML. ¿Por qué?

## 6. Cámbiala

Ahora modifica la ruta. Después de cada cambio, vuelve a poner la orden en `entrada/` y corre otra vez:

1. **Cambia el mensaje del log** para que diga `Recibí la orden ${header.CamelFileName}`. Antes de correr: ¿qué
   esperas ver?
2. **Cambia la estación de salida** a `file:procesadas`. ¿Qué carpeta nueva esperas ver?
3. **Pon un archivo que no sea `.json`** en `entrada/`, por ejemplo `nota.txt`. ¿Lo toma la ruta? ¿Qué línea del YAML
   lo decide?

## 7. Comprueba

Desde la carpeta de la lección, no desde `mio/`:

```bash
cd ..
./verifica
```

⚠️ `verifica` **vacía y vuelve a llenar** `mio/entrada/` y `mio/salida/` para probar desde cero. Tu ruta no la toca.

Está terminada cuando ves las cinco marcas verdes:

```
✅ La ruta arrancó
✅ El log anunció la orden
✅ La orden llegó a salida/
✅ entrada/ quedó vacía
✅ Salió idéntica a como entró
```

Si cambiaste la salida a `procesadas/` en el paso 6, regrésala a `salida/` antes de comprobar.

**¿No te corre?** Prueba con la nuestra: `./verifica solucion`. Si la nuestra sale verde y la tuya no, compara las dos
con `diff mio/primera-ruta.camel.yaml solucion/primera-ruta.camel.yaml`. Usar la nuestra no es trampa: es para que
un error de hoy no te detenga en las lecciones siguientes.

## 8. El mapa del viaje

<!-- PENDIENTE: el mapa de los 22 EIP, con sus ilustraciones (D24). Se ordena por el viaje de la orden: creación,
embarque, entrega y que no falle. Cada EIP lleva su ilustración, su nombre y el número de su lección, sin definición. -->

**No tienes que entender esto hoy.** Es el mapa de lo que vas a construir: cada pieza llega en su lección, y en cada
una vas a volver aquí para ver dónde estás.

*Por qué Camel:* partimos de premisas, no de gustos. Buscamos algo ligero, que escale por servicio, que se defina por
configuración y que tenga licencia abierta. Al final de la práctica vas a medir tú mismo qué gana y qué cede. El
detalle está en [`conceptos/por-que-camel.md`](../../conceptos/por-que-camel.md).

---

## Repaso

Sin ver el YAML, contesta:

1. ¿Qué hace `from`, y qué hace `to`?
2. ¿Qué es la carta en esta ruta, y por qué no está escrita en el YAML?
3. ¿Qué hace Camel con un archivo que ya leyó?
4. Si quisieras que la ruta solo tomara archivos `.xml`, ¿qué línea cambiarías?

**Siguiente:** en la lección 02, la estación de entrada deja de ser una carpeta: la orden llega por HTTP y sale hacia
una API.
