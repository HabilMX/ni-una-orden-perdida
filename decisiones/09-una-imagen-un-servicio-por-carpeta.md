# Una sola imagen; cada servicio es una carpeta

> **Dónde aparece:** lección 08.

## El problema

Si cada servicio necesita su propia imagen, agregar un destino obliga a construir y publicar una imagen nueva.

## Qué decidimos

El router recibe la orden de la tienda; cada `int-…` la lleva a un destino: `int-rest` a la API, `int-bd` al ERP e
`int-edi` al socio. Todos usan **la misma imagen de Camel** (una imagen es el paquete con todo lo que un contenedor
necesita para correr). Lo que los hace distintos es la carpeta que cada uno monta, es decir, que el contenedor ve
como suya: sus rutas, sus mapeos y sus plantillas.

## Por qué

- **Agregar un destino es agregar una carpeta**, no construir una imagen nueva.
- **Lo que escala es el servicio, no la imagen:** cada uno tiene su propio número de réplicas.
- **La carpeta se trata como código:** vive en git, tiene versión y casos dorados, y no se edita en caliente (con el servicio corriendo).

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Una imagen por servicio con todo adentro | Es lo usual en microservicios; aquí obligaría a construir una imagen por cada destino nuevo |
| Un operador de Kubernetes para integraciones (un programa que instala y administra otros dentro del clúster) | Hay que instalar y operar el operador, otra pieza más que aprender |

## Qué nos haría reabrirla

- Que un destino pida un componente tan pesado o tan raro que no convenga cargarlo en la imagen de todos.

## Fuentes

- Docker, carpetas montadas (bind mounts): https://docs.docker.com/engine/storage/bind-mounts/
- Docker, qué es una imagen: https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-an-image/
