# Una sola imagen; cada servicio es una carpeta

> **Dónde muerde:** lección 07.

## Qué decidimos

Todos los servicios (el router e int-rest, int-bd, int-edi) usan **la misma imagen de Camel**. Lo que los hace
distintos es la carpeta que cada uno monta: sus rutas, sus mapeos y sus plantillas.

## Por qué

- **Agregar un destino es agregar una carpeta**, no construir una imagen nueva.
- **Lo que escala es el servicio, no la imagen:** cada uno tiene su propio número de réplicas.
- **La carpeta se trata como código:** vive en git, tiene versión y casos dorados, y no se edita en caliente.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Una imagen por servicio con todo adentro | Es lo usual en microservicios; aquí obligaría a construir una imagen por cada destino nuevo |
| Un operador de Kubernetes para integraciones | Hay que instalar y operar el operador, otra pieza más que aprender |

## Qué nos haría reabrirla

- Que un destino pida un componente tan pesado o tan raro que no convenga cargarlo en la imagen de todos.

## Fuentes

- The Twelve-Factor App, configuración: https://12factor.net/config
