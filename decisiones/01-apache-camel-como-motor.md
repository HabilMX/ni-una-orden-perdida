# Apache Camel como motor de integración

> **Dónde aparece:** lección 01.

## El problema

Sin un motor, cada integración reinventa a mano lo mismo: conectarse, reintentar, traducir, repartir.

## Qué decidimos

Las integraciones de la práctica se escriben con **Apache Camel**: un motor de código abierto que ya trae resueltos los
patrones de integración (Enterprise Integration Patterns) y más de 350 componentes (HTTP, archivos, SFTP, bases de datos,
Kafka…), según su propio sitio. Se aprende con su CLI, `camel run`. Si llevas a producción una integración como la de esta práctica, el mismo YAML
corre sobre Camel Quarkus.

## Por qué

- **Es ligero.** Una integración es un proceso pequeño que hace una cosa; no hace falta un servidor de aplicaciones (el programa grande que hospeda a muchas aplicaciones a la vez).
- **Escala en Kubernetes.** Cada integración es un servicio aparte, y lo que escala es ese servicio.
- **Puede arrancar rápido.** Camel Quarkus compila a modo nativo, que promete arrancar mucho más rápido y con menos
  memoria que la JVM. La JVM es la máquina que corre Java; en modo nativo el programa se compila directo para el
  sistema operativo, sin JVM. Eso importa cuando un clúster sube y baja réplicas (copias del mismo servicio) solo. No te lo pedimos de fe: en la lección 33
  mides los dos modos con el mismo servicio.
- **Los patrones tienen nombre.** Router, Multicast, Content-Based Router… son los del libro de Hohpe y Woolf. Lo que
  aprendes aquí lo reconoces en cualquier otra herramienta.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Programar cada integración a mano en un framework web | Cada reintento, cada conexión SFTP y cada transacción se programa de cero; el patrón queda escondido en el código |
| Una plataforma de integración comercial | Es otra forma válida, pero ata el aprendizaje a un proveedor y a su licencia |

## Qué nos haría reabrirla

- Que el arranque y la memoria en nativo dejen de ser mejores que en la JVM para este tipo de servicio (lo mide la 33).
- Que un conector que la práctica necesite deje de tener soporte en modo nativo.

## Fuentes

- Apache Camel: https://camel.apache.org/
- CLI de Camel (camel-jbang): https://camel.apache.org/manual/camel-jbang.html
- Enterprise Integration Patterns (Hohpe y Woolf): https://www.enterpriseintegrationpatterns.com/
