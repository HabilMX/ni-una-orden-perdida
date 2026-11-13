# Lección 14 — Un archivo, varias órdenes

> **Lo que vas a lograr:** partir el archivo que la bodega deja con varios embarques, de modo que cada orden reciba
> su propio 856 y su estatus EMBARCADO, y que un embarque malo no detenga a los demás.

**Antes de empezar necesitas:** la lección 13 terminada y el ambiente de la 00 encendido.

## Lo nuevo de hoy, en una frase

En la 13 la bodega dejaba un archivo por embarque. En la vida real casi nunca es así: la bodega junta lo que salió
en la mañana y deja **un solo archivo, un lote**, con cinco embarques adentro. Hoy lo partes en sus partes, una por
orden. El patrón se llama *Splitter*.

**La imagen:** la caja que llega a la oficina con la correspondencia de todo el piso. Nadie entrega la caja entera
al primer escritorio: se abre, se saca cada sobre y se lleva a su destinatario. Y si un sobre trae un nombre que
nadie conoce, se aparta y se reparten los demás.

---

## 1. Prepara tu carpeta

```bash
cd lecciones/14-un-archivo-varias-ordenes
cp -R inicio mio
```

Abre el lote bueno, `datos/embarques/lote-bodega-0001.json`. Arriba trae los datos del lote (número, bodega,
cuándo se generó) y luego una lista, `embarques`, con cinco elementos. Cada elemento es un embarque como el de la
13: orden, paquetería, guía y renglones.

El lote malo es `lote-bodega-0002.json`: cinco embarques, y el tercero es de la orden **9999**, que la tienda nunca
creó. El router no lo va a aceptar.

## 2. Predice

Antes de abrir la ruta, contesta en un papel:

1. El lote 0001 trae cinco embarques. ¿Cuántos 856 esperas en el SFTP del socio?
2. En el lote 0002, el tercer embarque falla. ¿Qué pasa con el cuarto y el quinto? ¿Y con el primero y el segundo,
   que ya pasaron?
3. Al terminar el lote 0002, ¿a dónde debería ir el archivo: a `.camel/`, a `.error/` o a ninguno de los dos?

Mira cómo está hoy:

```bash
./verifica inicio
```

Hoy el sondeo le manda al router **el lote completo como si fuera un embarque**. El router busca una sola orden en
el cuerpo, no la encuentra y lo rechaza. Las marcas de la API, el ERP y el socio salen en rojo.

## 3. Lo que vas a escribir

Abre `mio/sondeo-bodega/sondeo-bodega.camel.yaml`. El `from` ya trae los cuatro cuidados de la 13; solo cambió el
`include`, que ahora toma los `lote-bodega-<algo>.json`. Tu trabajo está en los tres TODO de los pasos:

| TODO | Qué haces | Para qué |
|---|---|---|
| 1 | Envuelve los pasos en un `split` con la expresión JSONPath `$.embarques[*]` | Un mensaje por cada embarque de la lista |
| 2 | Escribe `stopOnException: false` en el `split` | Que un embarque malo no detenga a los demás |
| 3 | Mueve adentro del `split` los pasos que guardan la orden y llaman al router, y agrega un `marshal` a JSON antes de llamar | Que cada parte viaje sola al router |

Dentro del `split`, los pasos van en su propio `steps:`, con más sangría. Así se ve el esqueleto; los pasos de
adentro son los que ya están en el archivo:

```yaml
- split:
    jsonpath:
      expression: "$.embarques[*]"
    stopOnException: false
    steps:
      - setVariable: …   # la orden de esta parte
      - marshal:
          json: {}
      - …                # quitar encabezados, Content-Type, llamar al router, anotar
```

Dos cosas nuevas:

- **Cada parte llega como datos, no como texto.** JSONPath te entrega cada embarque como un mapa (llaves y
  valores). Por eso la orden ya no se saca con `$.orden` sino con `${body[orden]}`, y antes de mandarla al router
  hay que volverla texto JSON con `marshal`. Sin el `marshal`, el router recibe algo que no es JSON.
- **`stopOnException: false` ya es el valor por omisión.** Lo escribes igual: quien lea la ruta ve que se pensó, no
  que se olvidó.

## 4. Rompe uno a propósito

Con tu `split` terminado, cambia `stopOnException` a `true` y corre `./verifica`. Antes, predice: ¿cuántos de los
cuatro embarques buenos del lote 0002 llegan?

Sale en rojo la marca del lote 0002: solo llegan dos, la 4001 y la 4002. El `split` va en orden; al fallar el
tercero (la 9999) se detiene, y la 4004 y la 4005 se quedan sin avisar, aunque salieron de la bodega. Por un error
de una orden que ni existe, dos clientes no saben dónde está su paquete. Regresa a `false`.

