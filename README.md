# Ni una orden perdida

[![verifica](https://github.com/HabilMX/ni-una-orden-perdida/actions/workflows/verifica.yml/badge.svg)](https://github.com/HabilMX/ni-una-orden-perdida/actions/workflows/verifica.yml)

Una práctica abierta y gratuita de integración de sistemas con [Apache Camel](https://camel.apache.org/). Una tienda
inventada vende, y cada orden tiene que llegar a tres sistemas que no se parecen entre sí: una API REST, un ERP en
MariaDB y un socio comercial que recibe documentos EDI X12 por SFTP. Todo corre en tu máquina con Docker.

Lo que se aprende con las manos: que no se pierda ninguna orden, que todo deje rastro, que un reintento no duplique y
que un destino caído no tumbe a los demás.

## Antes de empezar

- Terminal y git, leer JSON y SQL básico.
- Docker con Compose. De Docker basta con `docker compose up` y `down`; lo demás viene escrito.
- No hace falta saber Java ni Camel: las rutas son YAML y se aprenden aquí.

Funciona en Linux, macOS y Windows con WSL.

## Cómo se trabaja

Cada lección vive en `lecciones/NN-…/` con tres cosas: `inicio/` (de donde partes), `solucion/` (la nuestra) y un
`verifica` que mide lo que llegó a cada sistema. Copias `inicio/` a `mio/`, trabajas ahí y corres `./verifica`: sale
en rojo diciendo qué falta, y en verde cuando lo lograste.

No es un recetario: cada lección abre pidiéndote que predigas qué va a pasar, te hace romper algo a propósito, y
termina ligando la ficha que explica por qué se hizo así.

## Temario

Esta práctica está **en construcción**: las lecciones sin liga todavía no salen.

### Entrega 1 — Que llegue

| # | Lección |
|---|---|
| 00 | [Ambiente listo](es/00-ambiente.md) |
| 01 | [Mi primera ruta corre](es/01-mi-primera-ruta.md) |
| 02 | [La orden llega a la API](es/02-la-orden-llega-a-la-api.md) |
| 03 | [Traducir la orden](es/03-traducir-la-orden.md) |
| 04 | [Al ERP](es/04-al-erp.md) |
| 05 | [Todo o nada](es/05-todo-o-nada.md) |
| 06 | [Armar el EDI](es/06-armar-el-edi.md) |
| 07 | [Al socio](es/07-al-socio.md) |
| 08 | [El router](es/08-el-router.md) |
| 09 | [El hilo entre servicios](es/09-el-hilo-entre-servicios.md) |
| 10 | [Todos los canales](es/10-todos-los-canales.md) |
| 11 | [No todo va a todos](es/11-no-todo-va-a-todos.md) |
| 12 | [Crecer en réplicas](es/12-crecer-en-replicas.md) |

### Entrega 2 — Que avance

| # | Lección |
|---|---|
| 13 | Llega el embarque *(en construcción)* |
| 14 | Un archivo, varias órdenes *(en construcción)* |
| 15 | El estado vive fuera del proceso *(en construcción)* |
| 16 | Llega la entrega *(en construcción)* |
| 17 | La entrega llegó antes que la guía *(en construcción)* |

### Entrega 3 — Que no falle

| # | Lección |
|---|---|
| 18 | ¿Cuándo dos mensajes son el mismo? *(en construcción)* |
| 19 | El destino no contesta *(en construcción)* |
| 20 | Aceptado, rechazado o no se sabe *(en construcción)* |
| 21 | Enviado no es confirmado *(en construcción)* |
| 22 | Tirar un canal *(en construcción)* |
| 23 | Reprocesar lo que falló *(en construcción)* |
| 24 | No tumbar al destino *(en construcción)* |
| 25 | Esperar a los canales *(en construcción)* |
| 26 | Sobrevivir al reinicio *(en construcción)* |
| 27 | Seguir una orden *(en construcción)* |
| 28 | Lo que el rastro no debe mostrar *(en construcción)* |

### Opcional — Que escale

| # | Lección |
|---|---|
| 29 | Un tópico, un grupo por integración *(en construcción)* |
| 30 | No perder al consumir *(en construcción)* |
| 31 | No perder al publicar *(en construcción)* |
| 32 | De laboratorio a proyecto *(en construcción)* |
| 33 | JVM contra nativo, medido *(en construcción)* |

## Más

- [Decisiones de diseño](decisiones/README.md): qué decidimos, por qué y qué nos haría cambiarlo.
- [Cambios](CHANGELOG.md) de cada entrega.
- ¿Algo no hace lo que dice? [Abre un issue](../../issues/new/choose).
- Licencia: ver [LICENSE.md](LICENSE.md).

---

> **Qué hace Hábil**
>
> Al cerrar esta entrega, una orden creada llega a la API, al ERP y al socio, cada uno con su formato. En una empresa,
> ese mismo tramo cruza sistemas que se hablan poco, y lo que se pierde está entre ellos: «un pedido que la tienda
> cobró y que el almacén no vio».
>
> En Hábil construimos a la medida la integración entre su tienda, su ERP, sus socios y sus canales. Para ese
> problema está **[Hábil MDW](https://www.habil.mx/es/habil-mdw/?utm_source=github&utm_medium=referral&utm_campaign=2026-11_ni-una-orden-perdida_es)**. Hábil MDW es la capa que se pone entre los
> sistemas que ya tiene. Ahí pasa cada pedido, cada cobro, cada póliza y cada aviso, con la respuesta de quien lo
> recibió y el motivo si algo falló. Cada sistema entra con su forma, y la plataforma lo traduce a un modelo común: un
> pedido es un pedido, venga de donde venga.
>
> Conectamos los sistemas de un primer flujo completo —por ejemplo, del pedido al cobro y a la factura—, definimos con
> usted los pasos y las reglas, y lo dejamos funcionando con el rastro a la vista de su equipo.
>
> **[Pida su primera conversación, sin costo.](https://wa.me/525667541323?text=Hola%2C%20vengo%20de%20GitHub%20%28Ni%20una%20orden%20perdida%20%C2%B7%20entrega%201%29.%20Me%20interesa%20una%20primera%20conversaci%C3%B3n.%0AIndustria%3A%0APuesto%3A%0ASistemas%20principales%3A)**
