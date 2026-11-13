# Java, la JVM y Quarkus

**Para qué te sirve aquí:** Camel está hecho en Java, y tus rutas corren dentro de una JVM aunque tú solo escribas
YAML. Cuando una integración pasa a un proyecto real, puede correr sobre Quarkus, como en la lección 31.

## Lo que ves en tu terminal

```bash
docker run --rm --entrypoint java apache/camel-jbang:4.22.1 -version
```

Sale algo como `openjdk version "21.0.12" … LTS`. Es la Java que viene dentro de la imagen de Camel de la práctica; en
tu máquina no hace falta instalarla.

## Una imagen de la vida diaria

La **JVM** es un traductor simultáneo. El programa en Java habla un idioma intermedio (el *bytecode*), y la JVM lo
traduce en el momento al idioma de cada computadora: Linux, macOS o Windows. Por eso el mismo programa corre en todas.

## El concepto

**Java** es un lenguaje de programación. Su código no se convierte directo en instrucciones de tu procesador: se
compila a **bytecode**, y la **JVM** (Java Virtual Machine, la máquina virtual de Java) lo ejecuta. Mientras corre, la
JVM observa qué partes del programa se usan más y las optimiza; por eso un programa en Java suele ir más rápido después
de un rato que al arrancar.

Ese arranque tiene un costo: al iniciar, la JVM carga clases, lee configuración y prepara todo, y eso toma tiempo y
memoria. Para un servidor que corre semanas da igual. Para un servicio pequeño que se levanta y se apaga según la carga,
pesa.

**Quarkus** es un framework de Java pensado para contenedores. Hace al **compilar** mucho del trabajo que otros frameworks
hacen al arrancar, para que el servicio arranque más rápido y ocupe menos memoria. **Camel Quarkus** es Camel corriendo
sobre Quarkus: las mismas rutas, empaquetadas como un servicio pequeño.

Quarkus además puede compilar el servicio a **modo nativo**, sin JVM. Eso tiene su propia ficha:
[nativo](07-nativo.md).

## Lo que necesitas saber para la práctica

- En las lecciones corres tus rutas con `camel run`, que usa la JVM de la imagen. Es la forma rápida de aprender.
- En la lección 31 llevas la misma carpeta a un proyecto de Camel Quarkus con `camel export`.
- No escribes Java en ninguna lección. Si un error dice `Exception` o trae muchas líneas `at org.apache…`, es Java
  contándote dónde falló: la primera línea con `Caused by` suele decir la causa.

## Compruébalo

```bash
docker run --rm --entrypoint java apache/camel-jbang:4.22.1 -version
```

Debe salir `openjdk version` con un número. Si Docker dice que no encuentra la imagen, la baja sola la primera vez.

## Si quieres ir más lejos

- Oracle, la especificación de la JVM: https://docs.oracle.com/javase/specs/jvms/se21/html/index.html
- Quarkus, qué es: https://quarkus.io/about/
- Camel Quarkus, primeros pasos: https://camel.apache.org/camel-quarkus/latest/first-steps.html
