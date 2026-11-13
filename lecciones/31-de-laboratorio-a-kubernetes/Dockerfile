# syntax=docker/dockerfile:1
#
# De la carpeta del laboratorio (int-rest/) a una imagen de contenedor, en cuatro etapas. Todo pasa DENTRO de
# contenedores: en tu máquina no instalas Java, Maven ni GraalVM.
#
#   1. exportar        camel export convierte la carpeta en un proyecto Maven de Camel Quarkus
#   2. compilar        el proyecto, listo para compilarse (con Java 21 y Mandrel, el compilador nativo)
#   3a. jvm            el proyecto compilado para la JVM, en una imagen que solo trae Java para correr
#   3b. nativo         el proyecto compilado a un ejecutable nativo, en una imagen mínima (lección 32)
#
# Se construye desde la carpeta del laboratorio, que es el «contexto» de la construcción:
#   docker build -f Dockerfile --target jvm    -t ni-una-orden-perdida/int-rest:jvm    int-rest
#   docker build -f Dockerfile --target nativo -t ni-una-orden-perdida/int-rest:nativo int-rest
#
# La configuración NO va en application.properties (regla de la práctica): lo que se decide al compilar va en el
# pom.xml (con --build-property), y lo que se decide al correr va en variables de entorno del Deployment.

# ---------------------------------------------------------------------------------------------------------------
# 1. exportar: la misma imagen de Camel de todas las lecciones
FROM apache/camel-jbang:4.22.1 AS exportar
COPY --chown=1000:1000 . /home/ubuntu/int-rest/
WORKDIR /home/ubuntu/int-rest
# - Se exporta SOLO la ruta. Si no se le dice qué archivos, camel export toma todos los de la carpeta y los deja
#   sueltos en un solo directorio: los casos dorados (entrada.json, esperado.json) chocarían entre sí.
# - quarkus.native.resources.includes le dice al compilador nativo que guarde los mapeos dentro del ejecutable
#   (en la JVM se leen del classpath sin pedirlo). Va al pom.xml como propiedad de construcción.
RUN camel export int-rest.camel.yaml \
      --runtime=quarkus \
      --gav=mx.habil.practica:int-rest:1.0.0 \
      --build-property='quarkus.native.resources.includes=mappings/**' \
      --directory=/home/ubuntu/proyecto
WORKDIR /home/ubuntu/proyecto
# Dos ajustes al proyecto que salió (ficha 36):
# - camel export deja un application.properties vacío. Se borra: la configuración va por variables de entorno.
# - Los mapeos se copian con su carpeta (mappings/api/pedido/v2/…), porque la ruta los busca con esa ruta.
RUN rm -f src/main/resources/application.properties \
 && cp -R /home/ubuntu/int-rest/mappings src/main/resources/mappings

# ---------------------------------------------------------------------------------------------------------------
# 2. compilar: la imagen de Quarkus que trae Java 25 y Mandrel 25, la versión que Quarkus 3.40 pide para el nativo
#    (con Mandrel 23.1 se niega a compilar). El proyecto se compila para Java 21 (maven.compiler.release del
#    pom.xml), así que corre en la JVM 21 de abajo. Se usa para las DOS compilaciones (JVM y nativo): así la
#    comparación de la lección 32 es con el mismo compilador de Java y en la misma máquina.
FROM quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:25.0.5.0-Final-java25 AS compilar
COPY --from=exportar --chown=quarkus:quarkus /home/ubuntu/proyecto/ /code/
USER quarkus
WORKDIR /code
# El caché de Maven (~/.m2) vive fuera de la imagen y se reusa entre construcciones: la segunda vez no se baja
# nada. Así el tiempo de construcción que mides en la 32 es el de compilar, no el de tu internet.
# Se agrega la salud de Camel (/q/health/ready): Kubernetes la usa para saber cuándo una copia ya puede recibir
# órdenes, y la lección 32 para medir el arranque.
RUN --mount=type=cache,id=ni-una-orden-perdida-m2,target=/home/quarkus/.m2,uid=1001 \
    ./mvnw -B -q quarkus:add-extension -Dextensions=camel-quarkus-microprofile-health

# ---------------------------------------------------------------------------------------------------------------
# 3a. jvm
FROM compilar AS compilar-jvm
RUN --mount=type=cache,id=ni-una-orden-perdida-m2,target=/home/quarkus/.m2,uid=1001 \
    ./mvnw -B package -DskipTests

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.24 AS jvm
ENV LANGUAGE='en_US:en'
COPY --from=compilar-jvm --chown=185 /code/target/quarkus-app/lib/ /deployments/lib/
COPY --from=compilar-jvm --chown=185 /code/target/quarkus-app/*.jar /deployments/
COPY --from=compilar-jvm --chown=185 /code/target/quarkus-app/app/ /deployments/app/
COPY --from=compilar-jvm --chown=185 /code/target/quarkus-app/quarkus/ /deployments/quarkus/
EXPOSE 8080
USER 185
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"
ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]

# ---------------------------------------------------------------------------------------------------------------
# 3b. nativo (lección 32). Es la parte cara: varios minutos y varios GiB de memoria (lecciones/31-…/CAPACIDAD.md).
#     native-image-xmx pone un techo a la memoria del compilador, para que no se coma la de tu máquina.
FROM compilar AS compilar-nativo
ARG MEMORIA_NATIVO=5g
RUN --mount=type=cache,id=ni-una-orden-perdida-m2,target=/home/quarkus/.m2,uid=1001 \
    ./mvnw -B package -DskipTests -Dnative -Dquarkus.native.native-image-xmx=${MEMORIA_NATIVO}

FROM quay.io/quarkus/ubi9-quarkus-micro-image:2.0 AS nativo
WORKDIR /work/
COPY --from=compilar-nativo --chown=1001:root --chmod=0775 /code/target/*-runner /work/application
EXPOSE 8080
USER 1001
CMD ["./application", "-Dquarkus.http.host=0.0.0.0"]
