# Contenedores, imágenes y Docker Compose

**Para qué te sirve aquí:** todo lo de la práctica (la API, el ERP, el SFTP del socio y tus rutas de Camel) corre en
contenedores. Con un solo comando levantas los cuatro sistemas sin instalar ninguno.

## Lo que ves en tu terminal

```bash
docker compose -f infra/compose.yaml up -d --wait   # levanta el ambiente de la lección 00
docker compose -f infra/compose.yaml ps             # lista lo que quedó corriendo
```

La segunda línea te muestra cuatro renglones: `api-pedidos`, `erp`, `socio-sftp` y `estado`, cada uno con su estado
(`healthy` cuando ya contesta). Ninguno de esos programas está instalado en tu máquina, y aun así están corriendo.

## Una imagen de la vida diaria

Una **imagen** es como una lonchera armada: la comida, los cubiertos y la servilleta, todo lo que hace falta para comer
en cualquier lugar. Un **contenedor** es esa lonchera abierta sobre una mesa: la misma comida, ya en uso.

## El concepto

Un programa casi nunca corre solo: necesita cierta versión de un lenguaje, bibliotecas, archivos de configuración. Una
**imagen** empaqueta el programa con todo eso. Se construye una vez y corre igual en tu laptop, en la de tu compañero y
en un servidor.

Un **contenedor** es una imagen corriendo. De la misma imagen puedes levantar varios contenedores, y cada uno tiene su
propio espacio: sus archivos, su red, sus procesos. Si lo borras y lo vuelves a crear, empieza limpio desde la imagen.

**Docker Compose** describe en un archivo YAML (`compose.yaml`) qué contenedores quieres, de qué imagen sale cada uno,
qué puertos abre, qué carpetas de tu máquina ve (lo que se llama **montar** una carpeta) y en qué red se encuentran.
Dentro de esa red, cada servicio encuentra a los demás **por su nombre**: el router llama a `http://int-rest:8080`, no a
una dirección IP.

## Lo que necesitas saber para la práctica

- `up -d` levanta en segundo plano; `down` apaga y borra los contenedores (los datos de los volúmenes se quedan).
- `logs <servicio>` muestra lo que ese contenedor escribió. Es la primera parada cuando algo no funciona.
- Desde la lección 08 cada servicio de Camel es un contenedor. Todos usan **la misma imagen**; lo que cambia es la
  carpeta que cada uno monta (ficha [09](../../decisiones/09-una-imagen-un-servicio-por-carpeta.md)).
- `localhost` dentro de un contenedor es ese mismo contenedor, no tu máquina.

## Compruébalo

```bash
docker compose version
```

Debe salir una línea como `Docker Compose version …` con un número. Si dice «command not found» o «unknown command»,
instala Docker con Compose antes de la lección 00.

## Si quieres ir más lejos

- Docker, qué es una imagen: https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-an-image/
- Docker, qué es un contenedor: https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-a-container/
- Docker Compose, redes: https://docs.docker.com/compose/how-tos/networking/