## 5. Comprueba

```bash
./verifica
```

`verifica` crea las órdenes 4001 a 4005, deja el lote 0001 en el buzón y revisa; luego limpia los destinos y deja
el lote 0002. Está terminada cuando ves las siete marcas verdes:

```
✅ Arrancaron el router, los tres canales y el sondeo de la bodega
✅ Lote 0001: las 5 órdenes quedaron EMBARCADO en la API y en el ERP
✅ Socio: 5 archivos 856, uno por orden, y los 5 pasan el lector X12
✅ Buzón: el lote 0001 se apartó a .camel/
✅ Lote 0002: los otros 4 embarques pasaron (4001, 4002, 4004 y 4005 EMBARCADO en la API y el ERP)
✅ Sondeo: anotó en su bitácora que el embarque de la 9999 no pasó
✅ Buzón: el lote 0002 se apartó a .error/ para que alguien vea el embarque malo
```

Fíjate en las dos últimas: el lote 0002 terminó en `.error/` **aunque cuatro de sus cinco embarques pasaron**. El
`split` siguió con los demás, pero al final recuerda que hubo un error, y el sondeo trata el archivo como fallido.
El archivo en `.error/` dice «revisa este»; la bitácora dice cuál:

```bash
DONDE=mio docker compose -f compose.yaml logs sondeo-bodega | grep "no aceptó"
```

**¿No te corre?** `./verifica solucion` prueba la nuestra, y
`diff mio/sondeo-bodega solucion/sondeo-bodega` te dice en qué son distintas.

| Lo que ves | Qué haces |
|---|---|
| `solo 0 de 5 órdenes quedaron EMBARCADO` | Los pasos que llaman al router quedaron fuera del `split`, o falta el `split`. Revisa la sangría: tienen que estar dentro de su `steps:` |
| La bitácora dice que el router no aceptó ningún embarque | Falta el `marshal` a JSON: el router recibe un mapa, no texto |
| `el malo detuvo a los demás` | `stopOnException` quedó en `true` |
| `Al ERP le falta la tabla embarque` | Recrea el ERP como dice la 13 |

---

## Lo que esto enseña fuera de la práctica

**Los lotes son la regla, no la excepción.** Bancos, bodegas y sistemas viejos entregan su trabajo en archivos
grandes, una vez al día o cada hora. Partir el archivo y tratar cada parte como si hubiera llegado sola es lo
primero que hace una integración con ellos.

**Un lote no es una transacción.** En la 05 aprendiste «todo o nada» dentro del ERP. Aquí es al revés: cada embarque
es de un cliente distinto, y que uno falle no es razón para no avisarle a los otros cuatro. Qué conviene, todo o
nada o uno por uno, depende de si las partes dependen unas de otras.

## Ejercicio: el lote con su error

Con tu versión terminada:

1. Abre `buzon/.error/lote-bodega-0002.json`. Si fueras quien atiende la bodega, ¿cómo sabrías cuál de los cinco
   embarques revisar? ¿Te basta el archivo, o necesitas la bitácora?
2. Corre `./verifica` otra vez sin cambiar nada. ¿Se duplicó algún 856 o algún EMBARCADO? ¿Por qué no? (Pista: el
   verifica limpia los destinos antes de cada lote, y la API reconoce la llave de cada embarque.)
3. Imagina que la bodega corrige la 9999 y vuelve a mandar el lote 0002 completo. ¿Qué les pasaría a las cuatro
   órdenes que ya estaban EMBARCADO? Escribe tu respuesta: el tema de «¿cuándo dos mensajes son el mismo?» es la
   lección 18.

---

## Por qué se hizo así

Lo que decidimos en esta lección, con lo que descartamos y qué nos haría cambiar de idea:

- [Un archivo de la bodega se parte en sus embarques, y uno malo no detiene a los demás](../decisiones/17-partir-el-archivo.md)

## Repaso

1. ¿Qué hace un Splitter, y por qué hace falta con un lote de la bodega?
2. ¿Por qué dentro del `split` la orden se lee con `${body[orden]}` y no con `$.orden`?
3. ¿Para qué sirve el `marshal` antes de llamar al router?
4. ¿Qué hace `stopOnException: false`? ¿Por qué se escribe si ya es el valor por omisión?
5. **La pregunta de cierre:** el lote 0002 terminó en `.error/` aunque cuatro embarques sí pasaron. ¿Es un
   problema? ¿Qué te dice ese archivo, y qué te dice la bitácora?

**Siguiente:** en la lección 15 la pregunta de cierre de la 12 regresa: si el router tiene copias, ¿dónde vive lo de
cada orden? Hoy el router no recuerda nada; mañana guarda cada orden, cada canal y cada intento en su propia base.
