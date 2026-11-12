# Qué es Camel y qué es una ruta

**Para qué te sirve aquí:** cada integración de la práctica es una **ruta** de Apache Camel escrita en YAML. Saber leer
una ruta es saber leer la práctica.

## Lo que ves en tu terminal

```bash
herramientas/camel version
```

Sale `Camel CLI version: 4.22.1`. Es la herramienta con la que corres tus rutas: `herramientas/camel run mi-ruta.camel.yaml`.
La práctica la trae dentro de un contenedor, así que no instalas Java ni Camel.

## Una imagen de la vida diaria

Una ruta es una **banda de una paquetería**: un paquete entra por una puerta, pasa por estaciones (se pesa, se etiqueta,
se separa) y sale por otra puerta hacia su destino. Camel es la paquetería completa: ya trae las puertas, las estaciones
y los camiones; tú dibujas la banda.

## El concepto

**Apache Camel** es un motor de integración de código abierto. Trae resueltos los **patrones de integración**
(Enterprise Integration Patterns, del libro de Hohpe y Woolf) y cientos de **componentes**: piezas que saben hablar con
HTTP, archivos, SFTP, bases de datos, Kafka y mucho más.

Una **ruta** tiene tres partes:

```yaml
- route:
    from:
      uri: platform-http:/ordenes      # 1. la puerta de entrada: aquí llega la orden por HTTP
      steps:
        - log:
            message: "Llegó la orden ${body}"   # 2. una estación: algo que se le hace al mensaje
        - to:
            uri: http://api-pedidos:8080/pedidos  # 3. la puerta de salida: a dónde va
```

Lo que viaja por la ruta es un **mensaje**: un **cuerpo** (la orden) y **encabezados** (datos sobre ella). Cada paso
puede leerlos, cambiarlos o decidir a dónde va el mensaje. Las `${…}` son expresiones del lenguaje **Simple** de Camel:
`${body}` es el cuerpo, `${header.X}` un encabezado.

Una **URI** como `platform-http:/ordenes` o `sftp://socio-sftp:22/entrada` dice qué componente usar y con qué opciones:
lo de antes de los dos puntos es el componente; lo demás, a dónde y cómo.

## Lo que necesitas saber para la práctica

- Antes de correr una ruta, **predice** qué va a pasar. Luego córrela y compara.
- `camel run` con `--trace` (o `camel trace`) te deja ver el mensaje en cada paso.
- Los mapeos (JSONata) y las plantillas (Mustache) viven en archivos aparte; la ruta solo los llama.
- Por qué la práctica usa Camel, y cuándo no conviene: [por qué Camel](../por-que-camel.md).

## Compruébalo

```bash
herramientas/camel version
```

Desde la raíz de la práctica, la última línea debe decir `Camel CLI version: 4.22.1`. La primera vez tarda porque baja
la imagen.

## Si quieres ir más lejos

- Apache Camel, qué es: https://camel.apache.org/manual/faq/what-is-camel.html
- El DSL de YAML de Camel: https://camel.apache.org/components/next/others/yaml-dsl.html
- El catálogo de patrones de integración: https://www.enterpriseintegrationpatterns.com/patterns/messaging/
