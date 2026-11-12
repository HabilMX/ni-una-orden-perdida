# Terminal y git, lo mínimo

**Para qué te sirve aquí:** la práctica se trabaja desde la terminal. Con git bajas el repositorio y, si quieres, guardas
tus avances en `mio/` sin miedo a perderlos.

## Lo que ves en tu terminal

```bash
git clone <liga-del-repositorio>      # baja la práctica a una carpeta nueva
cd ni-una-orden-perdida               # entra a esa carpeta
ls lecciones                          # lista las lecciones
```

`ls` te muestra carpetas como `00-ambiente`, `01-mi-primera-ruta`… Cada comando de las lecciones se escribe desde la
raíz del repositorio o desde la carpeta de la lección; el README de cada una dice desde dónde.

## Una imagen de la vida diaria

La terminal es hablarle a la computadora por escrito en vez de señalarle con el dedo. Git es un cuaderno donde cada
página es una foto de tus archivos: puedes regresar a cualquier página.

## El concepto

La **terminal** (o línea de comandos) recibe órdenes escritas. Cada orden es un programa con argumentos:
`cp -R inicio mio` es «copia la carpeta `inicio` en `mio`, con todo lo de adentro». Siempre estás parado en una carpeta,
el **directorio actual**; `pwd` te dice cuál es y `cd` te mueve.

**Git** guarda la historia de un proyecto. Un **commit** es una foto de los archivos con un mensaje que dice qué
cambió. Un **repositorio** es la carpeta con toda esa historia. `git clone` copia un repositorio completo a tu máquina;
`git status` te dice qué cambiaste desde la última foto.

## Lo que necesitas saber para la práctica

- Copiar el punto de partida de una lección: `cp -R inicio mio` desde la carpeta de la lección.
- `mio/` está en `.gitignore`: git no lo sube aunque hagas commit. Es tu espacio.
- Si algo se rompió y quieres empezar la lección de nuevo, borra tu `mio/` y vuelve a copiar `inicio/`.
- Las rutas con `./` (como `./verifica`) significan «el archivo que está en esta carpeta».
- En Windows se trabaja dentro de WSL, que te da una terminal de Linux.

## Compruébalo

```bash
git --version
```

Debe salir `git version` y un número. Si no, instala git antes de clonar la práctica.

## Si quieres ir más lejos

- Git, el libro oficial (en español): https://git-scm.com/book/es/v2
- Microsoft, instalar WSL: https://learn.microsoft.com/es-es/windows/wsl/install
