# Rutas en YAML, en su forma canónica

> **Dónde aparece:** lección 01 en adelante.

## El problema

Si cada ruta se escribe a su manera, leer la de otro cuesta, y Camel acepta formas que su propio validador marca.

## Qué decidimos

Todas las rutas se escriben en el DSL de YAML de Camel (DSL: un lenguaje hecho para una sola tarea, aquí describir
rutas) y en su **forma canónica**: cada expresión dice su lenguaje
y su texto por separado (`expression:` → `simple:` → `expression:`), aunque exista una forma corta.

## Por qué

- **YAML se lee sin saber Java.** La ruta se parece a un diagrama: de dónde viene, qué pasos sigue, a dónde va.
- **La forma canónica es la que no cambia.** La corta es un atajo que Camel acepta y que su propio validador marca
  con una advertencia. El alumno aprende una sola forma, la que Camel no marca.
- **Los comentarios se quedan.** Cada ruta explica en comentarios por qué hace lo que hace.

## Qué descartamos

| Alternativa | Por qué no |
|---|---|
| Rutas en Java | Obliga a saber Java antes de entender una integración |
| Forma corta de YAML | Camel la acepta, pero genera advertencias y hay dos maneras de escribir lo mismo |
| Convertir con el normalizador oficial de Camel | Existe, pero borra los comentarios, que aquí son parte de la lección |

## Qué nos haría reabrirla

- Que Camel retire la forma canónica o cambie cuál considera la oficial.

## Fuentes

- DSL de YAML de Camel: https://camel.apache.org/components/next/others/yaml-dsl.html
