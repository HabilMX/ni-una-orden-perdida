# Por qué Camel

Hay muchas formas de integrar sistemas, y ninguna es la mejor para todo. Esta práctica no eligió por gusto: partió de
unas **premisas** y buscó la herramienta que las cumpliera. Aquí están las premisas, las familias de herramientas que
hay, qué elegimos, qué cuesta y cuándo no conviene.

## Las premisas

1. **Ligera:** una integración es un proceso pequeño que hace una cosa.
2. **Que escale por copias:** cada integración es un servicio aparte, y crece pidiendo más copias, sin un clúster propio
   que operar.
3. **Que arranque al momento** cuando sube la carga.
4. **Cloud native:** corre en un contenedor, se configura por archivos o variables y dice si está sana.
5. **Que se defina por configuración**, que se lea sin saber programar en un lenguaje general.
6. **Licencia abierta.**

## Las familias de herramientas, cada una con su caso

- **Si solo copias datos de A a B cada hora**, una herramienta de flujo de datos o un conector de un broker (por ejemplo,
  los conectores de Kafka) lo hace sin escribir rutas. Choca cuando cada destino necesita su propia lógica.
- **Si un proceso dura días y no puede perder su avance** (una aprobación con varias firmas), un motor de procesos
  durables guarda cada paso en su base de datos. Choca en que hay que operar ese motor, y los flujos se escriben en código.
- **Si quieres automatizar algo entre dos aplicaciones de oficina**, una herramienta visual de automatización sale
  rápido. Choca cuando el flujo crece o cuando la licencia no es abierta.
- **Si tu empresa ya compró una plataforma de integración comercial**, trae su editor, su consola y su soporte. Choca en
  costo y en que no corre ligera en cualquier lado.
- **Si integras muchos sistemas distintos, cada uno con su protocolo**, una **biblioteca de patrones de integración**
  con muchos conectores es lo que más trabajo te ahorra. Aquí está Camel.

## Lo que elegimos

**Apache Camel**: una biblioteca de patrones de integración con cientos de componentes, rutas en YAML y licencia
Apache 2.0. Para aprender se corre con su CLI (`camel run`). **Si llevas a producción una integración como las de esta
práctica, el mismo YAML corre sobre Camel Quarkus**, que puede compilarse a modo nativo para arrancar rápido y ocupar
poca memoria.

## Lo que cuesta

- **El nativo no es gratis.** Compilarlo tarda minutos y pide una máquina con bastante memoria, y en carga sostenida
  rinde menos por copia que la JVM. Responde rápido **escalando**, no por copia (ficha [nativo](antes-de-empezar/07-nativo.md)).
- **No todo componente funciona en nativo.** Cada extensión de Camel Quarkus dice si lo soporta.
- **Camel es una biblioteca, no una plataforma.** No trae una consola para reprocesar lo que falló ni una base de
  estado: eso lo construyes tú, y la práctica te enseña cómo (parte 3).
- **Hay que aprender su forma de pensar:** mensajes, rutas, componentes y expresiones.

## Cuándo no usar Camel

- Cuando solo copias datos entre dos lugares sin lógica: es más pieza de la que hace falta.
- Cuando el proceso es largo y con pasos humanos: un motor de procesos durables lo resuelve mejor.
- Cuando tu equipo no va a mantener código ni YAML y necesita un editor visual.

## Y las cifras

Aquí no hay cifras de arranque ni de memoria. Las cifras publicadas vienen de ejemplos, máquinas y versiones que no son
las tuyas. En la lección 32 mides tú mismo el mismo servicio en JVM y en nativo, y ahí comparas tu medición con lo que
publica Quarkus.

## Fuentes

- Apache Camel: https://camel.apache.org/
- Camel Quarkus, primeros pasos: https://camel.apache.org/camel-quarkus/latest/first-steps.html
- Quarkus, construir un ejecutable nativo (costos de compilación y rendimiento): https://quarkus.io/guides/building-native-image
- Hohpe y Woolf, *Enterprise Integration Patterns*: https://www.enterpriseintegrationpatterns.com/
