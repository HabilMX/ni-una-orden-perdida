# El nativo se compila con Mandrel dentro de un contenedor, con el mismo compilador de Java que la JVM

> **Dónde aparece:** lección 32 (apéndice opcional). El Dockerfile vive en la 31.

## El problema

Compilar a nativo pide GraalVM (o Mandrel, su distribución para Quarkus) en la versión exacta que pide Quarkus, más
un compilador de C y bibliotecas del sistema. Instalar eso en la laptop de cada alumno, en macOS, Linux o Windows, es
una lección aparte, y cada máquina terminaría con una versión distinta. Además, la compilación es la parte cara: tarda
minutos y usa varios GiB de memoria, y la máquina la comparte con otras cosas.

## Qué decidimos

- **La compilación nativa corre dentro de la imagen de Quarkus que trae Mandrel**:
  `quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:25.0.5.0-Final-java25`, fijada en su versión. Es una etapa del
  `Dockerfile` de la 31 (`compilar-nativo`); el resultado va a la imagen mínima `ubi9-quarkus-micro-image:2.0`.
- **Mandrel 25, no 23.1:** Quarkus 3.40 (el que trae Camel 4.22.1) se niega a compilar con Mandrel 23.1 («Out of
  date version of GraalVM or Mandrel detected»). Lo medimos así.
- **La misma imagen compila la versión JVM** (etapa `compilar-jvm`). El proyecto se compila para Java 21
  (`maven.compiler.release`), así que la imagen JVM corre sobre `ubi9/openjdk-21-runtime:1.24`. Las dos
  compilaciones salen del mismo proyecto, el mismo Maven y el mismo compilador de Java: lo único que cambia es `-Dnative`.
- **Un techo para la memoria del compilador:** `-Dquarkus.native.native-image-xmx=5g` (argumento `MEMORIA_NATIVO`
  del Dockerfile). Sin techo, el compilador toma una parte de toda la memoria que ve.
- **El caché de Maven vive fuera de la imagen** (`RUN --mount=type=cache`): la segunda construcción no baja nada.
  Así el tiempo de construir que mide la 32 es el de compilar, no el de la red.
- **En la máquina del alumno se compila para su procesador** (arm64 en una Mac con Apple Silicon, amd64 en una PC).
  El workflow `apendice.yml` compila en x86 y, si se le pide, publica esa imagen en ghcr.io con el `GITHUB_TOKEN`
  de la corrida.

## Por qué

- **Nada que instalar:** el alumno ya tiene Docker desde la lección 00. La versión de Mandrel es la misma para todos.
- **Es la forma que documenta Quarkus** para construir el nativo en varias etapas, dentro de un contenedor.
- **Una comparación justa** pide que JVM y nativo salgan del mismo código y la misma cadena de construcción. Si cada
  una usara otra imagen u otra versión de Java, no sabríamos qué diferencia viene de qué.
- **El techo de memoria protege la máquina:** si el compilador se queda sin memoria, falla con un mensaje claro en
  vez de dejar sin memoria a lo demás. En la máquina donde lo medimos, con 5g de techo,
  reportó un pico de 4.58 GB y de 5.00 GB en dos construcciones (lecciones/31-…/CAPACIDAD.md).

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Instalar GraalVM o Mandrel en la máquina | Cambia por sistema operativo y versión; es otra lección y cada alumno tendría otra versión |
| `quarkus.native.container-build=true` (Maven en la máquina lanza el contenedor) | Pide Java y Maven en la máquina del alumno |
| GraalVM Community en vez de Mandrel | También funciona; Mandrel es la distribución que Quarkus prueba y recomienda para sus extensiones |
| Imagen de JVM con otra base (Temurin) | Funciona, pero el proyecto que genera `camel export` propone la de UBI; la dejamos igual a la que propone |
| Compilar el nativo solo en GitHub y bajarlo | El alumno no vería lo que cuesta compilarlo, que es la mitad de la lección; y en arm64 no le serviría la de x86 |

## Qué nos haría reabrirla

- Que Quarkus pida otra versión de Mandrel: se cambia la etiqueta de la imagen, en una sola línea.
- Que la máquina típica del alumno no tenga la memoria que pide la compilación: se bajaría el techo (más lento) o se
  usaría la imagen publicada por el workflow, avisando que no es de su procesador.

## Fuentes

- Quarkus, construir un ejecutable nativo (varias etapas en contenedor, imágenes de Mandrel y micro): https://quarkus.io/guides/building-native-image
- Quarkus, referencia del nativo (memoria del compilador): https://quarkus.io/guides/native-reference
- Mandrel: https://github.com/graalvm/mandrel
- Docker, caché de montaje en la construcción (`--mount=type=cache`): https://docs.docker.com/build/cache/optimize/
